package jimmy.logistics.config;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.annotation.OperationLog;
import jimmy.util.LogMaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@Component
public class OperationLogInterceptor implements HandlerInterceptor {

    private static final String OPERATION_USERNAME_ATTRIBUTE = "operationLogUsername";
    private static final String START_TIME_ATTRIBUTE = "operationLogStartTime";

    private final JdbcTemplate jdbcTemplate;
    private final CompactSnowflakeIdGenerator idGenerator;

    public OperationLogInterceptor(JdbcTemplate jdbcTemplate, CompactSnowflakeIdGenerator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(OPERATION_USERNAME_ATTRIBUTE, currentUsername());
        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception exception) {
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
        String status = exception == null && response.getStatus() < 400 ? "SUCCESS" : "FAILED";
        long costMs = System.currentTimeMillis() - (Long) request.getAttribute(START_TIME_ATTRIBUTE);
        MDC.put("module", firstPath(request.getRequestURI()));
        MDC.put("operation", operation);
        MDC.put("costMs", String.valueOf(costMs));
        MDC.put("result", status);
        try {
            jdbcTemplate.update(
                    "insert into sys_operation_log (id, username, operation, request_uri, request_method, operation_status, operation_time) values (?, ?, ?, ?, ?, ?, current_timestamp)",
                    idGenerator.nextId(),
                    username,
                    operation,
                    request.getRequestURI(),
                    request.getMethod(),
                    status
            );
            log.info("操作日志已记录，userId={}, username={}, operation={}, status={}, costMs={}",
                    StpUtil.getLoginIdDefaultNull(), LogMaskUtils.maskAccount(username), operation, status, costMs);
        } catch (RuntimeException logException) {
            log.warn("操作日志写入失败，operation={}, reason={}", operation, logException.getMessage());
        } finally {
            MDC.remove("module");
            MDC.remove("operation");
            MDC.remove("costMs");
            MDC.remove("result");
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

    private String firstPath(String uri) {
        if (uri == null || uri.length() <= 1) {
            return "system";
        }
        String[] parts = uri.substring(1).split("/");
        return parts.length == 0 ? "system" : parts[0];
    }
}
