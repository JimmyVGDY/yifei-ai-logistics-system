package jimmy.ai.service;

import jimmy.ai.model.AiReadonlyQueryResult;
import jimmy.ai.model.AiToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 覆盖 Python AI 回调 Java internal 工具时的返回契约。
 * <p>
 * 重点确认前端可见字段保持中文、安全，分页游标不被脱敏破坏。
 */
class ToolExecutorServiceTest {

    @Test
    void shouldExposeChineseDisplayTargetForBusinessModuleQuery() {
        // query_business_module 返回值必须带 display* 和 dataGroups，前端不能显示内部工具名或模块码。
        AiReadonlyQueryService readonlyQueryService = mock(AiReadonlyQueryService.class);
        UserContextResolver userContextResolver = mock(UserContextResolver.class);
        ToolExecutorService service = new ToolExecutorService(
                readonlyQueryService,
                mock(AiLogAnalysisService.class),
                mock(AiGeneratedSqlQueryService.class),
                new AiSensitiveDataMasker(),
                mock(AiAuditLogService.class),
                userContextResolver,
                new AiToolCallContext(8)
        );

        when(readonlyQueryService.queryModule("tasks", "", "2026-01-01 00:00:00", "2026-07-01 23:59:59"))
                .thenReturn(new AiReadonlyQueryResult(
                        true,
                        "已查询运输任务。",
                        List.of(),
                        List.of(new AiToolCall("业务数据查询", "运输任务", "已查询运输任务。")),
                        List.of(Map.of("任务号", "TASK-001")),
                        List.of("任务号"),
                        "cursor-1",
                        20L,
                        10,
                        10L,
                        true,
                        "还有 10 条"
                ));

        Map<String, Object> result = service.execute(
                "user-1",
                List.of("task:query"),
                "U-1",
                "ADMIN",
                "",
                "login-1",
                "conv-1",
                "query_business_module",
                Map.of(
                        "module", "tasks",
                        "keyword", "",
                        "startTime", "2026-01-01 00:00:00",
                        "endTime", "2026-07-01 23:59:59"
                )
        );

        assertThat(result.get("displayToolName")).isEqualTo("业务数据查询");
        assertThat(result.get("displayTarget")).isEqualTo("运输任务");
        assertThat((List<?>) result.get("dataGroups")).hasSize(1);
        Map<?, ?> group = (Map<?, ?>) ((List<?>) result.get("dataGroups")).getFirst();
        assertThat(group.get("displayTarget")).isEqualTo("运输任务");
        Map<?, ?> citation = (Map<?, ?>) result.get("citation");
        assertThat(citation.get("module")).isEqualTo("运输任务");
    }

    @Test
    void shouldReturnRawCursorIdForFrontendContinuation() {
        // cursorId 是不透明分页令牌，必须原样返回；如果被 masker 替换，续页按钮会失效。
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
