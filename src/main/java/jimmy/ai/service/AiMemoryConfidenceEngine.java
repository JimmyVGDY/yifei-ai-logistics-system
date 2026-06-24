package jimmy.ai.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * AI 记忆置信度引擎。
 * <p>
 * 用多维度信号替代单一关键词匹配来评估一条候选记忆的可靠性：
 * <ol>
 *   <li><b>不确定表达检测</b>：文本是否包含"可能/也许/大概/猜测"等模型推测用语</li>
 *   <li><b>来源匹配度</b>：用户原话是否实际表达了记忆声称的偏好</li>
 *   <li><b>LLM 提取置信度</b>：模型自己给出的置信度分数</li>
 *   <li><b>证据累积</b>：同一记忆被不同对话印证的次数</li>
 *   <li><b>来源质量</b>：LLM 提炼 vs 关键词降级 vs 行为统计</li>
 * </ol>
 * <p>
 * 所有评分逻辑都是纯函数，无副作用，方便单元测试。
 */
@Component
public class AiMemoryConfidenceEngine {

    /**
     * 不确定表达的典型标志词 —— 模型在推测而非陈述事实时会用这些词。
     */
    private static final List<String> UNCERTAINTY_MARKERS = List.of(
            "可能", "也许", "大概", "猜测", "推测", "应该是", "不确定", "疑似",
            "或许", "好像", "看起来", "似乎", "估计", "不太确定"
    );

    /**
     * 显式偏好信号 —— 用户主动表达长期意愿时使用的短语。
     */
    private static final List<String> EXPLICIT_PREFERENCE_MARKERS = List.of(
            "以后", "以后都", "以后不要", "默认", "记住", "我希望", "我喜欢",
            "不要", "只查", "别查", "习惯", "一直", "常用", "一般先", "偏好"
    );

    /**
     * 最低置信度阈值 —— 低于此值的候选记忆直接丢弃。
     */
    public static final double MIN_CONFIDENCE_THRESHOLD = 0.72;

    /**
     * 自动升级为 ACTIVE 所需的最低证据数。
     */
    public static final int AUTO_PROMOTE_MIN_EVIDENCE = 2;

    /**
     * 自动升级为 ACTIVE 所需的最低置信度。
     */
    public static final double AUTO_PROMOTE_MIN_CONFIDENCE = 0.85;

    /**
     * 记忆未被召回/强化超过此天数后开始衰减。
     */
    public static final int DECAY_AFTER_DAYS = 14;

    /**
     * 记忆衰减后超过此天数自动归档。
     */
    public static final int ARCHIVE_AFTER_DAYS = 30;

    /**
     * 已归档记忆超过此天数后物理删除。
     */
    public static final int PURGE_AFTER_DAYS = 90;

    /**
     * 每次衰减降低的置信度步长。
     */
    public static final double DECAY_STEP = 0.08;

    /**
     * 计算一条候选记忆的幻觉风险评分 (0~1)。
     * <p>
     * 评分越高 = 越可能是幻觉。打分基于四个维度：
     * <ul>
     *   <li>不确定词密度（权重 0.35）</li>
     *   <li>LLM 置信度反向（权重 0.30）</li>
     *   <li>来源不匹配度（权重 0.25）</li>
     *   <li>缺乏显式偏好信号（权重 0.10）</li>
     * </ul>
     *
     * @param text             记忆标题 + 摘要合并后的文本
     * @param userMessage      用户原始消息（脱敏后）
     * @param llmConfidence    LLM 提取时给出的置信度（关键词降级时用默认值）
     * @param sourceMatched    来源验证是否通过（用户原话是否支持该记忆）
     * @param isLlmExtracted  是否由 LLM 提炼（false 表示关键词降级或行为统计）
     * @return 幻觉风险评分 0~1
     */
    public double hallucinationRisk(String text,
                                    String userMessage,
                                    double llmConfidence,
                                    boolean sourceMatched,
                                    boolean isLlmExtracted) {
        String lower = normalize(text);
        String userLower = normalize(userMessage);

        // 维度1：不确定词密度 (0~1)
        double uncertaintyScore = uncertaintyDensity(lower);

        // 维度2：LLM 置信度反向 (0~1) —— 置信度越低，风险越高
        double llmConfidenceInverse = 1.0 - Math.max(0.0, Math.min(1.0, llmConfidence));

        // 维度3：来源不匹配度 (0~1) —— 未通过来源验证则满分
        double sourceMismatchScore = sourceMatched ? 0.0 : (isLlmExtracted ? 1.0 : 0.45);

        // 维度4：缺乏显式偏好信号 (0~1)
        double noExplicitSignalScore = hasExplicitPreference(userLower) ? 0.0 : 0.4;

        // 加权求和
        double risk = uncertaintyScore * 0.35
                + llmConfidenceInverse * 0.20
                + sourceMismatchScore * 0.35
                + noExplicitSignalScore * 0.10;

        return clamp(risk, 0.0, 1.0);
    }

    /**
     * 判断一条候选记忆是否应自动升级为 ACTIVE。
     * <p>
     * 条件：证据数达标 AND 置信度达标 AND 无不确定表达 AND 来源已验证。
     */
    public boolean shouldAutoPromote(int evidenceCount,
                                     double confidence,
                                     String text,
                                     boolean sourceMatched) {
        return evidenceCount >= AUTO_PROMOTE_MIN_EVIDENCE
                && confidence >= AUTO_PROMOTE_MIN_CONFIDENCE
                && !containsUncertainty(normalize(text))
                && sourceMatched;
    }

    /**
     * 判断一条 SUSPECTED_HALLUCINATION 记忆是否应该自动恢复为 CANDIDATE。
     * <p>
     * 条件：后续有新证据（用户行为吻合或新的相似对话印证）。
     */
    public boolean shouldAutoRecoverFromHallucination(int evidenceCount,
                                                       double confidence,
                                                       boolean recentMatchFound) {
        return recentMatchFound && evidenceCount >= 1 && confidence >= 0.78;
    }

    /**
     * 判断一条 ACTIVE 记忆是否需要衰减（降为 WEAKENING）。
     */
    public boolean shouldDecay(int daysSinceLastRecall, int daysSinceLastReinforce) {
        return Math.min(daysSinceLastRecall, daysSinceLastReinforce) >= DECAY_AFTER_DAYS;
    }

    /**
     * 判断一条 WEAKENING 记忆是否需要归档。
     */
    public boolean shouldArchive(int daysSinceLastRecall) {
        return daysSinceLastRecall >= ARCHIVE_AFTER_DAYS;
    }

    /**
     * 检测文本中是否包含不确定表达。
     */
    public boolean containsUncertainty(String text) {
        return containsAny(text, UNCERTAINTY_MARKERS);
    }

    /**
     * 检测用户消息中是否包含显式偏好信号。
     */
    public boolean hasExplicitPreference(String userMessage) {
        return containsAny(userMessage, EXPLICIT_PREFERENCE_MARKERS);
    }

    /**
     * 计算不确定词在文本中的密度。
     * <p>
     * 归一化到 0~1：命中 1 个不确定词得 0.3，2 个得 0.6，3 个及以上得 1.0。
     */
    private double uncertaintyDensity(String text) {
        if (!StringUtils.hasText(text)) {
            return 0.0;
        }
        int count = 0;
        for (String marker : UNCERTAINTY_MARKERS) {
            if (text.contains(marker)) {
                count++;
            }
        }
        return clamp(count / 3.0, 0.0, 1.0);
    }

    private boolean containsAny(String value, List<String> words) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (String word : words) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
