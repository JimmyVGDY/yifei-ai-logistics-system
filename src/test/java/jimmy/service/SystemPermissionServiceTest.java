package jimmy.system.service;

import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.system.config.StandardColumnRegistry;
import jimmy.system.mapper.SystemPermissionMapper;
import jimmy.system.model.MenuVO;
import jimmy.system.model.PermissionTreeNodeVO;
import jimmy.system.model.PermissionVO;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemPermissionServiceTest {

    private final SystemPermissionMapper mapper = mock(SystemPermissionMapper.class);
    private final StandardColumnRegistry columnRegistry = new StandardColumnRegistry();
    private final SystemPermissionService service = new SystemPermissionService(mapper, mock(CompactSnowflakeIdGenerator.class), columnRegistry);

    @Test
    void shouldExpandRolePermissionsAndApplyUserGrantAndDeny() {
        when(mapper.selectRolePermissionCodes(1L)).thenReturn(Arrays.asList("order:manage", "track:view"));
        when(mapper.selectUserPermissionOverrides(10L)).thenReturn(Arrays.asList(
                override("order:delete", "DENY"),
                override("fee:query", "GRANT")
        ));

        List<String> permissions = service.effectivePermissionCodes(10L, 1L);

        assertThat(permissions).contains("order:query", "order:create", "order:update", "order:import", "order:export");
        assertThat(permissions).doesNotContain("order:delete");
        assertThat(permissions).contains("track:view", "track:query", "track:export", "fee:query");
    }

    @Test
    void shouldLetUserDenyOverrideRoleManagePermission() {
        when(mapper.selectRolePermissionCodes(1L)).thenReturn(Collections.singletonList("order:manage"));
        when(mapper.selectUserPermissionOverrides(10L)).thenReturn(Collections.singletonList(override("order:manage", "DENY")));

        List<String> permissions = service.effectivePermissionCodes(10L, 1L);

        assertThat(permissions).doesNotContain("order:manage", "order:query", "order:create", "order:update", "order:delete", "order:import", "order:export");
    }

    @Test
    void shouldKeepAdminRoleExpandedPermissionsWithoutOverrides() {
        when(mapper.selectRolePermissionCodes(1L)).thenReturn(Arrays.asList("dashboard:view", "system:permission:manage"));
        when(mapper.selectUserPermissionOverrides(1L)).thenReturn(Collections.emptyList());

        List<String> permissions = service.effectivePermissionCodes(1L, 1L);

        assertThat(permissions).contains(
                "dashboard:view", "dashboard:query", "dashboard:export",
                "system:permission:query", "system:permission:create", "system:permission:update",
                "system:permission:delete", "system:permission:import", "system:permission:export"
        );
    }

    @Test
    void shouldExpandAiChatPermissionToReadOnlyAssistantActions() {
        when(mapper.selectRolePermissionCodes(1L)).thenReturn(Collections.singletonList("ai:chat"));
        when(mapper.selectUserPermissionOverrides(10L)).thenReturn(Collections.emptyList());

        List<String> permissions = service.effectivePermissionCodes(10L, 1L);

        assertThat(permissions).contains("ai:chat", "ai:conversation:query", "ai:conversation:archive",
                "ai:conversation:delete", "ai:memory:query", "ai:memory:delete", "ai:memory:settings");
        assertThat(permissions).doesNotContain("ai:log:analyze");
        assertThat(permissions).doesNotContain("ai:create", "ai:update", "ai:delete");
    }

    @Test
    void shouldPutColumnPermissionsIntoStructuredColumns() {
        when(mapper.selectRolePermissionCodes(1L)).thenReturn(Collections.singletonList("order:manage"));
        when(mapper.selectUserPermissionOverrides(10L)).thenReturn(Collections.emptyList());

        Map<String, Map<String, Object>> permissions = service.structuredPermissions(10L, 1L);

        assertThat(permissions.get("order").get("actions")).asList()
                .contains("manage", "query", "create", "update", "delete", "import", "export");
        assertThat(permissions.get("order").get("columns")).asList()
                .contains("order_no", "customer_name", "sender_address", "receiver_address");
    }

    @Test
    void shouldHandleDirectColumnGrantInStructuredPermissions() {
        when(mapper.selectRolePermissionCodes(1L)).thenReturn(Collections.emptyList());
        when(mapper.selectUserPermissionOverrides(10L)).thenReturn(Collections.singletonList(override("system:log:column:operation_time", "GRANT")));

        Map<String, Map<String, Object>> permissions = service.structuredPermissions(10L, 1L);

        assertThat(permissions.get("system:log").get("columns")).asList()
                .containsExactly("operation_time");
    }

    @Test
    void shouldNotExpandSensitiveColumnsForViewPermission() {
        when(mapper.selectRolePermissionCodes(1L)).thenReturn(Collections.singletonList("system:log:view"));
        when(mapper.selectUserPermissionOverrides(10L)).thenReturn(Collections.emptyList());

        Map<String, Map<String, Object>> permissions = service.structuredPermissions(10L, 1L);

        assertThat(permissions.get("system:log").get("columns")).asList()
                .contains("operation_id", "trace_id", "operation_time")
                .doesNotContain("error_message", "client_ip", "request_params", "change_summary");
    }

    @Test
    void shouldExposeSensitiveFlagInPermissionTree() {
        MenuVO menu = menu(1L, "操作日志", "/system/operation-logs", "system:log:view");
        PermissionVO sensitiveColumn = permission(2L, "system:log:column:error_message",
                "操作日志-异常信息（列）", "COLUMN", 1L, true);
        when(mapper.selectAllActiveMenus()).thenReturn(Collections.singletonList(menu));
        when(mapper.selectAllActivePermissions()).thenReturn(Collections.singletonList(sensitiveColumn));

        List<PermissionTreeNodeVO> tree = service.permissionTree();

        PermissionTreeNodeVO columnNode = tree.get(0).getChildren().get(0).getChildren().get(0);
        assertThat(columnNode.getPermissionCode()).isEqualTo("system:log:column:error_message");
        assertThat(columnNode.getSensitiveFlag()).isTrue();
    }

    private Map<String, Object> override(String permissionCode, String grantType) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("permissionCode", permissionCode);
        row.put("grantType", grantType);
        return row;
    }

    private MenuVO menu(Long id, String name, String path, String permissionCode) {
        MenuVO menu = new MenuVO();
        menu.setId(id);
        menu.setParentId(0L);
        menu.setName(name);
        menu.setPath(path);
        menu.setPermissionCode(permissionCode);
        menu.setSortNo(1);
        return menu;
    }

    private PermissionVO permission(Long id, String code, String name, String type, Long menuId, boolean sensitive) {
        PermissionVO permission = new PermissionVO();
        permission.setId(id);
        permission.setPermissionCode(code);
        permission.setPermissionName(name);
        permission.setPermissionType(type);
        permission.setModuleCode("system:log");
        permission.setActionCode("column:error_message");
        permission.setMenuId(menuId);
        permission.setSensitiveFlag(sensitive);
        permission.setSortNo(1);
        return permission;
    }
}
