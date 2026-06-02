package jimmy.config;

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
}
