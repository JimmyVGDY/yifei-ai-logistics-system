package jimmy.service;

import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.mapper.SystemPermissionMapper;
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
    private final SystemPermissionService service = new SystemPermissionService(mapper, mock(CompactSnowflakeIdGenerator.class));

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

    private Map<String, Object> override(String permissionCode, String grantType) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("permissionCode", permissionCode);
        row.put("grantType", grantType);
        return row;
    }
}
