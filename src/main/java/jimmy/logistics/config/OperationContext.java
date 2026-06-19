package jimmy.logistics.config;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 操作日志请求上下文 —— 从 HTTP 请求中提取客户端信息，并进行敏感参数过滤和错误消息脱敏。
 * <p>
 * 从 {@link OperationLogInterceptor} 拆分，独立可测。
 */
class OperationContext {

    private static final int MAX_LOG_TEXT_LENGTH = 255;
    private static final int MAX_PARAM_TEXT_LENGTH = 1000;

    static String clientIp(HttpServletRequest request) {
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

    static String userAgent(HttpServletRequest request) {
        return truncate(request.getHeader("User-Agent"), MAX_LOG_TEXT_LENGTH);
    }

    static String requestParams(HttpServletRequest request) {
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
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        return truncate(text, MAX_PARAM_TEXT_LENGTH);
    }

    static String targetId(HttpServletRequest request) {
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

    static String sanitizeErrorMessage(String message) {
        if (message == null) return null;
        String sanitized = message.replaceAll("(1[3-9]\\d)\\d{4}(\\d{4})", "$1****$2");
        sanitized = sanitized.replaceAll("([\\w.+-])[\\w.+-]*@([\\w.-])[\\w.-]*(\\.[a-zA-Z]{2,})", "$1***@$2***$3");
        sanitized = sanitized.replaceAll("\\b(\\d{3})\\d{11}(\\d{4})\\b", "$1***********$2");
        return truncate(sanitized, MAX_LOG_TEXT_LENGTH);
    }

    // ── User identity ──

    static String currentUsername() {
        Object loginId = currentLoginId();
        if (loginId == null) return "anonymous";
        return String.valueOf(StpUtil.getSession().get("username", loginId));
    }

    static String currentUserId() {
        Object loginId = currentLoginId();
        return loginId == null ? "" : String.valueOf(loginId);
    }

    static String currentUserCode() {
        Object loginId = currentLoginId();
        if (loginId == null) return "";
        return String.valueOf(StpUtil.getSession().get("userCode", ""));
    }

    static String currentLoginSessionId() {
        Object loginId = currentLoginId();
        if (loginId == null) return "";
        return String.valueOf(StpUtil.getSessionByLoginId(loginId).get("loginSessionId", ""));
    }

    static String currentRoleCode() {
        Object loginId = currentLoginId();
        if (loginId == null) return "";
        return String.valueOf(StpUtil.getSession().get("roleCode", ""));
    }

    // ── Internal ──

    private static Object currentLoginId() {
        try {
            return StpUtil.getLoginIdDefaultNull();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean isSensitiveParam(String key) {
        String lowerKey = key.toLowerCase();
        return lowerKey.contains("password") || lowerKey.contains("token") || lowerKey.contains("secret")
                || lowerKey.contains("credential") || lowerKey.contains("authorization")
                || lowerKey.contains("message") || lowerKey.contains("prompt")
                || lowerKey.contains("question") || lowerKey.contains("pagecontext")
                || lowerKey.contains("mobile") || lowerKey.contains("phone")
                || lowerKey.contains("email") || lowerKey.contains("id_card") || lowerKey.contains("idcard")
                || lowerKey.contains("bank_card") || lowerKey.contains("bankcard")
                || lowerKey.contains("license") || lowerKey.contains("address") || lowerKey.contains("location");
    }

    static String firstHeaderValue(String value) {
        if (!notBlank(value)) return null;
        return value.split(",")[0].trim();
    }

    static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    static String truncate(String value, int maxLength) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
