package jimmy.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jimmy.ai.service.ToolExecutorService;
import jimmy.ai.service.ToolRegistryService;
import jimmy.auth.mapper.AuthMapper;
import jimmy.system.service.SystemPermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiInternalControllerTest {

    @Test
    void shouldFailFastWhenPythonEnabledWithoutSharedSecret() {
        MockEnvironment env = new MockEnvironment();

        assertThatThrownBy(() -> new AiInternalController(
                mock(ToolRegistryService.class),
                mock(ToolExecutorService.class),
                mock(AuthMapper.class),
                mock(SystemPermissionService.class),
                new ObjectMapper(),
                env,
                true
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_INTERNAL_SHARED_SECRET");
    }

    @Test
    void shouldIgnoreForgedPermissionsAndResolveServerSidePermissions() {
        ToolRegistryService registryService = mock(ToolRegistryService.class);
        AuthMapper authMapper = mock(AuthMapper.class);
        SystemPermissionService permissionService = mock(SystemPermissionService.class);
        when(authMapper.findLoginUserById(1L)).thenReturn(Map.of(
                "id", 1L,
                "userCode", "U001",
                "status", 1,
                "roleId", 2L,
                "roleCode", "OPERATOR",
                "customerId", 10L
        ));
        when(permissionService.effectivePermissionCodes(1L, 2L)).thenReturn(List.of("order:query"));
        when(registryService.buildRegistry(eq("1"), eq(List.of("order:query")))).thenReturn(List.of());

        MockEnvironment env = new MockEnvironment()
                .withProperty("app.ai.internal.shared-secret", "local-secret");
        AiInternalController controller = new AiInternalController(
                registryService,
                mock(ToolExecutorService.class),
                authMapper,
                permissionService,
                new ObjectMapper(),
                env,
                false
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Internal-Secret", "local-secret");
        request.addHeader("X-Internal-User",
                "{\"userId\":\"1\",\"permissions\":[\"system:admin\"],\"roleCode\":\"ADMIN\",\"customerId\":\"999\"}");

        Map<String, Object> response = controller.toolsRegistry(request, new MockHttpServletResponse());

        assertThat(response.get("success")).isEqualTo(true);
        verify(registryService).buildRegistry("1", List.of("order:query"));
    }
}
