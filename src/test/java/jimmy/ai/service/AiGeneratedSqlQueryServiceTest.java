package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.mapper.AiPromptTemplateMapper;
import jimmy.ai.model.AiGeneratedSqlQueryResult;
import jimmy.ai.model.PromptRenderResult;
import jimmy.system.config.StandardColumnRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiGeneratedSqlQueryServiceTest {

    @Test
    void shouldReturnOnlyDisplaySafeColumnsForStatisticalSql() {
        AiModelGateway modelGateway = mock(AiModelGateway.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiAuditLogService auditLogService = mock(AiAuditLogService.class);
        ColumnPermissionResolver columnPermissionResolver = mock(ColumnPermissionResolver.class);
        AiGeneratedSqlQueryService service = service(modelGateway, jdbcTemplate, auditLogService, columnPermissionResolver);
        String sql = """
                select o.order_no, o.customer_name, count(*) as order_count
                from logistics_order o
                group by o.order_no, o.customer_name
                """;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("order_no", "LO-001");
        row.put("customer_name", "hidden-customer");
        row.put("order_count", 3);

        when(modelGateway.configured()).thenReturn(true);
        when(modelGateway.chat(any(PromptRenderResult.class), any(PromptRenderResult.class), any(), any()))
                .thenReturn(Optional.of(sql));
        when(columnPermissionResolver.allowedColumns("order")).thenReturn(Set.of("order_no"));
        when(jdbcTemplate.queryForList(contains("explain"))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("limit 20"))).thenReturn(List.of(row));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdDefaultNull).thenReturn(null);
            stp.when(() -> StpUtil.hasPermission("order:query")).thenReturn(true);

            AiGeneratedSqlQueryResult result = service.query("sql count order quantity");

            assertThat(result.executed()).isTrue();
            assertThat(result.columns()).containsExactly("订单号", "订单数量");
            assertThat(result.records()).hasSize(1);
            assertThat(result.records().getFirst())
                    .containsEntry("订单号", "LO-001")
                    .containsEntry("订单数量", 3)
                    .doesNotContainKeys("order_no", "order_count", "customer_name", "客户名称");
            assertThat(result.message())
                    .contains("统计分析完成")
                    .doesNotContain("order_count", "order_no", "hidden-customer", "| ---", "select");
            verify(jdbcTemplate).queryForList(contains("explain"));
            verify(jdbcTemplate).queryForList(contains("limit 20"));
        }
    }

    @Test
    void shouldSkipPlainDetailQueryWithoutCallingModel() {
        AiModelGateway modelGateway = mock(AiModelGateway.class);
        AiGeneratedSqlQueryService service = service(
                modelGateway,
                mock(JdbcTemplate.class),
                mock(AiAuditLogService.class),
                mock(ColumnPermissionResolver.class)
        );

        AiGeneratedSqlQueryResult result = service.query("我要看全部的运输任务");

        assertThat(result.executed()).isFalse();
        assertThat(result.message()).contains("普通业务明细查询");
        verify(modelGateway, never()).configured();
    }

    @Test
    void shouldHideInternalAndUnknownRawAliases() {
        AiModelGateway modelGateway = mock(AiModelGateway.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiAuditLogService auditLogService = mock(AiAuditLogService.class);
        ColumnPermissionResolver columnPermissionResolver = mock(ColumnPermissionResolver.class);
        AiGeneratedSqlQueryService service = service(modelGateway, jdbcTemplate, auditLogService, columnPermissionResolver);
        String sql = """
                select t.id, t.driver_id, t.task_no, count(*) as unknown_metric
                from logistics_task t
                group by t.id, t.driver_id, t.task_no
                """;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 100L);
        row.put("driver_id", 260602222327046L);
        row.put("task_no", "TASK-001");
        row.put("unknown_metric", 9);

        when(modelGateway.configured()).thenReturn(true);
        when(modelGateway.chat(any(PromptRenderResult.class), any(PromptRenderResult.class), any(), any()))
                .thenReturn(Optional.of(sql));
        when(columnPermissionResolver.allowedColumns("task")).thenReturn(Set.of("id", "driver_id", "task_no"));
        when(jdbcTemplate.queryForList(contains("explain"))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("limit 20"))).thenReturn(List.of(row));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdDefaultNull).thenReturn(null);
            stp.when(() -> StpUtil.hasPermission("task:query")).thenReturn(true);

            AiGeneratedSqlQueryResult result = service.query("统计运输任务数量");

            assertThat(result.records().getFirst())
                    .containsEntry("任务号", "TASK-001")
                    .containsEntry("统计字段1", 9)
                    .doesNotContainKeys("id", "driver_id", "司机ID", "unknown_metric");
            assertThat(result.columns()).containsExactly("任务号", "统计字段1");
        }
    }

    private AiGeneratedSqlQueryService service(AiModelGateway modelGateway,
                                               JdbcTemplate jdbcTemplate,
                                               AiAuditLogService auditLogService,
                                               ColumnPermissionResolver columnPermissionResolver) {
        StandardColumnRegistry columnRegistry = new StandardColumnRegistry();
        AiReadableSchemaRegistry schemaRegistry = new AiReadableSchemaRegistry(columnRegistry);
        AiSqlSafetyValidator validator = new AiSqlSafetyValidator(
                schemaRegistry, columnRegistry, columnPermissionResolver, new PermissionEvaluator());
        AiPromptTemplateService promptTemplateService = new AiPromptTemplateService(
                mock(AiPromptTemplateMapper.class), new DefaultAiPromptTemplates(), false, true);
        AiSqlOutputValidator sqlOutputValidator = new AiSqlOutputValidator(new AiOutputValidator());
        return new AiGeneratedSqlQueryService(
                modelGateway,
                validator,
                jdbcTemplate,
                new AiSensitiveDataMasker(),
                auditLogService,
                columnPermissionResolver,
                promptTemplateService,
                sqlOutputValidator);
    }
}
