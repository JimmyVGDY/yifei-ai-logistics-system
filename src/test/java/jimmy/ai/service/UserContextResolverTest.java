package jimmy.ai.service;

import jimmy.ai.util.SseChatContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextResolverTest {

    private final UserContextResolver resolver = new UserContextResolver();

    @AfterEach
    void clearContext() {
        SseChatContext.clear();
    }

    @Test
    void shouldReturnAnonymousWhenNoContextAvailable() {
        assertThat(resolver.currentUserId()).isEqualTo("anonymous");
        assertThat(resolver.currentUserCode()).isEmpty();
        assertThat(resolver.currentLoginSessionId()).isEmpty();
        assertThat(resolver.currentRoleCode()).isEmpty();
        assertThat(resolver.isSseThread()).isFalse();
    }

    @Test
    void shouldReturnSseContextValuesWhenSet() {
        SseChatContext.setLoginIdAndPermissions("U001", java.util.List.of("order:query"));
        SseChatContext.setUserCode("UC001");
        SseChatContext.setLoginSessionId("sess-123");
        SseChatContext.setRoleCode("ADMIN");

        assertThat(resolver.currentUserId()).isEqualTo("U001");
        assertThat(resolver.currentUserCode()).isEqualTo("UC001");
        assertThat(resolver.currentLoginSessionId()).isEqualTo("sess-123");
        assertThat(resolver.currentRoleCode()).isEqualTo("ADMIN");
        assertThat(resolver.isSseThread()).isTrue();
    }
}
