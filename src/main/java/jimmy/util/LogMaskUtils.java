package jimmy.util;

import org.springframework.util.StringUtils;

/**
 * 日志脱敏工具 —— 对姓名/账号/文本进行前后保留、中间星号替换。
 */
public final class LogMaskUtils {

    private LogMaskUtils() {
    }

    public static String maskName(String value) {
        return mask(value, 1, 1);
    }

    public static String maskAccount(String value) {
        return mask(value, 1, 1);
    }

    public static String maskText(String value) {
        return mask(value, 2, 2);
    }

    /**
     * 对数字ID进行脱敏，保留后4位。
     * <p>用于日志中隐藏用户ID、订单ID等数字标识，防止日志文件泄露后批量获取业务数据。
     */
    public static String maskId(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }

    private static String mask(String value, int prefixLength, int suffixLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        int length = trimmed.length();
        if (length == 1) {
            return "*";
        }
        if (length == 2) {
            return trimmed.charAt(0) + "*";
        }
        if (length <= prefixLength + suffixLength) {
            return trimmed.charAt(0) + repeat('*', length - 2) + trimmed.charAt(length - 1);
        }

        String prefix = trimmed.substring(0, prefixLength);
        String suffix = trimmed.substring(length - suffixLength);
        return prefix + repeat('*', length - prefixLength - suffixLength) + suffix;
    }

    private static String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(Math.max(count, 0));
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
