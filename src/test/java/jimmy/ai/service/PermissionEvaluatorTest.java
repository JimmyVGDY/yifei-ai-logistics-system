package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.util.SseChatContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class PermissionEvaluatorTest {

    private final PermissionEvaluator evaluator = new PermissionEvaluator();

    @AfterEach
    void clearContext() {
        SseChatContext.clear();
    }

    @Test
    void shouldUseSaTokenWhenNotInSseContext() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission("order:query")).thenReturn(true);
            stp.when(() -> StpUtil.hasPermission("fee:query")).thenReturn(false);

            assertThat(evaluator.hasPermission("order:query")).isTrue();
            assertThat(evaluator.hasPermission("fee:query")).isFalse();
            assertThat(evaluator.hasPermission(null)).isFalse();
            assertThat(evaluator.hasPermission("")).isFalse();
        }
    }

    @Test
    void shouldUseSseContextWhenLoginIdIsSet() {
        SseChatContext.setLoginIdAndPermissions("U001", List.of("order:query", "track:view"));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            assertThat(evaluator.hasPermission("order:query")).isTrue();
            assertThat(evaluator.hasPermission("track:view")).isTrue();
            assertThat(evaluator.hasPermission("fee:query")).isFalse();
            stp.verifyNoInteractions();
        }
    }
}
