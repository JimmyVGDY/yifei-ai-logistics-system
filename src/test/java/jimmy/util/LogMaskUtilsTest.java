package jimmy.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskUtilsTest {

    @Test
    void shouldMaskAccountButKeepReadablePrefixAndSuffix() {
        assertThat(LogMaskUtils.maskAccount("admin")).isEqualTo("a***n");
        assertThat(LogMaskUtils.maskAccount("13890000000")).isEqualTo("1*********0");
    }

    @Test
    void shouldMaskChineseNameAndShortValue() {
        assertThat(LogMaskUtils.maskName("张三")).isEqualTo("张*");
        assertThat(LogMaskUtils.maskName("王")).isEqualTo("*");
    }

    @Test
    void shouldTrimAndMaskTextWithoutReplacingEverything() {
        assertThat(LogMaskUtils.maskText("  生气啦  ")).isEqualTo("生*啦");
        assertThat(LogMaskUtils.maskText("上海鲜生零售有限公司")).isEqualTo("上海******公司");
    }
}
