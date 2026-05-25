package jimmy.util;

import org.springframework.util.StringUtils;

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
