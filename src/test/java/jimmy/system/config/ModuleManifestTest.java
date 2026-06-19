package jimmy.system.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleManifestTest {

    @Test
    void shouldContainAllExpectedModules() {
        assertThat(ModuleManifest.moduleCodes()).contains(
                "order", "customer", "waybill", "dispatch", "task", "track",
                "driver", "vehicle", "exception", "fee",
                "system:user", "system:role", "system:log", "file"
        );
    }

    @Test
    void shouldMapFrontendRouteNamesToPermissionPrefixes() {
        var map = ModuleManifest.routeModuleToPermissionPrefix();
        assertThat(map.get("orders")).isEqualTo("order");
        assertThat(map.get("customers")).isEqualTo("customer");
        assertThat(map.get("users")).isEqualTo("system:user");
        assertThat(map.get("roles")).isEqualTo("system:role");
        assertThat(map.get("operationLogs")).isEqualTo("system:log");
        assertThat(map.get("files")).isEqualTo("file");
    }

    @Test
    void shouldHaveColumnsForEachModule() {
        for (String module : ModuleManifest.moduleCodes()) {
            ModuleManifest.ModuleEntry entry = ModuleManifest.get(module);
            assertThat(entry).as("module " + module).isNotNull();
            assertThat(entry.columns()).as("columns for " + module).isNotEmpty();
        }
    }

    @Test
    void shouldMarkSensitiveColumns() {
        ModuleManifest.ModuleEntry fee = ModuleManifest.get("fee");
        assertThat(fee.nonSensitiveColumns()).hasSizeLessThan(fee.columns().size());

        ModuleManifest.ModuleEntry driver = ModuleManifest.get("driver");
        assertThat(driver.columns().stream().filter(ModuleManifest.ColumnDef::sensitive).count()).isGreaterThan(0);
    }
}
