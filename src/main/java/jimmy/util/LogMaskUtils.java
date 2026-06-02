package jimmy.util;

import org.springframework.util.StringUtils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 日志脱敏工具 —— 随机星号替换，彻底消除统计反推可能。
 *
 * <h3>脱敏规则</h3>
 * <table border="1">
 *   <tr><th>方法</th><th>保留规则</th><th>星号范围（随机）</th><th>示例（每次不同）</th></tr>
 *   <tr><td>maskName</td><td>首字 + 末字</td><td>3 ~ 6</td><td>张***丰 / 张*****丰</td></tr>
 *   <tr><td>maskAccount</td><td>首字 + 末字</td><td>3 ~ 6</td><td>a***s / a*****s</td></tr>
 *   <tr><td>maskText</td><td>前2 + 后2字</td><td>4 ~ 8</td><td>Mo****36 / Mo*******36</td></tr>
 *   <tr><td>maskId（短 2~4位）</td><td>首1位 + 末1位</td><td>3 ~ 6</td><td>8***6 / 8******6</td></tr>
 *   <tr><td>maskId（长 ≥5位）</td><td>前2位 + 后4位</td><td>4 ~ 8</td><td>10****0086 / 10**********0086</td></tr>
 *   <tr><td>maskId（1位）</td><td>—</td><td>1</td><td>*</td></tr>
 *   <tr><td>maskIp</td><td>前两段子网</td><td>格式固定</td><td>192.168.***.***</td></tr>
 * </table>
 *
 * <p>短/长ID星号范围有重叠（3~6 vs 4~8），大量样本也无法区分ID是短还是长。</p>
 * <p>每次调用随机取值，同一用户的两次日志可能显示 ***1 和 *****1，无法聚合分析。</p>
 */
public final class LogMaskUtils {

    private LogMaskUtils() {
    }

    // ==================== 星号范围常量（min/max 闭区间） ====================

    /** maskName / maskAccount 随机星号范围 */
    private static final int NAME_MIN = 3, NAME_MAX = 6;
    /** maskText 随机星号范围 */
    private static final int TEXT_MIN = 4, TEXT_MAX = 8;
    /** maskId 短值（≤4位）随机星号范围，与长值范围有重叠 */
    private static final int ID_SHORT_MIN = 3, ID_SHORT_MAX = 6;
    /** maskId 长值（≥5位）随机星号范围，与短值范围有重叠 */
    private static final int ID_LONG_MIN = 4, ID_LONG_MAX = 8;

    // ==================== 公开方法 ====================

    /**
     * 姓名脱敏：保留首尾各1字符，中间随机3~6个星号。
     * <p>例：张三丰 → 张***丰 / 张******丰，每次不同</p>
     */
    public static String maskName(String value) {
        return mask(value, 1, 1, NAME_MIN, NAME_MAX);
    }

    /**
     * 账号脱敏：保留首尾各1字符，中间随机3~6个星号。
     * <p>例：administrators → a***s / a******s，每次不同</p>
     */
    public static String maskAccount(String value) {
        return mask(value, 1, 1, NAME_MIN, NAME_MAX);
    }

    /**
     * 通用文本脱敏：保留前2后2字符，中间随机4~8个星号。
     * <p>例：Mozilla...Chrome/136 → Mo****36 / Mo********36，每次不同</p>
     */
    public static String maskText(String value) {
        return mask(value, 2, 2, TEXT_MIN, TEXT_MAX);
    }

    /**
     * ID脱敏：1位→*；短ID（2~4位）首尾各留1位；长ID（≥5位）首留2位尾留4位。
     * <p>短/长星号范围重叠，采集再多样本也无法区分是短ID还是长ID。</p>
     * <ul>
     *   <li>1 → *</li>
     *   <li>86 → 8***6 / 8*******6</li>
     *   <li>10086 → 10****0086 / 10**********0086</li>
     * </ul>
     */
    public static String maskId(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int len = value.length();
        if (len == 1) {
            // 1位ID：仅显示1个星号，无头尾可留
            return "*";
        }
        if (len <= 4) {
            // 短ID(2~4位)：前后各留1位 + 随机3~6星
            return mask(value, 1, 1, ID_SHORT_MIN, ID_SHORT_MAX);
        }
        // 长ID(≥5位)：前留2位 + 随机4~8星 + 后留4位，不经过mask()避免短字符串截断
        String prefix = value.substring(0, 2);
        String suffix = value.substring(len - 4);
        return prefix + randomStars(ID_LONG_MIN, ID_LONG_MAX) + suffix;
    }

    /**
     * IP地址脱敏：保留前两段网段，后两段掩码（格式固定，无需随机）。
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

    // ==================== 内部实现 ====================

    /**
     * 通用脱敏：前缀 + [minStars~maxStars]随机星号 + 后缀。
     *
     * @param value        原始字符串
     * @param prefixLength 保留前缀字符数
     * @param suffixLength 保留后缀字符数
     * @param minStars     最小星号数（含）
     * @param maxStars     最大星号数（含）
     * @return 脱敏后字符串（每次随机，同一输入可能不同输出）
     */
    private static String mask(String value, int prefixLength, int suffixLength,
                                int minStars, int maxStars) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        int length = trimmed.length();

        // 1字符：仅显示1个星号
        if (length == 1) {
            return "*";
        }
        // 2字符：显示首字符 + 随机星号 + 末字符（首末可能不同，如ID "86"）
        if (length == 2) {
            return trimmed.charAt(0) + randomStars(minStars, maxStars) + trimmed.charAt(1);
        }
        // 短字符串（≤ 保留位之和）：仅展示首尾各1字符 + 随机星号
        if (length <= prefixLength + suffixLength) {
            return trimmed.charAt(0) + randomStars(minStars, maxStars) + trimmed.charAt(length - 1);
        }

        // 常规长度：固定前缀 + 随机星号 + 固定后缀
        String prefix = trimmed.substring(0, prefixLength);
        String suffix = trimmed.substring(length - suffixLength);
        return prefix + randomStars(minStars, maxStars) + suffix;
    }

    /**
     * 在 [min, max] 闭区间内随机取一个整数，生成对应数量的星号字符串。
     */
    private static String randomStars(int min, int max) {
        int count = min + ThreadLocalRandom.current().nextInt(max - min + 1);
        return repeat('*', count);
    }

    /**
     * 重复指定字符。
     */
    private static String repeat(char value, int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
