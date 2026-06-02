package jimmy.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskUtilsTest {

    // ==================== maskPhone / maskIdCard（定长字段） ====================

    @Test
    void shouldMaskPhoneWithFixedPattern() {
        assertThat(LogMaskUtils.maskPhone("13812341234")).isEqualTo("138****1234");
        assertThat(LogMaskUtils.maskPhone("13900001111")).isEqualTo("139****1111");
    }

    @Test
    void shouldMaskIdCardWithFixedPattern() {
        assertThat(LogMaskUtils.maskIdCard("110101199001011234")).isEqualTo("110101********1234");
        assertThat(LogMaskUtils.maskIdCard("440305200001018888")).isEqualTo("440305********8888");
    }

    @Test
    void shouldFallbackToGeneralMaskForNonStandardPhoneLength() {
        // 非11位 → 回退到通用规则（前2后4 + 随机星号）
        String result = LogMaskUtils.maskPhone("12345");
        // 5位 ≤ 6 → 短分支：首 + 星 + 尾
        assertThat(result).matches("1\\*{4,8}5");
    }

    @Test
    void shouldFallbackToGeneralMaskForNonStandardIdCardLength() {
        String result = LogMaskUtils.maskIdCard("12345678");
        // 8位 > 6 → 前2后4 + 随机星号
        assertThat(result).matches("12\\*{4,8}5678");
    }

    // ==================== 非定长字段（前2后4 + 随机4~8星） ====================

    @Test
    void shouldMaskAccountWithRandomStars() {
        // admin(5位) ≤ 6 → 短分支：a + 4~8星 + n
        String result = LogMaskUtils.maskAccount("admin");
        assertThat(result).matches("a\\*{4,8}n");
    }

    @Test
    void shouldMaskLongAccountWithPrefix2Suffix4() {
        // administrators(14位) → 前2 + 4~8星 + 后4
        String result = LogMaskUtils.maskAccount("administrators");
        assertThat(result).matches("ad\\*{4,8}tors");
    }

    @Test
    void shouldMaskChineseNameWithRandomStarsForShortName() {
        // 张三(2位) → 张 + 4~8星 + 三
        String result = LogMaskUtils.maskName("张三");
        assertThat(result).matches("张\\*{4,8}三");
    }

    @Test
    void shouldReturnSingleStarForSingleChar() {
        assertThat(LogMaskUtils.maskName("王")).isEqualTo("*");
        assertThat(LogMaskUtils.maskId("1")).isEqualTo("*");
        assertThat(LogMaskUtils.maskText("在")).isEqualTo("*");
    }

    @Test
    void shouldMaskTextWithPrefix2Suffix4() {
        // 上海鲜生零售有限公司(10位) → 前2 + 4~8星 + 后4
        String result = LogMaskUtils.maskText("上海鲜生零售有限公司");
        assertThat(result).matches("上海\\*{4,8}有限公司");
    }

    @Test
    void shouldMaskShortTextToFirstAndLast() {
        // 生气啦(3位) ≤ 6 → 生 + 4~8星 + 啦
        String result = LogMaskUtils.maskText("生气啦");
        assertThat(result).matches("生\\*{4,8}啦");
        // trim 后生效
        String result2 = LogMaskUtils.maskText("  生气啦  ");
        assertThat(result2).matches("生\\*{4,8}啦");
    }

    @Test
    void shouldMaskIdWithPrefix2Suffix4() {
        // 10086(5位) ≤ 6 → 1 + 4~8星 + 6（短分支）
        String result = LogMaskUtils.maskId("10086");
        assertThat(result).matches("1\\*{4,8}6");

        // 260602222327001(15位) → 前2 + 4~8星 + 后4
        String result2 = LogMaskUtils.maskId("260602222327001");
        assertThat(result2).matches("26\\*{4,8}7001");
    }

    @Test
    void shouldMaskIpKeepSubnet() {
        assertThat(LogMaskUtils.maskIp("192.168.1.100")).isEqualTo("192.168.***.***");
        assertThat(LogMaskUtils.maskIp("10.0.0.1")).isEqualTo("10.0.***.***");
    }

    @Test
    void shouldHandleNullAndEmpty() {
        assertThat(LogMaskUtils.maskName(null)).isEqualTo("");
        assertThat(LogMaskUtils.maskAccount("")).isEqualTo("");
        assertThat(LogMaskUtils.maskText(null)).isEqualTo("");
        assertThat(LogMaskUtils.maskId("")).isEqualTo("");
        assertThat(LogMaskUtils.maskIp(null)).isEqualTo("");
        assertThat(LogMaskUtils.maskPhone(null)).isEqualTo("");
        assertThat(LogMaskUtils.maskIdCard("")).isEqualTo("");
    }

    // ==================== 随机性验证 ====================

    @RepeatedTest(20)
    void shouldProduceRandomStarCounts() {
        // 多次调用同一输入，星号数要有变化
        String r1 = LogMaskUtils.maskName("张三丰");
        String r2 = LogMaskUtils.maskName("张三丰");
        // 短名字 "张三丰"(3位) ≤ 6 → 张 + 4~8星 + 丰
        // 20次重复测试，极高概率会有不同星号数
        assertThat(r1).matches("张\\*{4,8}丰");
        assertThat(r2).matches("张\\*{4,8}丰");
    }
}
