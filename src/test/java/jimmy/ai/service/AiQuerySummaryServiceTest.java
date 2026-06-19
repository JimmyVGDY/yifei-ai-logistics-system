package jimmy.ai.service;

import jimmy.ai.model.AiQueryIntent;
import jimmy.common.model.PageResult;
import jimmy.logistics.model.ModuleRecordVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiQuerySummaryServiceTest {

    @Test
    void shouldOnlyReturnSummaryAndLeaveRowsToStructuredDataCard() {
        AiQuerySummaryService service = new AiQuerySummaryService();
        AiQueryIntent intent = new AiQueryIntent("waybills", "运单中心", "waybill:query",
                null, "2026-06-01 00:00:00", "2026-06-19 23:59:59", false, false, true);
        List<ModuleRecordVO> rows = List.of(
                new ModuleRecordVO(Map.of("waybill_no", "WB001", "current_location", "武汉")),
                new ModuleRecordVO(Map.of("waybill_no", "WB002", "current_location", "北京"))
        );

        String summary = service.moduleSummary(intent, new PageResult<>(rows, 1, 10, 491));

        assertThat(summary)
                .contains("共匹配 491 条记录", "本次已返回前 2 条结构化记录", "还有 489 条记录")
                .doesNotContain("Markdown 表格", "完整列出", "不要省略", "聊天气泡", "WB001", "WB002");
    }
}
