package jimmy.logistics.config;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.logistics.annotation.OperationLog;
import jimmy.util.LogMaskUtils;
import lombok.extern.slf4j.Slf4j;
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

    private final JdbcTemplate jdbcTemplate;

    public OperationLogInterceptor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        request.setAttribute(OPERATION_USERNAME_ATTRIBUTE, currentUsername());
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
        try {
            jdbcTemplate.update(
                    "insert into sys_operation_log (username, operation, request_uri, request_method, operation_status, operation_time) values (?, ?, ?, ?, ?, current_timestamp)",
                    username,
                    operation,
                    request.getRequestURI(),
                    request.getMethod(),
                    status
            );
            log.info("操作日志已记录，username={}, operation={}, status={}",
                    LogMaskUtils.maskAccount(username), operation, status);
        } catch (RuntimeException logException) {
            // 操作日志不能影响主业务请求，写入失败只保留系统日志用于排查。
            log.warn("操作日志写入失败，operation={}, reason={}", operation, logException.getMessage());
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
        return loginId == null ? "anonymous" : String.valueOf(loginId);
    }
}
