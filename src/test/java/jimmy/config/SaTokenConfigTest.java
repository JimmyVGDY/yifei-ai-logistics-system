package jimmy.auth.config;

import jimmy.logistics.config.OperationLogInterceptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SaTokenConfigTest {

    private final SaTokenConfig config = new SaTokenConfig(mock(OperationLogInterceptor.class), "http://127.0.0.1:5173");

    @Test
    void shouldResolveCrudModulePermissionsByMethodAndPath() {
        assertThat(config.resolveDynamicPermission("/logistics/modules/orders", "GET")).isEqualTo("order:query");
        assertThat(config.resolveDynamicPermission("/logistics/modules/orders", "POST")).isEqualTo("order:create");
        assertThat(config.resolveDynamicPermission("/logistics/modules/orders/1", "POST")).isEqualTo("order:update");
        assertThat(config.resolveDynamicPermission("/logistics/modules/orders/1/delete", "POST")).isEqualTo("order:delete");
    }

    @Test
    void shouldResolveSpecialLogisticsPermissions() {
        assertThat(config.resolveDynamicPermission("/logistics/excel/export/customers", "GET")).isEqualTo("customer:export");
        assertThat(config.resolveDynamicPermission("/logistics/excel/import/customers", "POST")).isEqualTo("customer:import");
        assertThat(config.resolveDynamicPermission("/logistics/customer-accounts", "POST")).isEqualTo("system:user:create");
        assertThat(config.resolveDynamicPermission("/logistics/files/upload", "POST")).isEqualTo("file:create");
    }

    @Test
    void shouldResolveBusinessEndpointPermissions() {
        assertThat(config.resolveDynamicPermission("/logistics/orders/LO-001", "GET")).isEqualTo("order:query");
        assertThat(config.resolveDynamicPermission("/logistics/orders", "POST")).isEqualTo("order:create");
        assertThat(config.resolveDynamicPermission("/logistics/exceptions/100/handle", "POST")).isEqualTo("exception:update");
        assertThat(config.resolveDynamicPermission("/logistics/exceptions", "POST")).isEqualTo("exception:create");
        assertThat(config.resolveDynamicPermission("/logistics/fees/generate/LO-001", "POST")).isEqualTo("fee:create");
        assertThat(config.resolveDynamicPermission("/logistics/fees/100/pay", "POST")).isEqualTo("fee:update");
    }

    @Test
    void resolvesPracticeAndMiddlewareEndpointPermissions() {
        SaTokenConfig config = new SaTokenConfig(null, "http://127.0.0.1:5173");

        assertThat(config.resolveDynamicPermission("/demo-users", "GET")).isEqualTo("system:user:query");
        assertThat(config.resolveDynamicPermission("/demo-users", "POST")).isEqualTo("system:user:create");
        assertThat(config.resolveDynamicPermission("/bloom-filter/items", "POST")).isEqualTo("resource:view");
        assertThat(config.resolveDynamicPermission("/rabbitmq/messages", "POST")).isEqualTo("resource:view");
    }

    @Test
    void shouldResolveAiAssistantEndpointPermissions() {
        assertThat(config.resolveDynamicPermission("/ai/chat", "POST")).isEqualTo("ai:chat");
        assertThat(config.resolveDynamicPermission("/ai/logs/analyze", "POST")).isEqualTo("ai:log:analyze");
        assertThat(config.resolveDynamicPermission("/ai/conversations", "GET")).isEqualTo("ai:conversation:query");
        assertThat(config.resolveDynamicPermission("/ai/conversations/100", "GET")).isEqualTo("ai:conversation:query");
        assertThat(config.resolveDynamicPermission("/ai/conversations/100/archive", "PUT")).isEqualTo("ai:conversation:archive");
        assertThat(config.resolveDynamicPermission("/ai/conversations/100/restore", "PUT")).isEqualTo("ai:conversation:archive");
        assertThat(config.resolveDynamicPermission("/ai/conversations/100", "DELETE")).isEqualTo("ai:conversation:delete");
    }
}
