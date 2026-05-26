package jimmy.logistics.config;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.annotation.OperationLog;
import jimmy.logistics.mapper.OperationLogMapper;
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
    private final CompactSnowflakeIdGenerator idGenerator;

    public OperationLogInterceptor(OperationLogMapper operationLogMapper, CompactSnowflakeIdGenerator idGenerator) {
        this.operationLogMapper = operationLogMapper;
        this.idGenerator = idGenerator;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = resolveTraceId(request);
        String operationId = String.valueOf(idGenerator.nextId());
        String username = currentUsername();
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        request.setAttribute(OPERATION_ID_ATTRIBUTE, operationId);
        request.setAttribute(OPERATION_USERNAME_ATTRIBUTE, username);
        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
        response.setHeader("X-Trace-Id", traceId);
        response.setHeader("X-Operation-Id", operationId);

        MDC.put("traceId", traceId);
        MDC.put("operationId", operationId);
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
            MDC.put("module", firstPath(request.getRequestURI()));
            MDC.put("operation", operation);
            MDC.put("costMs", String.valueOf(costMs));
            MDC.put("result", status);

            try {
                operationLogMapper.insertOperationLog(
                        idGenerator.nextId(),
                        operationId,
                        traceId,
                        currentUserId(),
                        currentUserCode(),
                        username,
                        currentRoleCode(),
                        operation,
                        request.getRequestURI(),
                        request.getMethod(),
                        status,
                        costMs
                );
                log.info("操作日志已记录，operationId={}, traceId={}, userId={}, username={}, operation={}, uri={}, method={}, status={}, costMs={}",
                        operationId, traceId, currentUserId(), LogMaskUtils.maskAccount(username), operation,
                        request.getRequestURI(), request.getMethod(), status, costMs);
            } catch (RuntimeException logException) {
                insertLegacyLog(username, operation, request, status);
                log.warn("操作日志扩展字段写入失败，已回退基础日志，operation={}, reason={}", operation, logException.getMessage());
            }
        } finally {
            clearRequestMdc();
        }
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
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            return "anonymous";
        }
        return String.valueOf(StpUtil.getSession().get("username", loginId));
    }

    private String currentUserId() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId == null ? "" : String.valueOf(loginId);
    }

    private String currentUserCode() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            return "";
        }
        return String.valueOf(StpUtil.getSession().get("userCode", ""));
    }

    private String currentRoleCode() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            return "";
        }
        return String.valueOf(StpUtil.getSession().get("roleCode", ""));
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId != null && !traceId.trim().isEmpty()) {
            return traceId.trim();
        }
        String currentTraceId = MDC.get("traceId");
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
