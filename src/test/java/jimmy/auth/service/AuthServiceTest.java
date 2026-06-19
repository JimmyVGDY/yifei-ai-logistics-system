package jimmy.auth.service;

import jimmy.auth.mapper.AuthMapper;
import jimmy.auth.service.LoginConflictService;
import jimmy.common.trace.TraceContextSupport;
import jimmy.common.util.FieldEncryptor;
import jimmy.system.service.SystemPermissionService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuthServiceTest {

    @Test
    void shouldConstructSuccessfully() {
        AuthMapper mapper = mock(AuthMapper.class);
        SystemPermissionService permService = mock(SystemPermissionService.class);
        LoginConflictService conflictService = mock(LoginConflictService.class);
        FieldEncryptor encryptor = mock(FieldEncryptor.class);
        LoginSecurityService securityService = mock(LoginSecurityService.class);
        TraceContextSupport traceSupport = mock(TraceContextSupport.class);
        MenuTreeBuilder menuBuilder = mock(MenuTreeBuilder.class);

        AuthService service = new AuthService(mapper, permService, conflictService, encryptor,
                securityService, traceSupport, menuBuilder);
        assertThat(service).isNotNull();
    }
}
