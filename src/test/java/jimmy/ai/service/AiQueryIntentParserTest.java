package jimmy.ai.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiQueryIntentParserTest {

    private final AiQueryIntentParser parser = new AiQueryIntentParser();

    @Test
    void shouldParseOrderQueryIntent() {
        AiQueryIntent intent = parser.parse("查询最近 10 条运单");

        assertThat(intent.matched()).isTrue();
        assertThat(intent.module()).isEqualTo("orders");
        assertThat(intent.moduleName()).isEqualTo("运单管理");
        assertThat(intent.forbiddenWrite()).isFalse();
    }

    @Test
    void shouldTreatUnpaidFeeAsReadonlyQuery() {
        AiQueryIntent intent = parser.parse("查未收款费用");

        assertThat(intent.matched()).isTrue();
        assertThat(intent.module()).isEqualTo("fees");
        assertThat(intent.forbiddenWrite()).isFalse();
    }

    @Test
    void shouldRejectWriteIntent() {
        AiQueryIntent intent = parser.parse("帮我删除这个订单");

        assertThat(intent.matched()).isTrue();
        assertThat(intent.forbiddenWrite()).isTrue();
    }

    @Test
    void shouldParseDashboardAndTodayRange() {
        AiQueryIntent intent = parser.parse("查询今天的运营看板统计");

        assertThat(intent.matched()).isTrue();
        assertThat(intent.dashboard()).isTrue();
        assertThat(intent.moduleName()).isEqualTo("运营看板");
        assertThat(intent.startTime()).isNotBlank();
        assertThat(intent.endTime()).isNotBlank();
    }
}
