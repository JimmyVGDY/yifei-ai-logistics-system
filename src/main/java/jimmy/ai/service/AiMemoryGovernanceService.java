package jimmy.ai.service;

import jimmy.ai.model.AiMemoryCandidate;
import jimmy.ai.model.AiMemoryGovernanceDecision;
import jimmy.ai.model.AiToolCall;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * AI 长期记忆治理策略。
 * <p>
 * 负责把模型抽取出的记忆候选转换成可维护的元数据（生命周期、作用域、冲突组、优先级），
 * 并决定初始状态。治理判定综合多维度信号：
 * <ol>
 *   <li><b>幻觉风险评估</b>：不确定词密度 + LLM 置信度 + 来源匹配度 + 显式偏好信号</li>
 *   <li><b>来源验证</b>：用户原话是否实际支持记忆声称的偏好</li>
 *   <li><b>冲突检测</b>：是否与同冲突组的已有生效记忆矛盾</li>
 *   <li><b>置信度阈值</b>：低于阈值的直接建议丢弃</li>
 * </ol>
 */
@Service
public class AiMemoryGovernanceService {

    public static final String STATUS_CANDIDATE = "CANDIDATE";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CONFLICTED = "CONFLICTED";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";
    public static final String STATUS_WEAKENING = "WEAKENING";
    public static final String STATUS_HALLUCINATION = "SUSPECTED_HALLUCINATION";

    public static final String SCOPE_GLOBAL = "GLOBAL";
    public static final String SCOPE_MODULE = "MODULE";
    public static final String SCOPE_SCENARIO = "SCENARIO";

    /** 幻觉风险阈值：超过此值的候选记忆直接标记为疑似幻觉 */
    public static final double HALLUCINATION_RISK_THRESHOLD = 0.55;

    private final AiMemoryConfidenceEngine confidenceEngine;
    private final AiMemorySourceVerifier sourceVerifier;

    public AiMemoryGovernanceService(AiMemoryConfidenceEngine confidenceEngine,
                                      AiMemorySourceVerifier sourceVerifier) {
        this.confidenceEngine = confidenceEngine;
        this.sourceVerifier = sourceVerifier;
    }

    /**
     * 根据候选内容和本轮对话上下文做治理判定。
     * <p>
     * 判定流程：
     * 1. 来源验证——用户原话是否支持记忆的偏好声称
     * 2. 多维度幻觉风险评估
     * 3. 综合确定初始状态
     * 4. 推断作用域、冲突组、优先级
     */
    public AiMemoryGovernanceDecision decide(AiMemoryCandidate candidate,
                                             String userMessage,
                                             List<AiToolCall> toolCalls) {
        return decide(candidate, userMessage, toolCalls, true);
    }

    /**
     * 带来源标记的治理判定。
     *
     * @param isLlmExtracted 是否由 LLM 提炼（false 表示关键词降级或行为统计）
     */
    public AiMemoryGovernanceDecision decide(AiMemoryCandidate candidate,
                                             String userMessage,
                                             List<AiToolCall> toolCalls,
                                             boolean isLlmExtracted) {
        String type = safe(candidate.memoryType()).toUpperCase(Locale.ROOT);
        String title = safe(candidate.memoryTitle());
        String summary = safe(candidate.memorySummary());
        String text = (title + " " + summary + " " + safe(userMessage)).toLowerCase(Locale.ROOT);

        // 来源验证
        AiMemorySourceVerifier.VerificationResult verification = sourceVerifier.verify(candidate, userMessage);
        boolean sourceMatched = verification != AiMemorySourceVerifier.VerificationResult.NO_MATCH;
        boolean strongSourceMatch = verification == AiMemorySourceVerifier.VerificationResult.STRONG_MATCH;

        // 多维度幻觉风险评估
        double risk = confidenceEngine.hallucinationRisk(
                text, safe(userMessage), candidate.confidence(), sourceMatched, isLlmExtracted);

        // 显式偏好检测
        boolean explicit = confidenceEngine.hasExplicitPreference(safe(userMessage).toLowerCase(Locale.ROOT));

        ScopeDecision scope = inferScope(type, text, toolCalls);
        String conflictGroup = inferConflictGroup(type, text, scope);
        int priority = inferPriority(type, explicit, scope.memoryScope());
        String status = inferInitialStatus(candidate.confidence(), explicit, type, text, risk,
                sourceMatched, strongSourceMatch, isLlmExtracted);
        String memoryKey = buildMemoryKey(type, conflictGroup, summary);
        String policyJson = buildPolicyJson(type, scope, conflictGroup, priority, explicit, risk, isLlmExtracted);

        return new AiMemoryGovernanceDecision(
                memoryKey,
                scope.memoryScope(),
                scope.scopeValue(),
                conflictGroup,
                status,
                priority,
                explicit ? 2 : 1,
                policyJson
        );
    }

