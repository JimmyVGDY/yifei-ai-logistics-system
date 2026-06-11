package jimmy.ai.service;

import jimmy.ai.model.AgentResult;
import jimmy.ai.model.AgentStep;
import jimmy.ai.model.AiCitation;
import jimmy.ai.model.AiReadonlyQueryResult;
import jimmy.ai.model.AiToolCall;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * AI Agent 自主编排器 —— 将用户复杂问题分解为多步工具调用链。
 * <p>
 * 工作流程：模型判断是否需要工具 → 执行工具 → 结果注入上下文 → 循环直到完成。
 * 复用现有 {@link AiModelGateway} 和 {@link AiBusinessQueryTools}，
 * Agent 不可用时自动退回单轮 Tool Calling（由 {@link AiAssistantService} 处理）。
 * <p>
 * 安全护栏：最大迭代 5 轮、工具去重、强制只读。
 */
@Slf4j
@Service
public class AiAgentOrchestrator {

    private static final int MAX_ITERATIONS = 5;

    private static final String AGENT_SYSTEM_PROMPT = """
            你是物流管理系统的 AI 智能分析助手，可以调用只读工具查询数据。

            规则：
            1. 分析用户问题，判断是否需要工具调用
            2. 如果有具体业务关键词（客户名、订单号、地名等），使用全场景模糊搜索
            3. 如果涉及到特定模块（客户、订单、调度等），使用业务模块查询
            4. 如果需要统计/汇总/排名，使用只读 SQL 分析
            5. 收集到足够数据后，生成分析报告并用 [DONE] 标记结尾
            6. 不要虚构数据，只基于工具返回结果回答
            7. 最多执行 5 轮查询
            """;

    private final AiModelGateway modelGateway;
    private final AiBusinessQueryTools businessQueryTools;
    private final AiKnowledgeService knowledgeService;
    private final AiToolCallContext toolCallContext;
    private final AiReadonlyQueryService readonlyQueryService;

    public AiAgentOrchestrator(AiModelGateway modelGateway,
                                AiBusinessQueryTools businessQueryTools,
                                AiKnowledgeService knowledgeService,
                                AiToolCallContext toolCallContext,
                                AiReadonlyQueryService readonlyQueryService) {
        this.modelGateway = modelGateway;
        this.businessQueryTools = businessQueryTools;
        this.knowledgeService = knowledgeService;
        this.toolCallContext = toolCallContext;
        this.readonlyQueryService = readonlyQueryService;
    }

