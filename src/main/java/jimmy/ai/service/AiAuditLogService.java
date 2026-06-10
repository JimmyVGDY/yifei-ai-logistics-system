package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.util.SseChatContext;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.common.trace.TraceContextSupport;
import jimmy.logistics.mapper.OperationLogMapper;
import jimmy.logistics.util.ColumnExistenceChecker;
import jimmy.util.LogMaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * AI 审计日志服务。
 * <p>
 * 普通操作日志只能说明用户请求了 /ai/chat，无法区分“用户提问”“AI 调用只读工具”
 * 和“AI 生成回答”。本服务把 AI 链路拆成独立审计记录，便于后续排查责任主体和执行过程。
 */
@Slf4j
@Service
public class AiAuditLogService {

    public static final String SOURCE_USER_TO_AI = "USER_TO_AI";
    public static final String SOURCE_AI_TOOL = "AI_TOOL";
    public static final String SOURCE_AI_RESPONSE = "AI_RESPONSE";
    public static final String SOURCE_AI_MEMORY = "AI_MEMORY";
    public static final String EXECUTOR_USER = "USER";
    public static final String EXECUTOR_AI = "AI";

    private static final int MAX_SUMMARY_LENGTH = 1000;
    private static final String AI_REQUEST_URI = "/ai/chat";
    private static final String AI_REQUEST_METHOD = "POST";

    /**
     * AI 审计扩展字段存在性缓存，避免每次写审计日志都重复查询 information_schema。
     * 使用 volatile + synchronized 双重检查保证线程安全，且仅首次初始化时查一次数据库。
     */
    private volatile Boolean columnsExist;

    private final OperationLogMapper operationLogMapper;
    private final ColumnExistenceChecker columnChecker;
    private final CompactSnowflakeIdGenerator idGenerator;
    private final TraceContextSupport traceContextSupport;
    private final AiSensitiveDataMasker masker;

    public AiAuditLogService(OperationLogMapper operationLogMapper,
                             ColumnExistenceChecker columnChecker,
                             CompactSnowflakeIdGenerator idGenerator,
                             TraceContextSupport traceContextSupport,
                             AiSensitiveDataMasker masker) {
        this.operationLogMapper = operationLogMapper;
        this.columnChecker = columnChecker;
        this.idGenerator = idGenerator;
        this.traceContextSupport = traceContextSupport;
        this.masker = masker;
    }

    public void recordUserQuestion(String conversationId, String promptSummary) {
        record("AI助手-用户提问", SOURCE_USER_TO_AI, EXECUTOR_USER, conversationId,
                null, null, true, promptSummary, "用户向 AI 助手发起只读问答请求", 0L);
    }

    public void recordToolCall(String conversationId, String toolName, String toolTarget, String promptSummary, String resultSummary) {
        record("AI助手-只读工具调用", SOURCE_AI_TOOL, EXECUTOR_AI, conversationId,
                toolName, toolTarget, true, promptSummary, resultSummary, 0L);
    }

    public void recordResponse(String conversationId, String promptSummary, String resultSummary, long costMs) {
        record("AI助手-生成回答", SOURCE_AI_RESPONSE, EXECUTOR_AI, conversationId,
                null, null, true, promptSummary, resultSummary, costMs);
    }

    public void recordMemory(String conversationId,
                             String eventType,
                             String source,
                             String memoryId,
                             int hitCount,
                             String traceSummary) {
        record("AI助手-长期记忆审计", SOURCE_AI_MEMORY, EXECUTOR_AI, conversationId,
                "长期记忆", eventType, true, traceSummary, traceSummary, 0L,
                truncate(memoryId, 64), truncate(eventType, 64), truncate(source, 64), hitCount, traceSummary);
    }

    private void record(String operation,
                        String operationSource,
                        String executorType,
                        String conversationId,
                        String toolName,
                        String toolTarget,
                        boolean readonly,
                        String promptSummary,
                        String resultSummary,
                        long costMs) {
        record(operation, operationSource, executorType, conversationId, toolName, toolTarget, readonly,
                promptSummary, resultSummary, costMs, null, null, null, null, null);
    }

