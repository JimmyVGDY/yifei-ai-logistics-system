package jimmy.ai.model;

import java.util.List;
import java.util.Map;

/**
 * AI 临时只读 SQL 网关的安全返回对象。
 * <p>
 * 这里承载的是已经通过 SQL 安全校验、列权限过滤和展示净化后的结果；
 * 普通业务明细查询会用 {@link #skipped()} 明确让上层回到标准业务模块查询链路。
 */
public record AiGeneratedSqlQueryResult(
        /** 是否真正执行了临时 SQL；false 表示应由标准业务工具继续处理。 */
        boolean executed,
        /** 面向用户和模型上下文的安全中文摘要，不包含 SQL 原文。 */
        String message,
        /** 已过滤后的统计结果行；普通明细查询不能通过该字段返回原始数据库明细。 */
        List<Map<String, Object>> records,
        /** 已中文化或安全化的展示列名。 */
        List<String> columns,
        /** 前端展示用工具名，禁止直接显示内部工具码。 */
        String displayToolName,
        /** 前端展示用目标名称，通常是“统计结果”。 */
        String displayTarget) {

    public AiGeneratedSqlQueryResult {
        // 统一把可空集合折叠为空集合，避免 SSE/JSON 包装层出现 null 分支。
        records = records == null ? List.of() : records;
        columns = columns == null ? List.of() : columns;
        // 展示字段必须有中文兜底，前端不应该看到 generated_sql 或 execute_readonly_sql。
        displayToolName = displayToolName == null || displayToolName.isBlank() ? "统计分析" : displayToolName;
        displayTarget = displayTarget == null || displayTarget.isBlank() ? "统计结果" : displayTarget;
    }

    /**
     * 明确拒绝临时 SQL 的普通明细查询，让 Agent 改走 query_business_module。
     */
    public static AiGeneratedSqlQueryResult skipped() {
        return new AiGeneratedSqlQueryResult(false,
                "该问题属于普通业务明细查询，请使用标准业务模块查询。",
                List.of(), List.of(), "统计分析", "统计结果");
    }

    /**
     * 只返回一段安全提示文本的便捷结果，适用于校验失败或无需表格的统计说明。
     */
    public static AiGeneratedSqlQueryResult message(String message) {
        return new AiGeneratedSqlQueryResult(true, message, List.of(), List.of(), "统计分析", "统计结果");
    }
}
