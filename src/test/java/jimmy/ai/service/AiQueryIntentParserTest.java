package jimmy.ai.service;

import jimmy.ai.model.AiQueryIntent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 覆盖自然语言到业务模块查询意图的规则解析。
 * <p>
 * 重点锁定相对时间、模块别名、写操作拦截和多轮追问继承，避免模型前置规划失效时后端兜底变弱。
 */
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

    @Test
    void shouldNotTreatAllTasksAsKeyword() {
        AiQueryIntent intent = parser.parse("给我看看所有的运输任务");

        assertThat(intent.matched()).isTrue();
        assertThat(intent.module()).isEqualTo("tasks");
        assertThat(intent.keyword()).isNull();
    }

    @Test
    void shouldNotTreatAllCustomersAsKeyword() {
        AiQueryIntent intent = parser.parse("查询全部客户管理数据");

        assertThat(intent.matched()).isTrue();
        assertThat(intent.module()).isEqualTo("customers");
        assertThat(intent.keyword()).isNull();
    }

    @Test
    void shouldNotTreatModuleNameAsKeywordWhenQueryingExceptionModule() {
        AiQueryIntent intent = parser.parse("查看所有异常管理记录");

        assertThat(intent.matched()).isTrue();
        assertThat(intent.module()).isEqualTo("exceptions");
        assertThat(intent.keyword()).isNull();
    }

    @Test
    void shouldNotTreatAllOrdersAndWaybillsAsKeyword() {
        AiQueryIntent intent = parser.parse("给我查一下所有的运单和订单");

        assertThat(intent.matched()).isTrue();
        assertThat(intent.module()).isEqualTo("orders");
        assertThat(intent.keyword()).isNull();
    }

    @Test
    void shouldRouteOrderManagementToOrders() {
        AiQueryIntent intent = parser.parse("查询订单管理里的陈菲");

        assertThat(intent.matched()).isTrue();
        assertThat(intent.module()).isEqualTo("orders");
        assertThat(intent.keyword()).isEqualTo("陈菲");
    }

    @Test
    void shouldRouteWaybillCenterToWaybills() {
        AiQueryIntent intent = parser.parse("查询运单中心里的陈菲");

        assertThat(intent.matched()).isTrue();
        assertThat(intent.module()).isEqualTo("waybills");
        assertThat(intent.keyword()).isEqualTo("陈菲");
    }

    @Test
    void shouldTreatStandaloneNameAsCustomerSearch() {
        AiQueryIntent intent = parser.parse("陈土豆");

        assertThat(intent.matched()).isTrue();
        assertThat(intent.module()).isEqualTo("customers");
        assertThat(intent.keyword()).isEqualTo("陈土豆");
    }

    @Test
    void shouldNotInheritPreviousModuleForStandaloneName() {
        AiQueryIntent intent = parser.parse("陈土豆", "给我看看所有的运输任务");

        assertThat(intent.matched()).isTrue();
        assertThat(intent.module()).isEqualTo("customers");
        assertThat(intent.keyword()).isEqualTo("陈土豆");
    }

    @Test
    void shouldUsePreviousKeywordWhenUserClarifiesModule() {
        AiQueryIntent intent = parser.parse("是一个客户", "陈土豆");

        assertThat(intent.matched()).isTrue();
        assertThat(intent.module()).isEqualTo("customers");
        assertThat(intent.keyword()).isEqualTo("陈土豆");
    }

    @Test
    void shouldStillInheritPreviousModuleForStatusFollowUp() {
        AiQueryIntent intent = parser.parse("只要待处理的", "查一下异常管理");

        assertThat(intent.matched()).isTrue();
        assertThat(intent.module()).isEqualTo("exceptions");
        assertThat(intent.keyword()).isEqualTo("待处理");
    }

    @Test
    void shouldNotTreatGlobalSearchAsKeyword() {
        AiQueryIntent intent = parser.parse("全局查找");

        assertThat(intent.matched()).isFalse();
        assertThat(intent.keyword()).isNull();
    }
}
