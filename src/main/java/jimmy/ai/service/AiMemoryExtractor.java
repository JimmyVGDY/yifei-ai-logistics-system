package jimmy.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jimmy.ai.model.AiMemoryCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * LLM 记忆提炼器 —— 用模型判断对话是否包含用户偏好或习惯。
 * <p>
 * 替代原来简单的关键词兜底逻辑：不再"用户查什么就存什么"，
 * 而是让 LLM 判断该对话是否暴露了值得记录的偏好或习惯。
 */
@Slf4j
@Component
public class AiMemoryExtractor {

    /**
     * LLM 记忆提炼系统提示词。
     * <p>
     * 关键要求：
     * <ul>
     *   <li>只输出严格 JSON，不要任何解释或代码块标记</li>
     *   <li>memoryType 必须是 ANSWER_STYLE / QUERY_HABIT / FAVORITE_MODULE 之一</li>
     *   <li>memorySummary 要脱敏，不能包含原始查询数据</li>
     * </ul>
     */
    private static final String SYSTEM_PROMPT = """
            你是用户行为分析助手。分析以下对话片段，判断用户是否表达了值得长期记忆的偏好或习惯。

            仅当以下情况返回 YES：
            - 用户明确说"以后""默认""习惯""希望""喜欢""记住""常用""一般先"等表达偏好的词
            - 用户指定了回答格式偏好（如"简短点""详细说明""用表格""先给结论"）
            - 用户表达了持续性的业务关注（如"我主要看异常""我经常需要查费用"）
            - 用户对某个业务模块表现出持续兴趣（"这个模块的XX功能怎么用"）

            以下情况返回 NO：
            - 普通的一次性业务查询（如"查一下订单123""最近三天的新单子""张三的运单状态"）
            - 简单的系统功能询问（如"这个怎么用""有哪些功能"）
            - 测试性质的问候（如"你好""你是谁""你能做什么"）
            - 日志排障类问题（带 traceId/operationId 的查询）
            - 纯数据统计查询（如"有多少条订单""统计本月费用"）

            输出严格JSON，不要任何解释、代码块标记或多余文本：
            {"hasMemory":true,"memoryType":"QUERY_HABIT","memoryTitle":"简洁标题","memorySummary":"脱敏后的偏好描述","confidence":0.85}
            memoryType 只能是 ANSWER_STYLE、QUERY_HABIT、FAVORITE_MODULE 之一。
            """;

    private final AiModelGateway modelGateway;
    private final ObjectMapper objectMapper;

    public AiMemoryExtractor(AiModelGateway modelGateway, ObjectMapper objectMapper) {
        this.modelGateway = modelGateway;
        this.objectMapper = objectMapper;
    }

