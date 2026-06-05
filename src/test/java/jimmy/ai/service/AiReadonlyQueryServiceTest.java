package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.logistics.model.ModuleQueryDTO;
import jimmy.logistics.model.ModuleRecordVO;
import jimmy.logistics.service.LogisticsRequirementService;
import jimmy.model.PageResult;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

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

    @Test
    void shouldInheritPreviousModuleWhenCurrentQuestionOnlyContainsFilter() {
        AiQueryIntentParser parser = new AiQueryIntentParser();
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
        when(requirementService.modulePage(anyString(), any(ModuleQueryDTO.class)))
                .thenReturn(new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of("异常状态", "待处理"))), 1, 10, 1));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("exception:query")).thenReturn(true);

            AiReadonlyQueryResult result = service.query("只要待处理的", "查一下异常管理");

            assertThat(result.executed()).isTrue();
            assertThat(result.toolCalls().getFirst().target()).isEqualTo("异常管理");
            assertThat(result.toolCalls().getFirst().result()).contains("待处理");
            verify(requirementService).modulePage(org.mockito.ArgumentMatchers.eq("exceptions"), any(ModuleQueryDTO.class));
        }
    }

    @Test
    void shouldFallbackToGlobalSearchWhenCustomerKeywordHasNoResult() {
        AiQueryIntentParser parser = new AiQueryIntentParser();
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
        when(requirementService.modulePage(anyString(), any(ModuleQueryDTO.class))).thenAnswer(invocation -> {
            String module = invocation.getArgument(0);
            if ("orders".equals(module)) {
                return new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of(
                        "order_no", "LO-TEST-001",
                        "customer_name", "陈菲"
                ))), 1, 5, 1);
            }
            return new PageResult<>(List.of(), 1, 5, 0);
        });

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);

            AiReadonlyQueryResult result = service.query("陈菲");

            assertThat(result.executed()).isTrue();
            assertThat(result.answerContext()).contains("全局只读查找", "运单管理", "陈菲");
            assertThat(result.toolCalls()).anySatisfy(toolCall -> {
                assertThat(toolCall.toolName()).isEqualTo("全局只读查找");
                assertThat(toolCall.target()).isEqualTo("运单管理");
            });
            verify(requirementService).modulePage(eq("customers"), any(ModuleQueryDTO.class));
            verify(requirementService).modulePage(eq("orders"), any(ModuleQueryDTO.class));
        }
    }

    @Test
    void shouldReusePreviousKeywordWhenUserRequestsGlobalSearch() {
        AiQueryIntentParser parser = new AiQueryIntentParser();
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
        when(requirementService.modulePage(anyString(), any(ModuleQueryDTO.class))).thenAnswer(invocation -> {
            String module = invocation.getArgument(0);
            if ("orders".equals(module)) {
                return new PageResult<>(List.of(new ModuleRecordVO(java.util.Map.of(
                        "order_no", "LO-TEST-002",
                        "customer_name", "陈菲"
                ))), 1, 5, 1);
            }
            return new PageResult<>(List.of(), 1, 5, 0);
        });

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);

            AiReadonlyQueryResult result = service.query("全局查找", "陈菲");

            assertThat(result.executed()).isTrue();
            assertThat(result.answerContext()).contains("陈菲", "运单管理");
            verify(requirementService).modulePage(eq("orders"), any(ModuleQueryDTO.class));
        }
    }
}