    public boolean isRecallable(AiMemoryGovernanceDecision decision) {
        return decision != null && isRecallableStatus(decision.status());
    }

    public boolean isRecallableStatus(String status) {
        return STATUS_ACTIVE.equals(status) || STATUS_WEAKENING.equals(status);
    }

    /**
     * 根据多维度信号推断初始状态。
     * <p>
     * 优先级（从高到低）：
     * <ol>
     *   <li>高幻觉风险（≥0.55）→ SUSPECTED_HALLUCINATION，无论置信度多高</li>
     *   <li>显式偏好 + 高置信度(≥0.82) + 低幻觉风险 → ACTIVE</li>
     *   <li>显式偏好 + 中等置信度(≥0.75) + 强来源匹配 → ACTIVE</li>
     *   <li>LLM 提炼 + 置信度≥0.92 + 低幻觉风险 + 强来源匹配 → ACTIVE</li>
     *   <li>其他 → CANDIDATE</li>
     * </ol>
     */
    private String inferInitialStatus(double confidence, boolean explicit, String type,
                                       String text, double hallucinationRisk,
                                       boolean sourceMatched, boolean strongSourceMatch, boolean isLlmExtracted) {
        // LLM 提炼出的偏好类记忆如果没有用户原话支撑，先隔离，避免把模型猜测写成长期偏好。
        if (isLlmExtracted
                && !sourceMatched
                && !explicit
                && ("ANSWER_STYLE".equals(type) || "QUERY_HABIT".equals(type) || "FAVORITE_MODULE".equals(type))) {
            return STATUS_HALLUCINATION;
        }

        // 高幻觉风险 → 隔离，无论其他信号多强
        if (hallucinationRisk >= HALLUCINATION_RISK_THRESHOLD) {
            return STATUS_HALLUCINATION;
        }

        // 显式偏好 + 高置信度 + 低幻觉风险 → 直接生效
        if (explicit && confidence >= 0.82 && hallucinationRisk < 0.35) {
            return STATUS_ACTIVE;
        }

        // 显式偏好 + 中等置信度 + 强来源匹配 → 直接生效
        if (explicit && confidence >= 0.75 && strongSourceMatch && hallucinationRisk < 0.40) {
            return STATUS_ACTIVE;
        }

        // LLM 提炼 + 非常高置信度 + 低风险 + 强来源匹配 → 直接生效
        if (isLlmExtracted && confidence >= 0.92 && hallucinationRisk < 0.25 && strongSourceMatch) {
            return STATUS_ACTIVE;
        }

        // FAVORITE_MODULE 类型默认候选，等待更多证据
        if ("FAVORITE_MODULE".equals(type)) {
            return STATUS_CANDIDATE;
        }

        // 中等置信度但幻觉风险可控 → 候选
        if (confidence >= 0.78 && hallucinationRisk < 0.45) {
            return STATUS_CANDIDATE;
        }

        // 低置信度但非幻觉 → 候选（等待证据积累后自动升级）
        return STATUS_CANDIDATE;
    }

