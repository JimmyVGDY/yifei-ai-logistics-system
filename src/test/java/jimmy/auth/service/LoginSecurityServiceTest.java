package jimmy.auth.service;

import jimmy.auth.mapper.LoginHistoryMapper;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginSecurityServiceTest {

    @Test
    void shouldLockAccountAndIpAfterFailureThreshold() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(any())).thenReturn(5L);

        LoginSecurityService service = new LoginSecurityService(
                mock(LoginHistoryMapper.class),
                redisTemplate,
                mock(CompactSnowflakeIdGenerator.class),
                5,
                15,
                10
        );

        service.recordLoginFailure("Admin", "127.0.0.1");

        verify(valueOps).set(eq("login:lock:account:admin"), eq("1"), eq(15L), eq(TimeUnit.MINUTES));
        verify(valueOps).set(eq("login:lock:ip:127.0.0.1"), eq("1"), eq(15L), eq(TimeUnit.MINUTES));
    }

    @Test
    void shouldRejectLockedLogin() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.hasKey("login:lock:account:admin")).thenReturn(true);

        LoginSecurityService service = new LoginSecurityService(
                mock(LoginHistoryMapper.class),
                redisTemplate,
                mock(CompactSnowflakeIdGenerator.class),
                5,
                15,
                10
        );

        assertThatThrownBy(() -> service.assertLoginAllowed("Admin", "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("失败次数过多");
    }
}
