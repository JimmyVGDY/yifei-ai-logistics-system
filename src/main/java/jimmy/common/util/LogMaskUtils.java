package jimmy.common.util;

import org.springframework.util.StringUtils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 日志脱敏工具 —— 随机星号替换，彻底消除统计反推可能。
 *
 * <h3>脱敏规则</h3>
 * <table border="1">
 *   <tr><th>方法</th><th>保留规则</th><th>星号</th><th>示例</th></tr>
 *   <tr><td>maskPhone</td><td>前3后4（固定位数11）</td><td>4星固定</td><td>138****1234</td></tr>
 *   <tr><td>maskIdCard</td><td>前6后4（固定位数18）</td><td>8星固定</td><td>110101****5678</td></tr>
 *   <tr><td>maskName</td><td>前2后4</td><td>随机4~8星</td><td>Al**** Lee / Al****** Lee</td></tr>
 *   <tr><td>maskAccount</td><td>前2后4</td><td>随机4~8星</td><td>ad****tor / ad*******tor</td></tr>
 *   <tr><td>maskText</td><td>前2后4</td><td>随机4~8星</td><td>Mo****36## / Mo*******36##</td></tr>
 *   <tr><td>maskId</td><td>前2后4</td><td>随机4~8星</td><td>10****0086 / 10*******0086</td></tr>
 *   <tr><td>maskIp</td><td>前两段子网</td><td>格式固定</td><td>192.168.***.***</td></tr>
 * </table>
 *
 * <p>所有非定长字段统一使用前2后4 + 随机4~8星号，规则一致、代码简洁。</p>
 * <p>手机号/身份证号属于定长格式，长度由数据本身决定，星号数无需随机。</p>
 * <p>非定长字段每次调用随机取值，同一用户的两次日志星号数可能不同，无法聚合分析。</p>
 */
public final class LogMaskUtils {

    private LogMaskUtils() {
    }

    /** 通用随机星号范围（min/max 闭区间），适用于所有非定长字段 */
    private static final int STAR_MIN = 4, STAR_MAX = 8;

    // ==================== 定长字段（长度固定，星号数随之固定） ====================

    /**
     * 手机号脱敏（固定11位）：前3 + 4星 + 后4。
     * <p>例：13812341234 → 138****1234</p>
     */
    public static String maskPhone(String value) {
        if (!StringUtils.hasText(value) || value.trim().length() != 11) {
            // 非标准手机号长度，回退到通用规则
            return maskGeneral(value);
        }
        String trimmed = value.trim();
        return trimmed.substring(0, 3) + "****" + trimmed.substring(7);
    }

    /**
     * 身份证号脱敏（固定18位）：前6（地区+生日）+ 8星 + 后4。
     * <p>例：110101199001011234 → 110101********1234</p>
     */
    public static String maskIdCard(String value) {
        if (!StringUtils.hasText(value) || value.trim().length() != 18) {
            // 非标准身份证号长度，回退到通用规则
            return maskGeneral(value);
        }
        String trimmed = value.trim();
        return trimmed.substring(0, 6) + "********" + trimmed.substring(14);
    }

    // ==================== 非定长字段（统一规则：前2后4 + 随机4~8星） ====================

    /**
     * 姓名脱敏：前2后4 + 随机4~8星。
     * <p>例：张三丰 → 张****丰（短名字退化为首+星+尾）</p>
     */
    public static String maskName(String value) {
        return maskGeneral(value);
    }

    /**
     * 账号脱敏：前2后4 + 随机4~8星。
     * <p>例：administrators → ad*******tor</p>
     */
    public static String maskAccount(String value) {
        return maskGeneral(value);
    }

    /**
     * 通用文本脱敏：前2后4 + 随机4~8星。
     * <p>例：Mozilla...Chrome/136 → Mo****36##</p>
     */
    public static String maskText(String value) {
        return maskGeneral(value);
    }

    /**
     * ID脱敏：前2后4 + 随机4~8星。
     * <p>例：10086 → 10****0086 / 10******0086</p>
     */
    public static String maskId(String value) {
        return maskGeneral(value);
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
            return maskGeneral(ip);
        }
        int secondLastDot = ip.lastIndexOf('.', lastDot - 1);
        if (secondLastDot <= 0) {
            return ip.substring(0, lastDot) + ".***";
        }
        return ip.substring(0, secondLastDot) + ".***.***";
    }

    // ==================== 内部实现 ====================

    /**
     * 通用脱敏：前2后4 + [4~8]随机星号（统一规则）。
     *
     * <p>内部自动处理短字符串退化：
     * <ul>
     *   <li>1字符 → 1个星号</li>
     *   <li>2字符 → 首 + [4~8]随机星 + 尾</li>
     *   <li>≤6字符（前缀+后缀之和） → 首 + [4~8]随机星 + 尾</li>
     *   <li>&gt;6字符 → 前2 + [4~8]随机星 + 后4</li>
     * </ul>
     *
     * @param value 原始字符串
     * @return 脱敏后字符串（每次随机，同一输入可能不同输出）
     */
    private static String maskGeneral(String value) {
        if (!StringUtils.hasText(value)) {
            return value == null ? "" : value;
        }
        String trimmed = value.trim();
        int length = trimmed.length();

        // 1字符：仅显示1个星号
        if (length == 1) {
            return "*";
        }
        // 2字符：首 + 随机星号 + 尾（首尾可能不同，如 "86"）
        if (length == 2) {
            return trimmed.charAt(0) + randomStars() + trimmed.charAt(1);
        }
        // 短字符串（≤ 前2+后4=6）：退化为首 + 随机星号 + 尾
        if (length <= 6) {
            return trimmed.charAt(0) + randomStars() + trimmed.charAt(length - 1);
        }
        // 常规长度：前2 + 随机星号 + 后4
        return trimmed.substring(0, 2) + randomStars() + trimmed.substring(length - 4);
    }

    /**
     * 在 [4, 8] 闭区间内随机取一个整数，生成对应数量的星号字符串。
     */
    private static String randomStars() {
        int count = STAR_MIN + ThreadLocalRandom.current().nextInt(STAR_MAX - STAR_MIN + 1);
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
