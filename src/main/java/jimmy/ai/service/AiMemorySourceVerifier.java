package jimmy.ai.service;

import jimmy.ai.model.AiMemoryCandidate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 记忆来源验证器。
 * <p>
 * 一条 LLM 提炼出的记忆只有在用户原话中能找到支撑才可信。
 * 例如：模型提炼出"用户希望简短回答"，但用户原话只说了"帮我查一下订单"，
 * 这就是典型的不匹配 —— 模型把自己的推测写成了偏好。
 * <p>
 * 验证策略：
 * <ul>
 *   <li>提取记忆标题和摘要中的关键词</li>
 *   <li>在用户原始消息中搜索这些关键词或其语义变体</li>
 *   <li>返回是否匹配 + 匹配质量评分</li>
 * </ul>
 */
@Component
public class AiMemorySourceVerifier {

    /**
     * 偏好类记忆在用户原话中应该出现的特征词映射。
     * key = 记忆可能声称的偏好方向，value = 用户原话中应出现的对应表达。
     */
    private static final List<PreferencePattern> PREFERENCE_PATTERNS = List.of(
            new PreferencePattern("简短", List.of("简短", "简单", "一句话", "别啰嗦", "不要太长", "简洁")),
            new PreferencePattern("详细", List.of("详细", "完整", "展开", "具体", "仔细", "全面")),
            new PreferencePattern("表格", List.of("表格", "表", "列表", "列出来")),
            new PreferencePattern("先给结论", List.of("先给结论", "先说结论", "结论先行", "总结", "先总结")),
            new PreferencePattern("结论", List.of("先给结论", "先说结论", "先给出结论", "结论先行", "先总结")),
            new PreferencePattern("markdown", List.of("markdown", "md", "格式", "排版")),
            new PreferencePattern("异常", List.of("异常", "问题", "故障", "报错", "错误", "出问题")),
            new PreferencePattern("运输任务", List.of("运输任务", "任务", "调度", "运单")),
            new PreferencePattern("费用", List.of("费用", "金额", "结算", "账单", "付款")),
            new PreferencePattern("客户", List.of("客户", "用户", "顾客", "甲方")),
            new PreferencePattern("司机", List.of("司机", "驾驶员", "师傅")),
            new PreferencePattern("车辆", List.of("车辆", "车", "货车", "卡车")),
            new PreferencePattern("仓库", List.of("仓库", "库存", "仓储", "入库", "出库")),
            new PreferencePattern("只查", List.of("只查", "只关注", "只需", "仅", "只", "不要查", "别查", "不要")),
            new PreferencePattern("习惯", List.of("习惯", "一直", "每次", "经常", "常用", "一般", "通常"))
    );

    /**
     * 验证 LLM 提炼出的记忆是否能在用户原话中找到支撑。
     *
     * @param candidate    LLM 提炼的候选记忆
     * @param userMessage  用户原始消息（脱敏后）
     * @return 验证结果
     */
    public VerificationResult verify(AiMemoryCandidate candidate, String userMessage) {
        if (candidate == null || !StringUtils.hasText(userMessage)) {
            return VerificationResult.NO_MATCH;
        }

        String title = normalize(candidate.memoryTitle());
        String summary = normalize(candidate.memorySummary());
        String user = normalize(userMessage);

        // 策略1：检查记忆标题/摘要中的关键词是否在用户原话中出现
        int titleMatches = countKeywordMatches(title, user);
        int summaryMatches = countKeywordMatches(summary, user);

        // 策略2：使用偏好模式匹配 —— 记忆声称的方向是否与用户表达一致
        int patternMatches = countPatternMatches(title + " " + summary, user);

        int totalMatches = titleMatches + summaryMatches + patternMatches;

        if (patternMatches >= 1 && containsAny(user, List.of("以后", "默认", "记住", "不要太长", "别啰嗦", "先给结论", "先说结论", "先给出结论"))) {
            return VerificationResult.STRONG_MATCH;
        }

        if (totalMatches >= 2) {
            return VerificationResult.STRONG_MATCH;
        }
        if (totalMatches >= 1) {
            return VerificationResult.WEAK_MATCH;
        }
        return VerificationResult.NO_MATCH;
    }

    /**
     * 简单关键词匹配：从 source 中提取有意义的中文词，在 target 中查找。
     */
    private int countKeywordMatches(String source, String target) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(target)) {
            return 0;
        }
        int matches = 0;
        // 提取 2-4 字的中文词组作为关键词
        String[] words = source.split("[，。；、！？\\s,;!?]+");
        for (String word : words) {
            String trimmed = word.trim();
            if (trimmed.length() >= 2 && target.contains(trimmed)) {
                matches++;
            }
        }
        return matches;
    }

    /**
     * 偏好模式匹配：如果记忆声称某方向，检查用户原话是否有对应表达。
     */
    private int countPatternMatches(String memoryText, String userText) {
        int matches = 0;
        for (PreferencePattern pattern : PREFERENCE_PATTERNS) {
            // 记忆声称了某个偏好方向
            if (memoryText.contains(pattern.preference)) {
                // 检查用户原话中是否有对应表达
                for (String signal : pattern.userSignals) {
                    if (userText.contains(signal)) {
                        matches++;
                        break; // 一个模式只计一次
                    }
                }
            }
        }
        return matches;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private boolean containsAny(String value, List<String> words) {
        for (String word : words) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 来源验证结果。
     */
    public enum VerificationResult {
        /** 强匹配：记忆关键词和偏好模式在用户原话中均有明确对应 */
        STRONG_MATCH,
        /** 弱匹配：有部分关键词对应但不够充分 */
        WEAK_MATCH,
        /** 不匹配：用户原话中找不到任何支撑 */
        NO_MATCH
    }

    /**
     * 偏好方向 → 用户原话信号词 的映射模式。
     */
    private record PreferencePattern(String preference, List<String> userSignals) {
    }
}
