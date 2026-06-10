package jimmy.logistics.model;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 操作变更审计上下文 —— 线程安全的请求级别上下文容器。
 * <p>
 * 写操作在 Service 层通过 {@link #setChangeSummary} 写入变更摘要，
 * OperationLogInterceptor 在请求完成后通过 {@link #changeSummary} 取出并记入审计日志。
 * 数据存储在 HttpServletRequest attribute 中，天然线程安全。
 */
public final class OperationChangeContext {

    private static final String CHANGE_SUMMARY_ATTRIBUTE = "operationLogChangeSummary";

    private OperationChangeContext() {
    }

    public static void setChangeSummary(String summary) {
        HttpServletRequest request = currentRequest();
        if (request == null || summary == null || summary.trim().isEmpty()) {
            return;
        }
        request.setAttribute(CHANGE_SUMMARY_ATTRIBUTE, summary.trim());
    }

    public static String changeSummary(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(CHANGE_SUMMARY_ATTRIBUTE);
        return value == null ? null : String.valueOf(value);
    }

    private static HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servletRequestAttributes)) {
            return null;
        }
        return servletRequestAttributes.getRequest();
    }
}
