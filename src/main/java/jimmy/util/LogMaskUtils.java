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
     * 对数字/字符串ID进行脱敏，尽可能保留可辨识信息。
     * <ul>
     *   <li>1位：不脱敏（如 userId=1 显示为 "1"）</li>
     *   <li>2-4位：保留后1-2位（如 "100" → "**00"）</li>
     *   <li>5位以上：保留后4位（如 "10086" → "****10086"）</li>
     * </ul>
     * <p>用于日志中的用户ID、订单号、角色ID等，防止日志文件泄露后批量获取数据。
     */
    public static String maskId(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int len = value.length();
        if (len == 1) {
            // 单字符不脱敏，如 userId=1（admin）
            return value;
        }
        if (len <= 3) {
            return repeat('*', len - 1) + value.charAt(len - 1);
        }
        if (len <= 4) {
            return "**" + value.substring(len - 2);
        }
        return "****" + value.substring(len - 4);
    }

    /**
     * 对IP地址脱敏，保留前两段用于定位子网，后两段掩码。
     * <p>例：192.168.1.100 → 192.168.***.***，兼顾隐私与调试定位。
     */
    public static String maskIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return "";
        }
        int lastDot = ip.lastIndexOf('.');
        if (lastDot <= 0) {
            return maskText(ip);
        }
        int secondLastDot = ip.lastIndexOf('.', lastDot - 1);
        if (secondLastDot <= 0) {
            return ip.substring(0, lastDot) + ".***";
        }
        return ip.substring(0, secondLastDot) + ".***.***";
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
