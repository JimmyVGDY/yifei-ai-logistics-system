package jimmy.ai.service;

import jimmy.ai.model.AiReadonlyQueryResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolExecutorServiceTest {

    @Test
    void shouldReturnRawCursorIdForFrontendContinuation() {
        AiReadonlyQueryService readonlyQueryService = mock(AiReadonlyQueryService.class);
        UserContextResolver userContextResolver = mock(UserContextResolver.class);
        AiSensitiveDataMasker masker = new AiSensitiveDataMasker();
        ToolExecutorService service = new ToolExecutorService(
                readonlyQueryService,
                mock(AiLogAnalysisService.class),
                mock(AiGeneratedSqlQueryService.class),
                masker,
                mock(AiAuditLogService.class),
                userContextResolver,
                new AiToolCallContext(8)
        );
        when(userContextResolver.currentUserId()).thenReturn("user-1");
        when(userContextResolver.currentUserCode()).thenReturn("U-1");

        when(readonlyQueryService.queryCursor("260701164400001", "conv-1", "user-1", "U-1"))
                .thenReturn(new AiReadonlyQueryResult(
                        true,
                        "ok",
                        List.of(),
                        List.of(),
                        List.of(Map.of("orderNo", "LO-001")),
                        List.of("orderNo"),
                        "260701164400999",
                        20L,
                        10,
                        10L,
                        true,
                        "10 rows remaining"
                ));

        Map<String, Object> result = service.execute(
                "user-1",
                List.of("order:query"),
                "U-1",
                "ADMIN",
                "",
                "login-1",
                "conv-1",
                "continue_cursor",
                Map.of("cursorId", "260701164400001", "offset", 10)
        );

        assertThat(result.get("cursorId")).isEqualTo("260701164400999");
        assertThat(String.valueOf(result.get("cursorId"))).doesNotContain("*");
    }
}
