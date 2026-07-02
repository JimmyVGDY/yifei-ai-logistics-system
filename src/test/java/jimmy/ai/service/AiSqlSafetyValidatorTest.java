package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.util.SseChatContext;
import jimmy.system.config.StandardColumnRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 锁定 AI 临时 SQL 的安全闸门。
 * <p>
 * 这些用例防止模型生成写 SQL、越权表字段、敏感列或绕过列权限的查询。
 */
class AiSqlSafetyValidatorTest {

    private final StandardColumnRegistry columnRegistry = new StandardColumnRegistry();
    private final AiReadableSchemaRegistry schemaRegistry = new AiReadableSchemaRegistry(columnRegistry);
    private final AiSqlSafetyValidator validator = new AiSqlSafetyValidator(schemaRegistry, columnRegistry, null, new jimmy.ai.service.PermissionEvaluator());

    @Test
    void shouldAllowSelectJoinWhenUserHasAllTablePermissions() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("customer:query")).thenReturn(true);
            stp.when(() -> StpUtil.hasPermission("order:query")).thenReturn(true);

            AiSqlSafetyValidator.ValidatedSql sql = validator.validate("""
                    select c.customer_name, count(o.id) as order_count
                    from logistics_customer c
                    left join logistics_order o on o.customer_id = c.id
                    group by c.customer_name
                    """);

            assertThat(sql.tables()).containsExactly("logistics_customer", "logistics_order");
        }
    }

    @Test
    void shouldRejectWriteSql() {
        assertThatThrownBy(() -> validator.validate("delete from logistics_order where id = 1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SELECT");
    }

    @Test
    void shouldRejectUnknownTable() {
        assertThatThrownBy(() -> validator.validate("select user from mysql.user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未开放");
    }

    @Test
    void shouldRejectSelectAllAndSensitiveColumns() {
        assertThatThrownBy(() -> validator.validate("select * from logistics_order"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("敏感");

        assertThatThrownBy(() -> validator.validate("select username, password from sys_user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("敏感");

        ColumnPermissionResolver resolver = mock(ColumnPermissionResolver.class);
        when(resolver.allowedColumns("system:log")).thenReturn(Set.of());
        AiSqlSafetyValidator validatorWithColumnPermission =
                new AiSqlSafetyValidator(schemaRegistry, columnRegistry, resolver, new jimmy.ai.service.PermissionEvaluator());
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("system:log:query")).thenReturn(true);
            assertThatThrownBy(() -> validatorWithColumnPermission.validate("select request_params from sys_operation_log"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("敏感");
        }
    }

    @Test
    void shouldRejectWhenUserLacksTablePermission() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("fee:query")).thenReturn(false);

            assertThatThrownBy(() -> validator.validate("select order_no from logistics_fee"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("权限不足")
                    .hasMessageNotContaining("fee:query");
        }
    }

    @Test
    void shouldUseSsePermissionSnapshotWhenRunningInAsyncThread() {
        SseChatContext.setLoginIdAndPermissions("260610133716001", List.of("customer:query", "order:query"));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            AiSqlSafetyValidator.ValidatedSql sql = validator.validate("""
                    select c.customer_name, o.order_no
                    from logistics_customer c
                    join logistics_order o on o.customer_id = c.id
                    """);

            assertThat(sql.tables()).containsExactly("logistics_customer", "logistics_order");
            stp.verifyNoInteractions();
        } finally {
            SseChatContext.clear();
        }
    }

    @Test
    void shouldExposeExpandedRealDatabaseSchema() {
        String schema = validator.schemaPrompt();

        assertThat(schema)
                .contains("logistics_warehouse")
                .contains("logistics_route")
                .contains("logistics_inventory")
                .contains("logistics_freight_bill")
                .contains("sys_permission")
                .contains("load_capacity_kg")
                .contains("payable_fee");
        assertThat(schema)
                .contains("logistics_waybill(id, waybill_no, order_id")
                .contains("logistics_fee(id, order_id, base_fee")
                .contains("logistics_exception(id, order_id, task_id");
        assertThat(schema)
                .doesNotContain("- ai_conversation(")
                .doesNotContain("transport_fee")
                .doesNotContain("total_fee")
                .doesNotContain("logistics_waybill(id, waybill_no, order_id, order_no")
                .doesNotContain("logistics_fee(id, order_id, order_no")
                .doesNotContain("logistics_exception(id, order_id, order_no");
    }

    @Test
    void shouldExposeBusinessColumnsWhenRegistryUsesDefaultConstructor() {
        String schema = new AiReadableSchemaRegistry().schemaPrompt();

        assertThat(schema)
                .contains("logistics_order")
                .contains("order_no")
                .contains("customer_name")
                .contains("created_at");
    }

    @Test
    void shouldAllowResourceTablesAndRejectAiInternalTables() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("resource:query")).thenReturn(true);
            stp.when(() -> StpUtil.hasPermission("ai:log:analyze")).thenReturn(true);
            stp.when(() -> StpUtil.hasPermission("ai:conversation:query")).thenReturn(true);

            assertThat(validator.validate("select warehouse_name from logistics_warehouse").tables())
                    .containsExactly("logistics_warehouse");
            assertThatThrownBy(() -> validator.validate("select conversation_id, message_count from ai_conversation"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("内部数据");
        }
    }

    @Test
    void shouldRejectCommaJoinBecauseItCanHideUncheckedTables() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("order:query")).thenReturn(true);
            stp.when(() -> StpUtil.hasPermission("system:user:query")).thenReturn(false);

            assertThatThrownBy(() -> validator.validate("""
                    select o.order_no, u.username
                    from logistics_order o, sys_user u
                    where o.customer_id = u.customer_id
                    """))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("逗号连表");
        }
    }

    @Test
    void shouldAllowSensitiveBusinessColumnOnlyWhenColumnPermissionGranted() {
        ColumnPermissionResolver resolver = mock(ColumnPermissionResolver.class);
        when(resolver.allowedColumns("fee")).thenReturn(Set.of("payable_fee"));
        AiSqlSafetyValidator validatorWithColumnPermission =
                new AiSqlSafetyValidator(schemaRegistry, columnRegistry, resolver, new jimmy.ai.service.PermissionEvaluator());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("fee:query")).thenReturn(true);

            AiSqlSafetyValidator.ValidatedSql sql = validatorWithColumnPermission.validate("""
                    select sum(payable_fee) as payable_total
                    from logistics_fee
                    """);

            assertThat(sql.tables()).containsExactly("logistics_fee");
        }
    }

    @Test
    void shouldRejectSensitiveBusinessColumnWhenColumnPermissionMissing() {
        ColumnPermissionResolver resolver = mock(ColumnPermissionResolver.class);
        when(resolver.allowedColumns("fee")).thenReturn(Set.of());
        AiSqlSafetyValidator validatorWithColumnPermission =
                new AiSqlSafetyValidator(schemaRegistry, columnRegistry, resolver, new jimmy.ai.service.PermissionEvaluator());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("fee:query")).thenReturn(true);

            assertThatThrownBy(() -> validatorWithColumnPermission.validate("""
                    select sum(payable_fee) as payable_total
                    from logistics_fee
                    """))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("敏感列");
        }
    }

    @Test
    void shouldTrackColumnAliasBackToSourceColumn() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("order:query")).thenReturn(true);

            AiSqlSafetyValidator.ValidatedSql sql = validator.validate("""
                    select o.order_no as 订单号, count(*) as order_count
                    from logistics_order o
                    group by o.order_no
                    """);

            assertThat(sql.resultColumnSources()).containsEntry("订单号", "order_no");
            assertThat(sql.resultColumnSources()).doesNotContainKey("order_count");
        }
    }
}
