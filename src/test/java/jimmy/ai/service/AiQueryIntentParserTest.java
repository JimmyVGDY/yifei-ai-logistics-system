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

    @Test
    void shouldCleanupChineseQuestionSuffixFromKeyword() {
        AiQueryIntent intent = parser.parse("我需要知道客户名称为陈土豆的信息");

        assertThat(intent.matched()).isTrue();
        assertThat(intent.module()).isEqualTo("customers");
        assertThat(intent.keyword()).isEqualTo("陈土豆");
    }

    @Test
    void shouldCleanupNamePrefixWhenCustomerKeywordStartsWithNameLabel() {
        AiQueryIntent intent = parser.parse("我需要知道客户 名称为唐若琳的相关信息");

        assertThat(intent.matched()).isTrue();
        assertThat(intent.module()).isEqualTo("customers");
        assertThat(intent.keyword()).isEqualTo("唐若琳");
    }

    @Test
    void shouldIgnoreAccidentalSpacesAndSymbolsAroundKeyword() {
        AiQueryIntent intent = parser.parse("  帮我查一下客户名称为  @唐若琳！！  的相关信息  ");

        assertThat(intent.matched()).isTrue();
        assertThat(intent.module()).isEqualTo("customers");
        assertThat(intent.keyword()).isEqualTo("唐若琳");
    }

    @Test
    void shouldMatchTraceIdIgnoringLetterCase() {
        AiQueryIntent intent = parser.parse("查询 traceid = abcDEF1234567890 的日志");

        assertThat(intent.matched()).isTrue();
        assertThat(intent.module()).isEqualTo("operationLogs");
        assertThat(intent.keyword()).isEqualTo("abcDEF1234567890");
    }

    @Test
    void shouldRejectExportIntent() {
        AiQueryIntent intent = parser.parse("帮我导出客户数据");

        assertThat(intent.matched()).isTrue();
        assertThat(intent.forbiddenWrite()).isTrue();
    }

    @Test
    void shouldParseVehiclePlateKeyword() {
        AiQueryIntent intent = parser.parse("查询车牌号为沪A12345的车辆");

        assertThat(intent.matched()).isTrue();
        assertThat(intent.module()).isEqualTo("vehicles");
        assertThat(intent.keyword()).isEqualTo("沪A12345");
    }
}
