package jimmy.util;

import org.springframework.util.StringUtils;

/**
 * 日志脱敏工具 —— 固定位星号替换，防止从星号数量反推原始数据长度。
 *
 * <h3>脱敏规则</h3>
 * <table border="1">
 *   <tr><th>方法</th><th>保留规则</th><th>星号数（固定）</th><th>示例</th></tr>
 *   <tr><td>maskName</td><td>首字 + 末字</td><td>3</td><td>张三丰 → 张***丰</td></tr>
 *   <tr><td>maskAccount</td><td>首字 + 末字</td><td>3</td><td>administrators → a***s</td></tr>
 *   <tr><td>maskText</td><td>前2 + 后2字</td><td>4</td><td>Mozilla...Chrome/136 → Mo****36</td></tr>
 *   <tr><td>maskId</td><td>末4位</td><td>6(长) / 3(短)</td><td>10086 → ******0086</td></tr>
 *   <tr><td>maskIp</td><td>前两段子网</td><td>格式固定</td><td>192.168.1.100 → 192.168.***.***</td></tr>
 * </table>
 *
 * <p>短值处理：1字符 → {@code *}；2字符 → 首字+固定*；3-4字符 → 首字+固定*+末字。</p>
 */
public final class LogMaskUtils {

    private LogMaskUtils() {
    }

    /** maskName / maskAccount 固定星号数 */
    private static final int FIXED_MASK_NAME = 3;
    /** maskText 固定星号数 */
    private static final int FIXED_MASK_TEXT = 4;
    /** maskId 短值固定星号数（长度 ≤ 4） */
    private static final int FIXED_MASK_ID_SHORT = 3;
    /** maskId 长值固定星号数（长度 ≥ 5） */
    private static final int FIXED_MASK_ID_LONG = 6;

    // ==================== 公开方法 ====================

    /**
     * 姓名脱敏：保留首尾各1字符，中间固定3个星号。
     * <p>例：张三丰 → 张***丰，李 → *，王五 → 王***</p>
     */
    public static String maskName(String value) {
        return mask(value, 1, 1, FIXED_MASK_NAME);
    }

    /**
     * 账号脱敏：保留首尾各1字符，中间固定3个星号。
     * <p>例：administrators → a***s，root → r***</p>
     */
    public static String maskAccount(String value) {
        return mask(value, 1, 1, FIXED_MASK_NAME);
    }

    /**
     * 通用文本脱敏：保留前2后2字符，中间固定4个星号。
     * <p>例：Mozilla...Chrome/136 → Mo****36</p>
     */
    public static String maskText(String value) {
        return mask(value, 2, 2, FIXED_MASK_TEXT);
    }

    /**
     * ID脱敏：短ID（≤4位）固定3星+末1位；长ID（≥5位）固定6星+末4位。
     * <p>短ID只看末位，外人无法从星号数反推原始位数。</p>
     * <ul>
     *   <li>1 → ***1</li>
     *   <li>86 → ***6</li>
     *   <li>100 → ***0</li>
     *   <li>1000 → ***0</li>
     *   <li>10086 → ******0086</li>
     *   <li>123456789 → ******6789</li>
     * </ul>
     */
    public static String maskId(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int len = value.length();
        if (len <= 4) {
            // 1-4位短ID：固定3星 + 末1位，外部无法区分原值是1/2/3/4位
            return repeat('*', FIXED_MASK_ID_SHORT) + value.charAt(len - 1);
        }
        // 5位以上长ID：固定6星 + 末4位
        return repeat('*', FIXED_MASK_ID_LONG) + value.substring(len - 4);
    }

    /**
     * IP地址脱敏：保留前两段网段，后两段掩码。
     * <p>例：192.168.1.100 → 192.168.***.***；10.0.1.2 → 10.0.***.***</p>
     * <p>IP格式固定，无需防长度推断。</p>
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
     * 通用脱敏：前缀+固定星号+后缀。
     *
     * @param value          原始字符串
     * @param prefixLength   保留前缀字符数
     * @param suffixLength   保留后缀字符数
     * @param fixedMaskCount 固定星号数量（不随原始长度变化）
     * @return 脱敏后字符串
     */
    private static String mask(String value, int prefixLength, int suffixLength, int fixedMaskCount) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        int length = trimmed.length();

        // 1字符：仅显示1个星号，无法区分首尾
        if (length == 1) {
            return "*";
        }
        // 2字符：显示首字符 + 固定星号（末字符与首字符重叠，不重复展示）
        if (length == 2) {
            return trimmed.charAt(0) + repeat('*', fixedMaskCount);
        }
        // 3字符以上但 ≤ 保留位之和：仅展示首尾各1字符 + 固定星号
        if (length <= prefixLength + suffixLength) {
            return trimmed.charAt(0) + repeat('*', fixedMaskCount) + trimmed.charAt(length - 1);
        }

        // 常规长度：固定前缀 + 固定星号 + 固定后缀
        String prefix = trimmed.substring(0, prefixLength);
        String suffix = trimmed.substring(length - suffixLength);
        return prefix + repeat('*', fixedMaskCount) + suffix;
    }

    /**
     * 重复指定字符。
     *
     * @param value 重复字符
     * @param count 重复次数
     * @return 重复后的字符串
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