    /**
     * 使用 LLM 判断对话是否包含用户偏好或习惯。
     * <p>
     * 三种返回结果：
     * <ul>
     *   <li>{@link ExtractionDecision#found(AiMemoryCandidate)} —— LLM 识别到值得记录的记忆</li>
     *   <li>{@link ExtractionDecision#skip()} —— LLM 判断该对话不值得记忆</li>
     *   <li>{@link ExtractionDecision#unavailable()} —— LLM 不可用或调用失败，由调用方降级为关键词匹配</li>
     * </ul>
     *
     * @param userMessage      用户原始问题（已脱敏）
     * @param assistantMessage AI 回答内容（已脱敏）
     * @param toolTargets      AI 调用的工具/模块名列表
     */
    public ExtractionDecision extract(String userMessage, String assistantMessage, List<String> toolTargets) {
        // LLM 未配置时直接降级，不浪费 HTTP 调用
        if (!modelGateway.configured()) {
            return ExtractionDecision.unavailable();
        }

        // 构造用户提示词：用户问题 + 工具调用摘要 + AI 回答摘要（截断防止过长）
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("用户问题：").append(truncate(userMessage, 200)).append("\n");
        if (toolTargets != null && !toolTargets.isEmpty()) {
            userPrompt.append("AI 调用的工具/模块：").append(String.join("、", toolTargets)).append("\n");
        }
        userPrompt.append("AI 回答摘要：").append(truncate(assistantMessage, 300));

        try {
            Optional<String> result = modelGateway.chat(SYSTEM_PROMPT, userPrompt.toString(), "memory_extract");
            if (result.isEmpty()) {
                return ExtractionDecision.unavailable(); // 模型调用异常，降级关键词匹配
            }

            JsonNode node = objectMapper.readTree(result.get());
            boolean hasMemory = node.path("hasMemory").asBoolean(false);
            if (!hasMemory) {
                return ExtractionDecision.skip(); // LLM 明确判断：不值得记忆
            }

            String memoryType = node.path("memoryType").asText("");
            String memoryTitle = node.path("memoryTitle").asText("");
            String memorySummary = node.path("memorySummary").asText("");
            double confidence = node.path("confidence").asDouble(0.80);

            // 校验 LLM 返回的字段完整性
            if (!StringUtils.hasText(memoryType)
                    || !StringUtils.hasText(memoryTitle)
                    || !StringUtils.hasText(memorySummary)) {
                return ExtractionDecision.unavailable();
            }

            // 归一化并裁剪置信度到合理范围
            String normalizedType = normalizeType(memoryType);
            double clampedConfidence = Math.max(0.72, Math.min(0.98, confidence));

            AiMemoryCandidate candidate = new AiMemoryCandidate(
                    normalizedType,
                    truncate(memoryTitle, 100),
                    truncate(memorySummary, 500),
                    clampedConfidence
            );
            return ExtractionDecision.found(candidate);
        } catch (JsonProcessingException e) {
            // LLM 偶尔不按 JSON 格式输出
            log.debug("LLM 记忆提取返回非 JSON，已降级关键词匹配，reason={}", e.getMessage());
            return ExtractionDecision.unavailable();
        } catch (RuntimeException e) {
            // LLM 调用超时、网络异常等
            log.debug("LLM 记忆提取调用失败，已降级关键词匹配，reason={}", e.getMessage());
            return ExtractionDecision.unavailable();
        }
    }

    /**
     * 截断字符串到指定长度，超长时追加 "..."。
     */
    private String truncate(String value, int maxLen) {
        if (value == null) return "";
        return value.length() <= maxLen ? value : value.substring(0, maxLen) + "...";
    }

    /**
     * 归一化 LLM 返回的 memoryType，防止模型输出不规范的值。
     */
    private String normalizeType(String type) {
        if (type == null) return "QUERY_HABIT";
        String upper = type.toUpperCase().trim();
        if (upper.contains("ANSWER") || upper.contains("STYLE")) return "ANSWER_STYLE";
        if (upper.contains("FAVORITE") || upper.contains("MODULE")) return "FAVORITE_MODULE";
        return "QUERY_HABIT";
    }

    /**
     * LLM 记忆提炼决策。
     * <p>
     * 三种状态：
     * <ul>
     *   <li>{@code found(candidate)} —— LLM 已处理，识别到记忆候选</li>
     *   <li>{@code skip()} —— LLM 已处理，判断无需记忆</li>
     *   <li>{@code unavailable()} —— LLM 不可用，调用方应降级关键词匹配</li>
     * </ul>
     */
    public record ExtractionDecision(boolean processed, AiMemoryCandidate candidate) {

        private static final ExtractionDecision UNAVAILABLE = new ExtractionDecision(false, null);
        private static final ExtractionDecision SKIP = new ExtractionDecision(true, null);

        public static ExtractionDecision found(AiMemoryCandidate candidate) {
            return new ExtractionDecision(true, candidate);
        }

        public static ExtractionDecision skip() {
            return SKIP;
        }

        public static ExtractionDecision unavailable() {
            return UNAVAILABLE;
        }
    }
}
