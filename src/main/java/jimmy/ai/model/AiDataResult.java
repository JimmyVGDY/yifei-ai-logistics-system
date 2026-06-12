package jimmy.ai.model;

import java.util.List;
import java.util.Map;

/**
 * AI 只读工具返回的结构化数据集。
 * <p>
 * 聊天气泡只展示摘要，表格数据交给前端抽屉/分页表格承载，避免大结果把回答区域撑得过长。
 */
public record AiDataResult(
        String toolName,
        String target,
        String summary,
        List<String> columns,
        List<Map<String, Object>> rows) {

    public AiDataResult {
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
