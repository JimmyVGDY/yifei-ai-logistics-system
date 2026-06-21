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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiGeneratedSqlQueryServiceTest {

    @Test
    void shouldFilterBaseColumnsByPermissionAndKeepSafeDerivedColumns() {
        AiModelGateway modelGateway = mock(AiModelGateway.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AiAuditLogService auditLogService = mock(AiAuditLogService.class);
        ColumnPermissionResolver columnPermissionResolver = mock(ColumnPermissionResolver.class);
        StandardColumnRegistry columnRegistry = new StandardColumnRegistry();
        AiReadableSchemaRegistry schemaRegistry = new AiReadableSchemaRegistry(columnRegistry);
        AiSqlSafetyValidator validator = new AiSqlSafetyValidator(
                schemaRegistry, columnRegistry, columnPermissionResolver, new PermissionEvaluator());
        AiPromptTemplateService promptTemplateService = new AiPromptTemplateService(
                mock(AiPromptTemplateMapper.class), new DefaultAiPromptTemplates(), false, true);
        AiSqlOutputValidator sqlOutputValidator = new AiSqlOutputValidator(new AiOutputValidator());
        AiGeneratedSqlQueryService service = new AiGeneratedSqlQueryService(
                modelGateway,
                validator,
                jdbcTemplate,
                new AiSensitiveDataMasker(),
                auditLogService,
                columnPermissionResolver,
                promptTemplateService,
                sqlOutputValidator);
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
            assertThat(result.records()).hasSize(1);
            assertThat(result.records().getFirst())
                    .containsEntry("order_no", "LO-001")
                    .containsEntry("order_count", 3)
                    .doesNotContainKey("customer_name");
            assertThat(result.message()).contains("order_count").doesNotContain("hidden-customer");
            verify(jdbcTemplate).queryForList(contains("explain"));
            verify(jdbcTemplate).queryForList(contains("limit 20"));
        }
    }
}
