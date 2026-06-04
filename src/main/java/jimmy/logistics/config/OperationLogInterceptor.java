package jimmy.logistics.config;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.config.TraceContextSupport;
import jimmy.logistics.mapper.OperationLogMapper;
import jimmy.logistics.util.ColumnExistenceChecker;
import jimmy.util.LogMaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 操作日志拦截器 —— 自动拦截所有 Controller 请求，生成 traceId/operationId，
 * 在请求完成后将操作人、接口、耗时、IP、变更摘要等信息写入操作日志表和日志文件。
 * 非标注 @OperationLog 的 GET 请求不记录。
 */
@Slf4j
@Component
public class OperationLogInterceptor implements HandlerInterceptor {

    private static final String OPERATION_USERNAME_ATTRIBUTE = "operationLogUsername";
    private static final String START_TIME_ATTRIBUTE = "operationLogStartTime";
    private static final String OPERATION_ID_ATTRIBUTE = "operationLogOperationId";
    private static final String TRACE_ID_ATTRIBUTE = "operationLogTraceId";
    private static final int MAX_LOG_TEXT_LENGTH = 255;
    private static final int MAX_PARAM_TEXT_LENGTH = 1000;

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
        String username = currentUsername();
        // 每次请求生成唯一操作 ID，并与 traceId 一起写入响应头，方便前端反馈问题时反查日志。
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        request.setAttribute(OPERATION_ID_ATTRIBUTE, operationId);
        request.setAttribute(OPERATION_USERNAME_ATTRIBUTE, username);
        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
        response.setHeader("X-Trace-Id", traceId);
        response.setHeader("X-Operation-Id", operationId);

