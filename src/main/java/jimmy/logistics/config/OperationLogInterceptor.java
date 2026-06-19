package jimmy.logistics.config;

import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.common.trace.TraceContextSupport;
import jimmy.logistics.mapper.OperationLogMapper;
import jimmy.logistics.model.OperationChangeContext;
import jimmy.logistics.util.ColumnExistenceChecker;
import jimmy.common.util.LogMaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 操作日志拦截器 —— 自动拦截所有 Controller 请求，生成 traceId/operationId，
 * 在请求完成后将操作人、接口、耗时、IP、变更摘要等信息写入操作日志表和日志文件。
 * <p>
 * 请求上下文提取（客户端IP、UA、参数脱敏、用户身份）委托给 {@link OperationContext}。
 */
@Slf4j
@Component
public class OperationLogInterceptor implements HandlerInterceptor {

    private static final String USERNAME_ATTR = "operationLogUsername";
    private static final String START_TIME_ATTR = "operationLogStartTime";
    private static final String OPERATION_ID_ATTR = "operationLogOperationId";
    private static final String TRACE_ID_ATTR = "operationLogTraceId";
    private static final int MAX_LOG_TEXT_LENGTH = 255;

    private final OperationLogMapper operationLogMapper;
    private final ColumnExistenceChecker columnChecker;
    private final CompactSnowflakeIdGenerator idGenerator;
    private final TraceContextSupport traceContextSupport;
    private final OperationNameResolver operationNameResolver;

    public OperationLogInterceptor(OperationLogMapper operationLogMapper,
                                   ColumnExistenceChecker columnChecker,
                                   CompactSnowflakeIdGenerator idGenerator,
                                   TraceContextSupport traceContextSupport,
                                   OperationNameResolver operationNameResolver) {
        this.operationLogMapper = operationLogMapper;
        this.columnChecker = columnChecker;
        this.idGenerator = idGenerator;
        this.traceContextSupport = traceContextSupport;
        this.operationNameResolver = operationNameResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = resolveTraceId(request);
        String operationId = resolveOperationId(request);
        String username = OperationContext.currentUsername();

        request.setAttribute(TRACE_ID_ATTR, traceId);
        request.setAttribute(OPERATION_ID_ATTR, operationId);
        request.setAttribute(USERNAME_ATTR, username);
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        response.setHeader("X-Trace-Id", traceId);
        response.setHeader("X-Operation-Id", operationId);

        MDC.put(TraceContextSupport.TRACE_ID, traceId);
        MDC.put(TraceContextSupport.OPERATION_ID, operationId);
        MDC.put(TraceContextSupport.USER_ID, OperationContext.currentUserId());
        MDC.put(TraceContextSupport.USER_CODE, OperationContext.currentUserCode());
        MDC.put(TraceContextSupport.LOGIN_SESSION_ID, OperationContext.currentLoginSessionId());
        MDC.put(TraceContextSupport.USERNAME_MASKED, LogMaskUtils.maskAccount(username));
        MDC.put(TraceContextSupport.ROLE_CODE, OperationContext.currentRoleCode());
        MDC.put(TraceContextSupport.REQUEST_URI, request.getRequestURI());
        MDC.put(TraceContextSupport.REQUEST_METHOD, request.getMethod());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        try {
            if (!(handler instanceof HandlerMethod handlerMethod)) {
                return;
            }
            String operation = operationNameResolver.resolve(handlerMethod, request);
            if (operation == null && !isBusinessWrite(request)) return;
            if (operation == null) return;

            String username = String.valueOf(request.getAttribute(USERNAME_ATTR));
            if ("anonymous".equals(username)) {
                username = OperationContext.currentUsername();
            }
            String traceId = String.valueOf(request.getAttribute(TRACE_ID_ATTR));
            String operationId = String.valueOf(request.getAttribute(OPERATION_ID_ATTR));
            String status = exception == null && response.getStatus() < 400 ? "SUCCESS" : "FAILED";
            long costMs = System.currentTimeMillis() - (Long) request.getAttribute(START_TIME_ATTR);
            String errorMessage = exception == null ? null : OperationContext.sanitizeErrorMessage(exception.getMessage());

            MDC.put(TraceContextSupport.MODULE, firstPath(request.getRequestURI()));
            MDC.put(TraceContextSupport.OPERATION, operation);
            MDC.put(TraceContextSupport.COST_MS, String.valueOf(costMs));
            MDC.put(TraceContextSupport.RESULT, status);

            try {
                insertCompatibleOperationLog(username, operation, request, status, costMs, traceId, operationId, errorMessage);
                String changeSummary = OperationChangeContext.changeSummary(request);
                String ip = OperationContext.clientIp(request);
                String targetId = OperationContext.targetId(request);
                String ua = OperationContext.userAgent(request);
                String loginSessionId = OperationContext.currentLoginSessionId();
                log.info("操作日志已记录，operationId={}, traceId={}, loginSessionId={}, userId={}, username={}, operation={}, uri={}, method={}, status={}, costMs={}, ip={}, targetId={}, ua={}{}",
                        operationId, traceId, loginSessionId, OperationContext.currentUserId(), LogMaskUtils.maskAccount(username), operation,
                        request.getRequestURI(), request.getMethod(), status, costMs, LogMaskUtils.maskIp(ip),
                        targetId == null ? "-" : targetId, LogMaskUtils.maskText(ua),
                        changeSummary != null && !changeSummary.isEmpty() ? ", change=" + changeSummary : "");
            } catch (RuntimeException logException) {
                insertLegacyLog(username, operation, request, status);
                log.warn("操作日志扩展字段写入失败，已回退基础日志，operation={}, reason={}", operation, logException.getMessage());
            }
        } finally {
            traceContextSupport.clearTraceContext();
        }
    }

