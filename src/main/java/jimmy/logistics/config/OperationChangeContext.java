package jimmy.logistics.config;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

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
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
            return null;
        }
        return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
    }
}