        MDC.put(TraceContextSupport.TRACE_ID, traceId);
        MDC.put(TraceContextSupport.OPERATION_ID, operationId);
        // userId/userCode 是内部审计追踪标识，需要保留原值用于精确检索；用户名等展示字段继续脱敏。
        MDC.put(TraceContextSupport.USER_ID, currentUserId());
        MDC.put(TraceContextSupport.USER_CODE, currentUserCode());
        MDC.put(TraceContextSupport.LOGIN_SESSION_ID, currentLoginSessionId());
        MDC.put(TraceContextSupport.USERNAME_MASKED, LogMaskUtils.maskAccount(username));
        MDC.put(TraceContextSupport.ROLE_CODE, currentRoleCode());
        MDC.put(TraceContextSupport.REQUEST_URI, request.getRequestURI());
        MDC.put(TraceContextSupport.REQUEST_METHOD, request.getMethod());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception exception) {
        try {
            if (!(handler instanceof HandlerMethod handlerMethod)) {
                return;
            }

            String operation = operationNameResolver.resolve(handlerMethod, request);
            if (operation == null && !isBusinessWrite(request)) {
                return;
            }
            if (operation == null) {
                // 仅记录已标注 @OperationLog 或默认写操作的接口，GET 请求和未标注接口不审计。
                return;
            }

            String username = String.valueOf(request.getAttribute(OPERATION_USERNAME_ATTRIBUTE));
            if ("anonymous".equals(username)) {
                username = currentUsername();
            }
            String traceId = String.valueOf(request.getAttribute(TRACE_ID_ATTRIBUTE));
            String operationId = String.valueOf(request.getAttribute(OPERATION_ID_ATTRIBUTE));
            String status = exception == null && response.getStatus() < 400 ? "SUCCESS" : "FAILED";
            long costMs = System.currentTimeMillis() - (Long) request.getAttribute(START_TIME_ATTRIBUTE);
            String errorMessage = exception == null ? null : sanitizeErrorMessage(exception.getMessage());
            MDC.put(TraceContextSupport.MODULE, firstPath(request.getRequestURI()));
            MDC.put(TraceContextSupport.OPERATION, operation);
            MDC.put(TraceContextSupport.COST_MS, String.valueOf(costMs));
            MDC.put(TraceContextSupport.RESULT, status);

            try {
                // 优先写入包含 traceId、operationId、耗时、异常信息等扩展字段的新日志结构。
                insertCompatibleOperationLog(username, operation, request, status, costMs, traceId, operationId, currentLoginSessionId(), errorMessage);
                String changeSummary = OperationChangeContext.changeSummary(request);
                String ip = clientIp(request);
                String targetId = targetId(request);
                String ua = userAgent(request);
                String loginSessionId = currentLoginSessionId();
                log.info("操作日志已记录，operationId={}, traceId={}, loginSessionId={}, userId={}, username={}, operation={}, uri={}, method={}, status={}, costMs={}, ip={}, targetId={}, ua={}{}",
                        operationId, traceId, loginSessionId, currentUserId(), LogMaskUtils.maskAccount(username), operation,
                        request.getRequestURI(), request.getMethod(), status, costMs, LogMaskUtils.maskIp(ip),
                        targetId == null ? "-" : targetId, LogMaskUtils.maskText(ua),
                        changeSummary != null && !changeSummary.isEmpty() ? ", change=" + changeSummary : "");
            } catch (RuntimeException logException) {
                // 如果本地库还没执行增量脚本，回退到旧字段写法，保证业务接口不因审计字段缺失而失败。
                insertLegacyLog(username, operation, request, status);
                log.warn("操作日志扩展字段写入失败，已回退基础日志，operation={}, reason={}", operation, logException.getMessage());
            }
        } finally {
            clearRequestMdc();
        }
    }

    private void insertCompatibleOperationLog(String username, String operation, HttpServletRequest request,
                                              String status, long costMs, String traceId, String operationId,
                                              String loginSessionId, String errorMessage) {
        if (!columnChecker.hasColumn("sys_operation_log", "operation_id")) {
            insertLegacyLog(username, operation, request, status);
            return;
        }
        boolean loginSessionColumnExists = columnChecker.hasColumn("sys_operation_log", "login_session_id");
        if (columnChecker.hasColumn("sys_operation_log", "error_message")) {
            if (hasClientContextColumns()) {
                if (loginSessionColumnExists) {
                    operationLogMapper.insertOperationLogWithClientContextAndSession(
                            idGenerator.nextId(), operationId, traceId, loginSessionId, currentUserId(), currentUserCode(), username,
                            currentRoleCode(), operation, request.getRequestURI(), request.getMethod(), status, costMs,
                            truncate(errorMessage, MAX_LOG_TEXT_LENGTH), clientIp(request), userAgent(request),
                            requestParams(request), targetId(request), changeSummary(request)
                    );
                } else {
                    operationLogMapper.insertOperationLogWithClientContext(
                            idGenerator.nextId(), operationId, traceId, currentUserId(), currentUserCode(), username,
                            currentRoleCode(), operation, request.getRequestURI(), request.getMethod(), status, costMs,
                            truncate(errorMessage, MAX_LOG_TEXT_LENGTH), clientIp(request), userAgent(request),
                            requestParams(request), targetId(request), changeSummary(request)
                    );
                }
                return;
            }
            if (loginSessionColumnExists) {
                operationLogMapper.insertOperationLogWithSession(
                        idGenerator.nextId(), operationId, traceId, loginSessionId, currentUserId(), currentUserCode(), username,
                        currentRoleCode(), operation, request.getRequestURI(), request.getMethod(), status, costMs,
                        truncate(errorMessage, MAX_LOG_TEXT_LENGTH)
                );
            } else {
                operationLogMapper.insertOperationLog(
                        idGenerator.nextId(), operationId, traceId, currentUserId(), currentUserCode(), username,
                        currentRoleCode(), operation, request.getRequestURI(), request.getMethod(), status, costMs,
                        truncate(errorMessage, MAX_LOG_TEXT_LENGTH)
                );
            }
            return;
        }
        if (loginSessionColumnExists) {
            operationLogMapper.insertOperationLogWithoutErrorMessageWithSession(
                    idGenerator.nextId(), operationId, traceId, loginSessionId, currentUserId(), currentUserCode(), username,
                    currentRoleCode(), operation, request.getRequestURI(), request.getMethod(), status, costMs
            );
        } else {
            operationLogMapper.insertOperationLogWithoutErrorMessage(
                    idGenerator.nextId(), operationId, traceId, currentUserId(), currentUserCode(), username,
                    currentRoleCode(), operation, request.getRequestURI(), request.getMethod(), status, costMs
            );
        }
    }

    private boolean hasClientContextColumns() {
        return columnChecker.hasColumn("sys_operation_log", "client_ip")
                && columnChecker.hasColumn("sys_operation_log", "user_agent")
                && columnChecker.hasColumn("sys_operation_log", "request_params")
                && columnChecker.hasColumn("sys_operation_log", "target_id")
                && columnChecker.hasColumn("sys_operation_log", "change_summary");
    }

    private String changeSummary(HttpServletRequest request) {
        return truncate(OperationChangeContext.changeSummary(request), MAX_PARAM_TEXT_LENGTH);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = firstHeaderValue(request.getHeader("X-Forwarded-For"));
        if (notBlank(forwardedFor)) {
            return truncate(forwardedFor, 45);
        }
        String realIp = firstHeaderValue(request.getHeader("X-Real-IP"));
        if (notBlank(realIp)) {
            return truncate(realIp, 45);
        }
        return truncate(request.getRemoteAddr(), 45);
    }

    private String userAgent(HttpServletRequest request) {
        return truncate(request.getHeader("User-Agent"), MAX_LOG_TEXT_LENGTH);
    }

    private String requestParams(HttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        if (parameterMap == null || parameterMap.isEmpty()) {
            return null;
        }
        Map<String, String> safeParams = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String key = entry.getKey();
            if (!notBlank(key) || isSensitiveParam(key)) {
                continue;
            }
            String value = Arrays.stream(entry.getValue() == null ? new String[0] : entry.getValue())
                    .map(item -> truncate(item, 80))
                    .collect(Collectors.joining(","));
            safeParams.put(key, value);
        }
        if (safeParams.isEmpty()) {
            return null;
        }
        String text = safeParams.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        return truncate(text, MAX_PARAM_TEXT_LENGTH);
    }

    private String targetId(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/logistics/modules/")) {
            String[] parts = uri.substring("/logistics/modules/".length()).split("/");
            return parts.length >= 2 ? truncate(parts[1], 64) : null;
        }
        if (uri.matches("/system/permissions/(roles|users)/[^/]+/.*")) {
            String[] parts = uri.substring("/system/permissions/".length()).split("/");
            return parts.length >= 2 ? truncate(parts[0] + ":" + parts[1], 64) : null;
        }
        if (uri.matches("/logistics/exceptions/[^/]+/handle")) {
            return truncate(uri.substring("/logistics/exceptions/".length()).split("/")[0], 64);
        }
        if (uri.matches("/logistics/fees/[^/]+/pay")) {
            return truncate(uri.substring("/logistics/fees/".length()).split("/")[0], 64);
        }
        if (uri.matches("/logistics/fees/generate/[^/]+")) {
            return truncate(uri.substring("/logistics/fees/generate/".length()), 64);
        }
        if (uri.matches("/logistics/orders/[^/]+")) {
            return truncate(uri.substring("/logistics/orders/".length()), 64);
        }
        return null;
    }

    /**
     * 判断请求参数名是否为敏感字段，敏感参数不会被写入日志。
     * 防止 GET 请求中传递手机号/身份证/银行卡等个人信息时被明文记录到日志文件和数据库。
     */
    private boolean isSensitiveParam(String key) {
        String lowerKey = key.toLowerCase();
        return lowerKey.contains("password")
                || lowerKey.contains("token")
                || lowerKey.contains("secret")
                || lowerKey.contains("credential")
                || lowerKey.contains("authorization")
                || lowerKey.contains("mobile")
                || lowerKey.contains("phone")
                || lowerKey.contains("email")
                || lowerKey.contains("id_card")
                || lowerKey.contains("idcard")
                || lowerKey.contains("bank_card")
                || lowerKey.contains("bankcard")
                || lowerKey.contains("license")
                || lowerKey.contains("address")
                || lowerKey.contains("location");
    }

    /**
     * 对异常消息进行安全清洗：脱敏手机号、邮箱、身份证号，保留头尾以支持问题定位。
     * <p>防止业务代码抛出带用户数据的异常时，敏感信息被写入日志文件和数据库。
     * 脱敏后仍可通过部分信息辅助排查（如 "138****5678不存在" 可定位到特定用户）。
     */
    private String sanitizeErrorMessage(String message) {
        if (message == null) {
            return null;
        }
        // 中国大陆 11 位手机号脱敏：保留前3位和后4位 → 138****5678
        String sanitized = message.replaceAll("(1[3-9]\\d)\\d{4}(\\d{4})", "$1****$2");
        // 邮箱地址脱敏：只保留 @ 前后各1位字符 → t***@e***.com
        sanitized = sanitized.replaceAll("([\\w.+-])[\\w.+-]*@([\\w.-])[\\w.-]*(\\.[a-zA-Z]{2,})", "$1***@$2***$3");
        // 18 位身份证号脱敏：只保留前3后4位 → 310***********1234
        sanitized = sanitized.replaceAll("\\b(\\d{3})\\d{11}(\\d{4})\\b", "$1***********$2");
        return truncate(sanitized, MAX_LOG_TEXT_LENGTH);
    }

    private String firstHeaderValue(String value) {
        if (!notBlank(value)) {
            return null;
        }
        return value.split(",")[0].trim();
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private void insertLegacyLog(String username, String operation, HttpServletRequest request, String status) {
        try {
            operationLogMapper.insertLegacyOperationLog(
                    idGenerator.nextId(),
                    username,
                    operation,
                    request.getRequestURI(),
                    request.getMethod(),
                    status
            );
        } catch (RuntimeException ignored) {
            log.warn("基础操作日志写入失败，operation={}", operation);
        }
    }

    private boolean isBusinessWrite(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        return isAuditedBusinessPath(uri)
                && ("POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method));
    }

    private boolean isAuditedBusinessPath(String uri) {
        return uri.startsWith("/logistics/")
                || uri.startsWith("/auth/")
                || uri.startsWith("/system/")
                || uri.startsWith("/infra/")
                || uri.startsWith("/ai/")
                || uri.startsWith("/demo-users")
                || uri.startsWith("/bloom-filter/")
                || uri.startsWith("/rabbitmq/");
    }

    private String currentUsername() {
        Object loginId = currentLoginId();
        if (loginId == null) {
            return "anonymous";
        }
        return String.valueOf(StpUtil.getSession().get("username", loginId));
    }

    private String currentUserId() {
        Object loginId = currentLoginId();
        return loginId == null ? "" : String.valueOf(loginId);
    }

    private String currentUserCode() {
        Object loginId = currentLoginId();
        if (loginId == null) {
            return "";
        }
        return String.valueOf(StpUtil.getSession().get("userCode", ""));
    }

    private String currentLoginSessionId() {
        Object loginId = currentLoginId();
        if (loginId == null) {
            return "";
        }
        return String.valueOf(StpUtil.getSessionByLoginId(loginId).get(TraceContextSupport.LOGIN_SESSION_ID, ""));
    }

    private String currentRoleCode() {
        Object loginId = currentLoginId();
        if (loginId == null) {
            return "";
        }
        return String.valueOf(StpUtil.getSession().get("roleCode", ""));
    }

    private Object currentLoginId() {
        try {
            return StpUtil.getLoginIdDefaultNull();
        } catch (RuntimeException exception) {
            // 单元测试或极端非 Web 调用场景下可能没有 Sa-Token 上下文，日志按匿名请求兜底。
            return null;
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId != null && !traceId.trim().isEmpty()) {
            return traceId.trim();
        }
        String currentTraceId = MDC.get(TraceContextSupport.TRACE_ID);
        // 没有上游 traceId 时本服务生成一个，保证一次请求内日志可以被统一检索。
        return currentTraceId == null || currentTraceId.trim().isEmpty() ? traceContextSupport.newTraceId() : currentTraceId;
    }

    private String resolveOperationId(HttpServletRequest request) {
        String operationId = request.getHeader("X-Operation-Id");
        if (operationId != null && !operationId.trim().isEmpty()) {
            return operationId.trim();
        }
        return traceContextSupport.newOperationId();
    }

    private void clearRequestMdc() {
        traceContextSupport.clearTraceContext();
    }

    private String firstPath(String uri) {
        if (uri == null || uri.length() <= 1) {
            return "system";
        }
        String[] parts = uri.substring(1).split("/");
        return parts.length == 0 ? "system" : parts[0];
    }
}
