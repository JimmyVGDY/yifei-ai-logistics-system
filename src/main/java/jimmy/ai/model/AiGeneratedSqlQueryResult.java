package jimmy.ai.model;

import java.util.List;
import java.util.Map;

public record AiGeneratedSqlQueryResult(
        boolean executed,
        String message,
        List<Map<String, Object>> records,
        List<String> columns,
        String displayToolName,
        String displayTarget) {

    public AiGeneratedSqlQueryResult {
        records = records == null ? List.of() : records;
        columns = columns == null ? List.of() : columns;
        displayToolName = displayToolName == null || displayToolName.isBlank() ? "统计分析" : displayToolName;
        displayTarget = displayTarget == null || displayTarget.isBlank() ? "统计结果" : displayTarget;
    }

    public static AiGeneratedSqlQueryResult skipped() {
        return new AiGeneratedSqlQueryResult(false,
                "该问题属于普通业务明细查询，请使用标准业务模块查询。",
                List.of(), List.of(), "统计分析", "统计结果");
    }

    public static AiGeneratedSqlQueryResult message(String message) {
        return new AiGeneratedSqlQueryResult(true, message, List.of(), List.of(), "统计分析", "统计结果");
    }
}
