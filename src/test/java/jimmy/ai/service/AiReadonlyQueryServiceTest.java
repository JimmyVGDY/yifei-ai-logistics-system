package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.logistics.service.LogisticsRequirementService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

class AiReadonlyQueryServiceTest {

    @Test
    void shouldReturnFriendlyMessageAndSkipQueryWhenPermissionDenied() {
        AiQueryIntentParser parser = mock(AiQueryIntentParser.class);
        AiGeneratedSqlQueryService sqlQueryService = mock(AiGeneratedSqlQueryService.class);
        LogisticsRequirementService requirementService = mock(LogisticsRequirementService.class);
        AiReadonlyQueryService service = new AiReadonlyQueryService(
                parser,
                sqlQueryService,
                requirementService,
                new AiQuerySummaryService(),
                new AiSensitiveDataMasker()
        );
        when(sqlQueryService.query(anyString())).thenReturn(AiGeneratedSqlQueryResult.skipped());
        when(parser.parse("查未收款费用")).thenReturn(new AiQueryIntent(
                "fees", "费用结算", "fee:query", null, null, null, false, false, true
        ));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("fee:query")).thenReturn(false);

            AiReadonlyQueryResult result = service.query("查未收款费用");

            assertThat(result.executed()).isTrue();
            assertThat(result.answerContext()).isEqualTo("当前账号权限不足，无法查询该类数据。如有需要，请联系系统管理员。");
            assertThat(result.answerContext()).doesNotContain("fee:query", "fees");
            assertThat(result.toolCalls()).hasSize(1);
            assertThat(result.toolCalls().getFirst().result()).doesNotContain("fee:query", "fees");
            verifyNoInteractions(requirementService);
        }
    }

    @Test
    void shouldRejectWriteRequestWithoutQueryingDatabase() {
        AiQueryIntentParser parser = mock(AiQueryIntentParser.class);
        AiGeneratedSqlQueryService sqlQueryService = mock(AiGeneratedSqlQueryService.class);
        LogisticsRequirementService requirementService = mock(LogisticsRequirementService.class);
        AiReadonlyQueryService service = new AiReadonlyQueryService(
                parser,
                sqlQueryService,
                requirementService,
                new AiQuerySummaryService(),
                new AiSensitiveDataMasker()
        );
        when(sqlQueryService.query(anyString())).thenReturn(AiGeneratedSqlQueryResult.skipped());
        when(parser.parse("删除订单")).thenReturn(AiQueryIntent.forbiddenWriteIntent());

        AiReadonlyQueryResult result = service.query("删除订单");

        assertThat(result.executed()).isTrue();
        assertThat(result.answerContext()).contains("仅支持只读查询");
        verifyNoInteractions(requirementService);
    }
}