    /**
     * 执行 Agent 自主编排循环。
     *
     * @param userMessage    用户问题
     * @param conversationId AI 会话 ID
     * @param outputStream   SSE 输出流
     * @return Agent 执行结果
     */
    public AgentResult execute(String userMessage, String conversationId, OutputStream outputStream) {
        long start = System.currentTimeMillis();
        List<AgentStep> steps = new ArrayList<>();
        List<AiCitation> allCitations = new ArrayList<>(knowledgeService.search(userMessage));
        List<AiToolCall> allToolCalls = new ArrayList<>();
        Set<String> calledKeys = new HashSet<>();
        int totalCalls = 0;

        try {
            sendEvent(outputStream, "agent_start",
                    "{\"question\":\"" + escapeJson(userMessage) + "\",\"maxIterations\":" + MAX_ITERATIONS + "}");

            StringBuilder collectedData = new StringBuilder();
            for (AiCitation citation : allCitations) {
                collectedData.append("[").append(citation.title()).append("] ").append(citation.snippet()).append("\n");
            }

            for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
                sendEvent(outputStream, "thinking",
                        "{\"iteration\":" + iteration + ",\"message\":\"第" + iteration + "轮分析中...\"}");

                // 调用模型（使用现有 gateway，带工具）
                String userPrompt = "用户问题：" + userMessage
                        + "\n\n已收集数据：\n" + collectedData
                        + "\n\n请判断：是否需要更多工具调用？如果需要，说明要查什么。如果信息足够，给出分析报告并标记 [DONE]。";

                Optional<String> modelAnswer = modelGateway.chat(
                        AGENT_SYSTEM_PROMPT, userPrompt, "agent_step", conversationId);

                if (modelAnswer.isEmpty()) {
                    sendEvent(outputStream, "agent_done",
                            "{\"finalAnswer\":\"" + escapeJson("AI 模型暂时不可用，已收集的数据如下：\n" + collectedData)
                                    + "\",\"totalIterations\":" + iteration + ",\"totalToolCalls\":" + totalCalls + "}");
                    return new AgentResult("AI 模型暂时不可用", iteration, totalCalls,
                            allCitations, steps, 0, System.currentTimeMillis() - start);
                }

                String answer = modelAnswer.get();

                // 检查完成标记
                if (answer.contains("[DONE]")) {
                    String finalAnswer = answer.replace("[DONE]", "").trim();
                    sendEvent(outputStream, "agent_done",
                            "{\"finalAnswer\":\"" + escapeJson(finalAnswer)
                                    + "\",\"totalIterations\":" + iteration + ",\"totalToolCalls\":" + totalCalls + "}");
                    return new AgentResult(finalAnswer, iteration, totalCalls,
                            allCitations, steps, 8000, System.currentTimeMillis() - start);
                }

                // 根据模型输出执行工具
                List<AiToolCall> roundCalls = executeTools(answer, userMessage, calledKeys);
                totalCalls += roundCalls.size();

                if (roundCalls.isEmpty()) {
                    // 没有可执行的工具 → 直接返回当前结果
                    sendEvent(outputStream, "agent_done",
                            "{\"finalAnswer\":\"" + escapeJson(answer) + "\",\"totalIterations\":" + iteration + ",\"totalToolCalls\":" + totalCalls + "}");
                    return new AgentResult(answer, iteration, totalCalls,
                            allCitations, steps, 8000, System.currentTimeMillis() - start);
                }

                // 推送工具调用事件
                for (AiToolCall tc : roundCalls) {
                    sendEvent(outputStream, "tool_start",
                            "{\"iteration\":" + iteration + ",\"toolName\":\"" + escapeJson(tc.toolName()) + "\",\"target\":\"" + escapeJson(tc.target()) + "\"}");
                    sendEvent(outputStream, "tool_result",
                            "{\"toolName\":\"" + escapeJson(tc.toolName()) + "\",\"result\":\"" + escapeJson(truncate(tc.result(), 200)) + "\"}");
                    collectedData.append("[").append(tc.toolName()).append("] ").append(tc.result()).append("\n");
                    allToolCalls.add(tc);
                }
                steps.add(new AgentStep(iteration, answer, roundCalls,
                        "第" + iteration + "轮：" + roundCalls.size() + "个工具调用"));
            }

            String finalAnswer = "已完成 " + MAX_ITERATIONS + " 轮查询，调用 " + totalCalls + " 个工具。数据摘要：\n\n" + collectedData;
            sendEvent(outputStream, "agent_done",
                    "{\"finalAnswer\":\"" + escapeJson(finalAnswer) + "\",\"totalIterations\":" + MAX_ITERATIONS + ",\"totalToolCalls\":" + totalCalls + "}");
            return new AgentResult(finalAnswer, MAX_ITERATIONS, totalCalls,
                    allCitations, steps, 8000, System.currentTimeMillis() - start);
        } catch (RuntimeException exception) {
            log.warn("AI Agent 异常，conversationId={}, reason={}", conversationId, exception.getMessage());
            sendEvent(outputStream, "error", "{\"message\":\"AI 分析暂时不可用，请稍后重试\"}");
            return new AgentResult("AI 分析暂时不可用", 0, 0, List.of(), List.of(), 0, System.currentTimeMillis() - start);
        }
    }

    /**
     * 根据模型输出执行工具调用。
     * <p>
     * 启发式匹配：从模型输出中提取模块名和关键词 → 调用对应工具。
     */
    private List<AiToolCall> executeTools(String modelOutput, String userMessage, Set<String> calledKeys) {
        List<AiToolCall> results = new ArrayList<>();
        String lower = modelOutput.toLowerCase();

        // 1. 全场景模糊搜索（最通用）
        String searchKeyword = extractSearchKeyword(modelOutput);
        if (StringUtils.hasText(searchKeyword)) {
            String key = "globalSearch|" + searchKeyword;
            if (calledKeys.add(key)) {
                try {
                    toolCallContext.incrementAndCheck();
                    AiReadonlyQueryResult queryResult = readonlyQueryService.globalSearch(searchKeyword, null, null);
                    if (queryResult != null && StringUtils.hasText(queryResult.answerContext())) {
                        results.add(new AiToolCall("全场景模糊搜索", "业务模块", queryResult.answerContext()));
                    }
                } catch (Exception e) {
                    log.debug("Agent 全局搜索失败，keyword={}", searchKeyword);
                }
            }
            return results; // 一次执行一个工具，下一轮再判断
        }

        // 2. 如果模型提到了具体模块
        for (String module : new String[]{"客户管理", "订单管理", "运单中心", "调度管理", "运输任务", "物流轨迹", "司机管理", "车辆管理", "异常管理", "费用结算"}) {
            if (modelOutput.contains(module)) {
                String key = "moduleQuery|" + module;
                if (calledKeys.add(key)) {
                    try {
                        toolCallContext.incrementAndCheck();
                        AiReadonlyQueryResult result = readonlyQueryService.queryModule(module, searchKeyword, null, null);
                        if (result != null && StringUtils.hasText(result.answerContext())) {
                            results.add(new AiToolCall("业务模块查询", module, result.answerContext()));
                        }
                    } catch (Exception e) {
                        log.debug("Agent 模块查询失败，module={}", module);
                    }
                    return results;
                }
            }
        }

        return results;
    }

    /**
     * 从模型输出中提取搜索关键词。
     */
    private String extractSearchKeyword(String text) {
        if (!StringUtils.hasText(text)) return "";
        // 找引号中的内容（中文引号 “ 和 ”）
        int start = text.indexOf('“');
        if (start >= 0) {
            int end = text.indexOf('”', start + 1);
            if (end > start) return text.substring(start + 1, end).trim();
        }
        start = text.indexOf('「');
        if (start >= 0) {
            int end = text.indexOf('」', start + 1);
            if (end > start) return text.substring(start + 1, end).trim();
        }
        // 关键词提示：看"查询""搜索"后面的内容
        for (String marker : new String[]{"查询", "搜索", "查找", "query", "search"}) {
            int idx = text.indexOf(marker);
            if (idx >= 0) {
                String after = text.substring(idx + marker.length()).trim();
                // 取到第一个标点或换行
                int endIdx = after.length();
                for (int i = 0; i < after.length(); i++) {
                    if ("，,。；;\\n\"".indexOf(after.charAt(i)) >= 0) {
                        endIdx = i;
                        break;
                    }
                }
                String keyword = after.substring(0, Math.min(endIdx, 30)).trim();
                if (keyword.length() >= 2) return keyword;
            }
        }
        return "";
    }

    private void sendEvent(OutputStream outputStream, String event, String data) {
        try {
            String payload = "event: " + event + "\ndata: " + data + "\n\n";
            outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (Exception ignore) {
            // SSE 推送失败不影响 Agent 执行
        }
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", " ");
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return "";
        return value.length() <= maxLen ? value : value.substring(0, maxLen) + "...";
    }
}