    // ── DB write ──

    private void insertCompatibleOperationLog(String username, String operation, HttpServletRequest request,
                                              String status, long costMs, String traceId, String operationId,
                                              String errorMessage) {
        if (!columnChecker.hasColumn("sys_operation_log", "operation_id")) {
            insertLegacyLog(username, operation, request, status);
            return;
        }
        boolean hasSession = columnChecker.hasColumn("sys_operation_log", "login_session_id");
        boolean hasErrorMsg = columnChecker.hasColumn("sys_operation_log", "error_message");
        boolean hasClient = hasClientContextColumns();
        String userId = OperationContext.currentUserId();
        String userCode = OperationContext.currentUserCode();
        String roleCode = OperationContext.currentRoleCode();
        String sessionId = OperationContext.currentLoginSessionId();
        String err = OperationContext.truncate(errorMessage, MAX_LOG_TEXT_LENGTH);

        if (hasErrorMsg) {
            if (hasClient) {
                if (hasSession) {
                    operationLogMapper.insertOperationLogWithClientContextAndSession(
                            idGenerator.nextId(), operationId, traceId, sessionId, userId, userCode, username,
                            roleCode, operation, request.getRequestURI(), request.getMethod(), status, costMs,
                            err, OperationContext.clientIp(request), OperationContext.userAgent(request),
                            OperationContext.requestParams(request), OperationContext.targetId(request),
                            OperationChangeContext.changeSummary(request));
                } else {
                    operationLogMapper.insertOperationLogWithClientContext(
                            idGenerator.nextId(), operationId, traceId, userId, userCode, username,
                            roleCode, operation, request.getRequestURI(), request.getMethod(), status, costMs,
                            err, OperationContext.clientIp(request), OperationContext.userAgent(request),
                            OperationContext.requestParams(request), OperationContext.targetId(request),
                            OperationChangeContext.changeSummary(request));
                }
                return;
            }
            if (hasSession) {
                operationLogMapper.insertOperationLogWithSession(
                        idGenerator.nextId(), operationId, traceId, sessionId, userId, userCode, username,
                        roleCode, operation, request.getRequestURI(), request.getMethod(), status, costMs, err);
            } else {
                operationLogMapper.insertOperationLog(
                        idGenerator.nextId(), operationId, traceId, userId, userCode, username,
                        roleCode, operation, request.getRequestURI(), request.getMethod(), status, costMs, err);
            }
            return;
        }
        if (hasSession) {
            operationLogMapper.insertOperationLogWithoutErrorMessageWithSession(
                    idGenerator.nextId(), operationId, traceId, sessionId, userId, userCode, username,
                    roleCode, operation, request.getRequestURI(), request.getMethod(), status, costMs);
        } else {
            operationLogMapper.insertOperationLogWithoutErrorMessage(
                    idGenerator.nextId(), operationId, traceId, userId, userCode, username,
                    roleCode, operation, request.getRequestURI(), request.getMethod(), status, costMs);
        }
    }

    private boolean hasClientContextColumns() {
        return columnChecker.hasColumn("sys_operation_log", "client_ip")
                && columnChecker.hasColumn("sys_operation_log", "user_agent")
                && columnChecker.hasColumn("sys_operation_log", "request_params")
                && columnChecker.hasColumn("sys_operation_log", "target_id")
                && columnChecker.hasColumn("sys_operation_log", "change_summary");
    }

    private void insertLegacyLog(String username, String operation, HttpServletRequest request, String status) {
        try {
            operationLogMapper.insertLegacyOperationLog(
                    idGenerator.nextId(), username, operation,
                    request.getRequestURI(), request.getMethod(), status);
        } catch (RuntimeException ignored) {
            log.warn("基础操作日志写入失败，operation={}", operation);
        }
    }

    // ── Routing ──

    private boolean isBusinessWrite(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        return isAuditedBusinessPath(uri)
                && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method));
    }

    private boolean isAuditedBusinessPath(String uri) {
        return uri.startsWith("/logistics/") || uri.startsWith("/auth/")
                || uri.startsWith("/system/") || uri.startsWith("/infra/")
                || uri.startsWith("/ai/") || uri.startsWith("/demo-users")
                || uri.startsWith("/bloom-filter/") || uri.startsWith("/rabbitmq/");
    }

    // ── Trace ──

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId != null && !traceId.trim().isEmpty()) return traceId.trim();
        String currentTraceId = MDC.get(TraceContextSupport.TRACE_ID);
        return currentTraceId == null || currentTraceId.trim().isEmpty()
                ? traceContextSupport.newTraceId() : currentTraceId;
    }

    private String resolveOperationId(HttpServletRequest request) {
        String operationId = request.getHeader("X-Operation-Id");
        if (operationId != null && !operationId.trim().isEmpty()) return operationId.trim();
        return traceContextSupport.newOperationId();
    }

    private String firstPath(String uri) {
        if (uri == null || uri.length() <= 1) return "system";
        String[] parts = uri.substring(1).split("/");
        return parts.length == 0 ? "system" : parts[0];
    }
}
