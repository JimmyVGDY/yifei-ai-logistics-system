package jimmy.ai.model;

import java.util.List;
import java.util.Map;

/**
 * 用户界面可安全展示的一组结构化业务结果。
 * <p>
 * 全局搜索和联合查询会返回多组结果，前端按组渲染独立卡片，避免不同模块的列和摘要互相混入。
 */
public record AiDataResultGroup(
        String groupId,
        String displayToolName,
        String displayTarget,
        String displaySummary,
        List<String> columns,
        List<Map<String, Object>> rows,
        String cursorId,
        Long total,
        Integer returnedCount,
        Long remainingCount,
        Boolean hasMore,
        String nextPageHint) {

    public AiDataResultGroup {
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