    private ScopeDecision inferScope(String type, String text, List<AiToolCall> toolCalls) {
        if ("ANSWER_STYLE".equals(type)) {
            return new ScopeDecision(SCOPE_GLOBAL, "answer_style");
        }
        if (containsAny(text, "运输任务", "任务管理") && containsAny(text, "异常", "问题", "故障")) {
            return new ScopeDecision(SCOPE_SCENARIO, "task_exception");
        }
        if (containsAny(text, "运输任务", "任务管理")) {
            return new ScopeDecision(SCOPE_MODULE, "tasks");
        }
        if (containsAny(text, "异常管理", "异常记录")) {
            return new ScopeDecision(SCOPE_MODULE, "exceptions");
        }
        if (containsAny(text, "当前范围", "当前模块", "不要扩展", "别扩展")) {
            return new ScopeDecision(SCOPE_GLOBAL, "query_scope_current_first");
        }
        if (containsAny(text, "全貌", "关联", "所有业务", "一次性获取", "一次性查")) {
            return new ScopeDecision(SCOPE_GLOBAL, "query_expand_when_requested");
        }
        String firstToolTarget = firstToolTarget(toolCalls);
        if ("FAVORITE_MODULE".equals(type) && StringUtils.hasText(firstToolTarget)) {
            return new ScopeDecision(SCOPE_MODULE, normalizeModuleScope(firstToolTarget));
        }
        return new ScopeDecision(SCOPE_GLOBAL, type.toLowerCase(Locale.ROOT));
    }

    private String inferConflictGroup(String type, String text, ScopeDecision scope) {
        if ("ANSWER_STYLE".equals(type)) {
            if (containsAny(text, "简短", "详细", "啰嗦", "一句话", "完整说明")) {
                return "answer_style:verbosity";
            }
            if (containsAny(text, "表格", "列表", "markdown", "格式")) {
                return "answer_style:format";
            }
            return "answer_style:general";
        }
        if (SCOPE_SCENARIO.equals(scope.memoryScope())) {
            return "query_scope:" + scope.scopeValue();
        }
        if ("query_expand_when_requested".equals(scope.scopeValue())) {
            return "query_strategy:expand";
        }
        if ("query_scope_current_first".equals(scope.scopeValue())) {
            return "query_strategy:scope";
        }
        return type.toLowerCase(Locale.ROOT) + ":" + scope.scopeValue();
    }

    private int inferPriority(String type, boolean explicit, String scope) {
        int base = switch (type) {
            case "ANSWER_STYLE" -> 70;
            case "QUERY_HABIT" -> 60;
            case "FAVORITE_MODULE" -> 45;
            default -> 40;
        };
        if (SCOPE_SCENARIO.equals(scope)) {
            base += 10;
        }
        if (explicit) {
            base += 15;
        }
        return Math.min(base, 95);
    }

    private String buildMemoryKey(String type, String conflictGroup, String summary) {
        return type.toLowerCase(Locale.ROOT) + ":" + conflictGroup + ":" + digest(summary);
    }

    private String buildPolicyJson(String type, ScopeDecision scope, String conflictGroup,
                                    int priority, boolean explicit,
                                    double hallucinationRisk, boolean isLlmExtracted) {
        return """
                {"memoryType":"%s","scope":"%s","scopeValue":"%s","conflictGroup":"%s","priority":%d,"explicit":%s,"hallucinationRisk":%.2f,"source":"%s"}
                """.formatted(
                escape(type), escape(scope.memoryScope()), escape(scope.scopeValue()),
                escape(conflictGroup), priority, explicit,
                hallucinationRisk, isLlmExtracted ? "llm" : "keyword"
        ).trim();
    }

    private String firstToolTarget(List<AiToolCall> toolCalls) {
        for (AiToolCall toolCall : toolCalls == null ? List.<AiToolCall>of() : toolCalls) {
            if (toolCall != null && StringUtils.hasText(toolCall.target())) {
                return toolCall.target();
            }
        }
        return "";
    }

    private String normalizeModuleScope(String target) {
        String value = safe(target);
        if (value.contains("运输任务")) return "tasks";
        if (value.contains("异常")) return "exceptions";
        if (value.contains("运单")) return "orders";
        if (value.contains("客户")) return "customers";
        if (value.contains("费用")) return "fees";
        if (value.contains("司机")) return "drivers";
        if (value.contains("车辆")) return "vehicles";
        return value;
    }

    private boolean containsAny(String value, String... words) {
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

    private String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(safe(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 24);
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(safe(value).hashCode());
        }
    }

    private String escape(String value) {
        return safe(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record ScopeDecision(String memoryScope, String scopeValue) {
    }
}
