package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.util.SseChatContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

class AiSqlSafetyValidatorTest {

    private final AiSqlSafetyValidator validator = new AiSqlSafetyValidator();

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
                .hasMessageContaining("敏感字段");

        assertThatThrownBy(() -> validator.validate("select username, password from sys_user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("敏感字段");

        assertThatThrownBy(() -> validator.validate("select request_params from sys_operation_log"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("敏感字段");
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
                .contains("ai_conversation")
                .contains("load_capacity_kg")
                .contains("payable_fee");
        assertThat(schema)
                .doesNotContain("load_capacity,")
                .doesNotContain("transport_fee")
                .doesNotContain("total_fee")
                .doesNotContain("order_no, start_site");
    }

    @Test
    void shouldAllowResourceAndAiAuditTablesWithExpectedPermissions() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("resource:query")).thenReturn(true);
            stp.when(() -> StpUtil.hasPermission("ai:log:analyze")).thenReturn(true);

            assertThat(validator.validate("select warehouse_name from logistics_warehouse").tables())
                    .containsExactly("logistics_warehouse");
            assertThat(validator.validate("select conversation_id, message_count from ai_conversation").tables())
                    .containsExactly("ai_conversation");
        }
    }
}
