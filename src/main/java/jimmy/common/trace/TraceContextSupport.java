package jimmy.common.trace;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.model.LogisticsOrderEvent;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 链路上下文工具：统一管理 traceId、operationId、loginSessionId 和系统任务上下文。
 */
@Component
public class TraceContextSupport {

    public static final String TRACE_ID = "traceId";
    public static final String OPERATION_ID = "operationId";
    public static final String LOGIN_SESSION_ID = "loginSessionId";
    public static final String USER_ID = "userId";
    public static final String USER_CODE = "userCode";
    public static final String USERNAME_MASKED = "usernameMasked";
    public static final String ROLE_CODE = "roleCode";
    public static final String REQUEST_URI = "requestUri";
    public static final String REQUEST_METHOD = "requestMethod";
    public static final String MODULE = "module";
    public static final String OPERATION = "operation";
    public static final String COST_MS = "costMs";
    public static final String RESULT = "result";
    public static final String JOB_RUN_ID = "jobRunId";

    private final CompactSnowflakeIdGenerator idGenerator;

    public TraceContextSupport(CompactSnowflakeIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    public String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public String newOperationId() {
        return String.valueOf(idGenerator.nextId());
    }

    public String newLoginSessionId() {
        return String.valueOf(idGenerator.nextId());
    }

    public String newJobRunId(String jobName) {
        String safeJobName = hasText(jobName) ? jobName.trim() : "job";
        return "JOB-" + safeJobName + "-" + idGenerator.nextId();
    }

    /**
     * 从 Sa-Token 会话恢复当前登录用户上下文，供本次 HTTP 请求内日志使用。
     */
    public void bindCurrentLoginSession() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            return;
        }
        put(USER_ID, String.valueOf(loginId));
        put(USER_CODE, String.valueOf(StpUtil.getSessionByLoginId(loginId).get("userCode", "")));
        put(USERNAME_MASKED, String.valueOf(StpUtil.getSessionByLoginId(loginId).get("usernameMasked", "")));
        put(ROLE_CODE, String.valueOf(StpUtil.getSessionByLoginId(loginId).get("roleCode", "")));
        put(LOGIN_SESSION_ID, String.valueOf(StpUtil.getSessionByLoginId(loginId).get(LOGIN_SESSION_ID, "")));
    }

    /**
     * 发布异步消息时复制当前上下文，保证消费者可以延续同一业务链路。
     */
    public void captureEventContext(LogisticsOrderEvent event) {
        if (event == null) {
            return;
        }
        event.setTraceId(currentOrNewTraceId());
        event.setOperationId(currentOrNewOperationId());
        event.setLoginSessionId(current(LOGIN_SESSION_ID));
        event.setUserId(current(USER_ID));
        event.setUserCode(current(USER_CODE));
        event.setUsernameMasked(current(USERNAME_MASKED));
        event.setRoleCode(current(ROLE_CODE));
    }

    /**
     * 消费异步消息时恢复上下文；老消息缺少 traceId 时在消费端补生成。
     */
    public void restoreEventContext(LogisticsOrderEvent event) {
        clearTraceContext();
        if (event == null) {
            put(TRACE_ID, newTraceId());
            put(OPERATION_ID, newOperationId());
            put(MODULE, "message");
            put(OPERATION, "RabbitMQ消息消费");
            return;
        }
        put(TRACE_ID, hasText(event.getTraceId()) ? event.getTraceId() : newTraceId());
        put(OPERATION_ID, hasText(event.getOperationId()) ? event.getOperationId() : newOperationId());
        put(LOGIN_SESSION_ID, event.getLoginSessionId());
        put(USER_ID, event.getUserId());
        put(USER_CODE, event.getUserCode());
        put(USERNAME_MASKED, event.getUsernameMasked());
        put(ROLE_CODE, event.getRoleCode());
        put(MODULE, "message");
        put(OPERATION, event.getEventType());
    }

    /**
     * 为一次程序自主任务生成独立上下文。
     */
    public String bindJobContext(String jobName) {
        clearTraceContext();
        String jobRunId = newJobRunId(jobName);
        put(TRACE_ID, newTraceId());
        put(OPERATION_ID, jobRunId);
        put(JOB_RUN_ID, jobRunId);
        put(USER_ID, "system");
        put(USER_CODE, "system");
        put(USERNAME_MASKED, "system");
        put(ROLE_CODE, "SYSTEM");
        put(MODULE, "job");
        put(OPERATION, jobName);
        return jobRunId;
    }

    public String current(String key) {
        String value = MDC.get(key);
        return value == null ? "" : value;
    }

    public String currentOrNewTraceId() {
        String traceId = current(TRACE_ID);
        if (hasText(traceId)) {
            return traceId;
        }
        traceId = newTraceId();
        put(TRACE_ID, traceId);
        return traceId;
    }

    public String currentOrNewOperationId() {
        String operationId = current(OPERATION_ID);
        if (hasText(operationId)) {
            return operationId;
        }
        operationId = newOperationId();
        put(OPERATION_ID, operationId);
        return operationId;
    }

    public void clearTraceContext() {
        MDC.remove(TRACE_ID);
        MDC.remove(OPERATION_ID);
        MDC.remove(LOGIN_SESSION_ID);
        MDC.remove(USER_ID);
        MDC.remove(USER_CODE);
        MDC.remove(USERNAME_MASKED);
        MDC.remove(ROLE_CODE);
        MDC.remove(REQUEST_URI);
        MDC.remove(REQUEST_METHOD);
        MDC.remove(MODULE);
        MDC.remove(OPERATION);
        MDC.remove(COST_MS);
        MDC.remove(RESULT);
        MDC.remove(JOB_RUN_ID);
    }

    public void put(String key, String value) {
        if (hasText(key) && hasText(value)) {
            MDC.put(key, value);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
