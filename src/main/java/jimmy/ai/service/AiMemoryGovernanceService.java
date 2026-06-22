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
 * 它负责把“模型抽取出来的一句话”转换成可维护的记忆元数据：
 * 生命周期、作用域、冲突组、优先级和策略摘要。这样长期记忆不会因为
 * 一句临时纠偏就污染全局行为。
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

    /**
     * 根据候选内容和本轮工具调用上下文做治理判定。
     */
    public AiMemoryGovernanceDecision decide(AiMemoryCandidate candidate,
                                             String userMessage,
                                             List<AiToolCall> toolCalls) {
        String type = safe(candidate.memoryType()).toUpperCase(Locale.ROOT);
        String title = safe(candidate.memoryTitle());
        String summary = safe(candidate.memorySummary());
        String text = (title + " " + summary + " " + safe(userMessage)).toLowerCase(Locale.ROOT);
        /*
         * “明确偏好”只能来自用户原话，不能来自模型抽取出来的摘要。
         * 否则模型把“用户可能希望...”写进候选记忆时，会反过来把自己的猜测升级成有效长期记忆。
         */
        boolean explicit = isExplicitPreference(safe(userMessage).toLowerCase(Locale.ROOT));

        ScopeDecision scope = inferScope(type, text, toolCalls);
        String conflictGroup = inferConflictGroup(type, text, scope);
        int priority = inferPriority(type, explicit, scope.memoryScope());
        String status = inferInitialStatus(candidate.confidence(), explicit, type, text);
        String memoryKey = buildMemoryKey(type, conflictGroup, summary);
        String policyJson = buildPolicyJson(type, scope, conflictGroup, priority, explicit);

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

    private String inferInitialStatus(double confidence, boolean explicit, String type, String text) {
        /*
         * 长期记忆必须有明确证据。包含“可能、猜测、应该、大概”等不确定表达，
         * 且不是用户明确偏好的内容，先隔离为疑似幻觉，等待用户确认或后续证据强化。
         */
        if (!explicit && containsAny(text, "可能", "也许", "大概", "猜测", "推测", "应该是", "不确定", "疑似")) {
            return STATUS_HALLUCINATION;
        }
        if (explicit && confidence >= 0.82) {
            return STATUS_ACTIVE;
        }
        if ("FAVORITE_MODULE".equals(type)) {
            return STATUS_CANDIDATE;
        }
        return confidence >= 0.92 ? STATUS_ACTIVE : STATUS_CANDIDATE;
    }

    private boolean isExplicitPreference(String text) {
        return containsAny(text, "以后", "默认", "记住", "我希望", "我喜欢", "不要", "只查", "别查", "以后都", "以后不要");
    }

    private String buildMemoryKey(String type, String conflictGroup, String summary) {
        return type.toLowerCase(Locale.ROOT) + ":" + conflictGroup + ":" + digest(summary);
    }

    private String buildPolicyJson(String type, ScopeDecision scope, String conflictGroup, int priority, boolean explicit) {
        return """
                {"memoryType":"%s","scope":"%s","scopeValue":"%s","conflictGroup":"%s","priority":%d,"explicit":%s}
                """.formatted(escape(type), escape(scope.memoryScope()), escape(scope.scopeValue()),
                escape(conflictGroup), priority, explicit).trim();
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
