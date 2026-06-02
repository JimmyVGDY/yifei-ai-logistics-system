package jimmy.logistics.config;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.annotation.OperationLog;
import jimmy.logistics.mapper.OperationLogMapper;
import jimmy.logistics.util.ColumnExistenceChecker;
import jimmy.util.LogMaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

@Slf4j
@Component
public class OperationLogInterceptor implements HandlerInterceptor {

    private static final String OPERATION_USERNAME_ATTRIBUTE = "operationLogUsername";
    private static final String START_TIME_ATTRIBUTE = "operationLogStartTime";
    private static final String OPERATION_ID_ATTRIBUTE = "operationLogOperationId";
    private static final String TRACE_ID_ATTRIBUTE = "operationLogTraceId";

    private final OperationLogMapper operationLogMapper;
    private final ColumnExistenceChecker columnChecker;
    private final CompactSnowflakeIdGenerator idGenerator;

    public OperationLogInterceptor(OperationLogMapper operationLogMapper,
                                   ColumnExistenceChecker columnChecker,
                                   CompactSnowflakeIdGenerator idGenerator) {
        this.operationLogMapper = operationLogMapper;
        this.columnChecker = columnChecker;
        this.idGenerator = idGenerator;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = resolveTraceId(request);
        String operationId = String.valueOf(idGenerator.nextId());
        String username = currentUsername();
        // 每次请求生成唯一操作 ID，并与 traceId 一起写入响应头，方便前端反馈问题时反查日志。
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        request.setAttribute(OPERATION_ID_ATTRIBUTE, operationId);
        request.setAttribute(OPERATION_USERNAME_ATTRIBUTE, username);
        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
        response.setHeader("X-Trace-Id", traceId);
        response.setHeader("X-Operation-Id", operationId);

        MDC.put("traceId", traceId);
        MDC.put("operationId", operationId);
        // MDC 字段会被 logback 写入 JSON 日志，业务日志和操作日志可通过 traceId 串起来。
        MDC.put("userId", currentUserId());
        MDC.put("userCode", currentUserCode());
        MDC.put("usernameMasked", LogMaskUtils.maskAccount(username));
        MDC.put("roleCode", currentRoleCode());
        MDC.put("requestUri", request.getRequestURI());
        MDC.put("requestMethod", request.getMethod());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception exception) {
        try {
            if (!(handler instanceof HandlerMethod)) {
                return;
            }

            HandlerMethod handlerMethod = (HandlerMethod) handler;
            String operation = resolveOperation(handlerMethod);
            if (operation == null && !isBusinessWrite(request)) {
                return;
            }
            if (operation == null) {
                // 未显式标注 @OperationLog 的写接口仍然记录，避免关键变更没有审计痕迹。
                operation = "业务接口调用";
            }

            String username = String.valueOf(request.getAttribute(OPERATION_USERNAME_ATTRIBUTE));
            if ("anonymous".equals(username)) {
                username = currentUsername();
            }
            String traceId = String.valueOf(request.getAttribute(TRACE_ID_ATTRIBUTE));
            String operationId = String.valueOf(request.getAttribute(OPERATION_ID_ATTRIBUTE));
            String status = exception == null && response.getStatus() < 400 ? "SUCCESS" : "FAILED";
            long costMs = System.currentTimeMillis() - (Long) request.getAttribute(START_TIME_ATTRIBUTE);
            String errorMessage = exception == null ? null : exception.getMessage();
            MDC.put("module", firstPath(request.getRequestURI()));
            MDC.put("operation", operation);
            MDC.put("costMs", String.valueOf(costMs));
            MDC.put("result", status);

            try {
                // 优先写入包含 traceId、operationId、耗时、异常信息等扩展字段的新日志结构。
                insertCompatibleOperationLog(username, operation, request, status, costMs, traceId, operationId, errorMessage);
                log.info("操作日志已记录，operationId={}, traceId={}, userId={}, username={}, operation={}, uri={}, method={}, status={}, costMs={}",
                        operationId, traceId, currentUserId(), LogMaskUtils.maskAccount(username), operation,
                        request.getRequestURI(), request.getMethod(), status, costMs);
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
                                              String errorMessage) {
        if (!columnChecker.hasColumn("sys_operation_log", "operation_id")) {
            insertLegacyLog(username, operation, request, status);
            return;
        }
        if (columnChecker.hasColumn("sys_operation_log", "error_message")) {
            operationLogMapper.insertOperationLog(
                    idGenerator.nextId(), operationId, traceId, currentUserId(), currentUserCode(), username,
                    currentRoleCode(), operation, request.getRequestURI(), request.getMethod(), status, costMs, errorMessage
            );
            return;
        }
        operationLogMapper.insertOperationLogWithoutErrorMessage(
                idGenerator.nextId(), operationId, traceId, currentUserId(), currentUserCode(), username,
                currentRoleCode(), operation, request.getRequestURI(), request.getMethod(), status, costMs
        );
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

    private String resolveOperation(HandlerMethod handlerMethod) {
        OperationLog methodLog = handlerMethod.getMethodAnnotation(OperationLog.class);
        if (methodLog != null) {
            return methodLog.value();
        }
        OperationLog typeLog = handlerMethod.getBeanType().getAnnotation(OperationLog.class);
        return typeLog == null ? null : typeLog.value();
    }

    private boolean isBusinessWrite(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        return uri.startsWith("/logistics/")
                && ("POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method));
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
        String currentTraceId = MDC.get("traceId");
        // 没有上游 traceId 时本服务生成一个，保证一次请求内日志可以被统一检索。
        return currentTraceId == null || currentTraceId.trim().isEmpty() ? UUID.randomUUID().toString().replace("-", "") : currentTraceId;
    }

    private void clearRequestMdc() {
        MDC.remove("traceId");
        MDC.remove("operationId");
        MDC.remove("userId");
        MDC.remove("userCode");
        MDC.remove("usernameMasked");
        MDC.remove("roleCode");
        MDC.remove("requestUri");
        MDC.remove("requestMethod");
        MDC.remove("module");
        MDC.remove("operation");
        MDC.remove("costMs");
        MDC.remove("result");
    }

    private String firstPath(String uri) {
        if (uri == null || uri.length() <= 1) {
            return "system";
        }
        String[] parts = uri.substring(1).split("/");
        return parts.length == 0 ? "system" : parts[0];
    }
}