    private void record(String operation,
                        String operationSource,
                        String executorType,
                        String conversationId,
                        String toolName,
                        String toolTarget,
                        boolean readonly,
                        String promptSummary,
                        String resultSummary,
                        long costMs,
                        String memoryId,
                        String memoryEventType,
                        String memorySource,
                        Integer memoryHitCount,
                        String memoryTraceSummary) {
        if (!aiAuditColumnsExist()) {
            log.debug("AI 审计扩展字段不存在，跳过 AI 分层审计日志，operation={}", operation);
            return;
        }
        try {
            operationLogMapper.insertAiAuditLog(
                    idGenerator.nextId(),
                    traceContextSupport.newOperationId(),
                    traceContextSupport.currentOrNewTraceId(),
                    currentLoginSessionId(),
                    currentUserId(),
                    currentUserCode(),
                    currentUsername(),
                    currentRoleCode(),
                    operation,
                    AI_REQUEST_URI,
                    AI_REQUEST_METHOD,
                    "SUCCESS",
                    costMs,
                    operationSource,
                    executorType,
                    truncate(conversationId, 64),
                    traceContextSupport.newOperationId(),
                    truncate(toolName, 64),
                    truncate(toolTarget, 128),
                    readonly,
                    safeSummary(promptSummary),
                    safeSummary(resultSummary),
                    memoryId,
                    memoryEventType,
                    memorySource,
                    memoryHitCount,
                    safeSummary(memoryTraceSummary)
            );
            log.info("AI审计日志已记录，source={}, executor={}, conversationId={}, tool={}, target={}",
                    operationSource, executorType, conversationId, toolName == null ? "-" : toolName,
                    toolTarget == null ? "-" : toolTarget);
        } catch (RuntimeException exception) {
            log.warn("AI 审计日志写入失败，operation={}, reason={}", operation, exception.getMessage());
        }
    }

    private boolean aiAuditColumnsExist() {
        Boolean cached = columnsExist;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (columnsExist != null) {
                return columnsExist;
            }
            boolean exist = columnChecker.hasColumn("sys_operation_log", "operation_source")
                    && columnChecker.hasColumn("sys_operation_log", "executor_type")
                    && columnChecker.hasColumn("sys_operation_log", "ai_conversation_id")
                    && columnChecker.hasColumn("sys_operation_log", "ai_tool_name")
                    && columnChecker.hasColumn("sys_operation_log", "ai_prompt_summary")
                    && columnChecker.hasColumn("sys_operation_log", "ai_result_summary")
                    && columnChecker.hasColumn("sys_operation_log", "ai_memory_id")
                    && columnChecker.hasColumn("sys_operation_log", "ai_memory_event_type")
                    && columnChecker.hasColumn("sys_operation_log", "ai_memory_source")
                    && columnChecker.hasColumn("sys_operation_log", "ai_memory_hit_count")
                    && columnChecker.hasColumn("sys_operation_log", "ai_memory_trace_summary");
            columnsExist = exist;
            return exist;
        }
    }

    private String safeSummary(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return truncate(masker.mask(LogMaskUtils.maskText(value)), MAX_SUMMARY_LENGTH);
    }

    private String currentUserId() {
        Object loginId = currentLoginId();
        return loginId == null ? "" : String.valueOf(loginId);
    }

    private String currentUserCode() {
        Object loginId = currentLoginId();
        return loginId == null ? "" : String.valueOf(StpUtil.getSessionByLoginId(loginId).get("userCode", ""));
    }

    private String currentUsername() {
        Object loginId = currentLoginId();
        if (loginId == null) {
            return "anonymous";
        }
        return String.valueOf(StpUtil.getSessionByLoginId(loginId).get("username", loginId));
    }

    private String currentRoleCode() {
        Object loginId = currentLoginId();
        return loginId == null ? "" : String.valueOf(StpUtil.getSessionByLoginId(loginId).get("roleCode", ""));
    }

    private String currentLoginSessionId() {
        Object loginId = currentLoginId();
        return loginId == null ? "" : String.valueOf(StpUtil.getSessionByLoginId(loginId).get(TraceContextSupport.LOGIN_SESSION_ID, ""));
    }

    private Object currentLoginId() {
        // 优先从 SSE 异步线程读取 Controller 传递的登录标识
        String sseLoginId = SseChatContext.getLoginId();
        if (sseLoginId != null && !sseLoginId.isBlank() && !"null".equals(sseLoginId)) {
            return sseLoginId;
        }
        try {
            return StpUtil.getLoginIdDefaultNull();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
