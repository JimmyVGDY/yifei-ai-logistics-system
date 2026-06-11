package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.util.SseChatContext;
import jimmy.ai.model.AiGeneratedSqlQueryResult;
import jimmy.common.util.LogMaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * AI 生成 SQL 查询服务：模型只生成候选 SELECT，后端校验后执行只读查询。
 */
@Slf4j
@Service
public class AiGeneratedSqlQueryService {

    private static final int MAX_ROWS = 20;
    private static final int SUMMARY_ROWS = 8;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CUSTOMER_SCOPE_MESSAGE = "当前账号存在客户数据范围限制，AI 暂不支持自定义关联 SQL 查询。请使用客户、订单或轨迹等普通只读查询。";

    private final AiModelGateway modelGateway;
    private final AiSqlSafetyValidator sqlSafetyValidator;
    private final JdbcTemplate jdbcTemplate;
    private final AiSensitiveDataMasker masker;
    private final AiAuditLogService auditLogService;

    public AiGeneratedSqlQueryService(AiModelGateway modelGateway,
                                      AiSqlSafetyValidator sqlSafetyValidator,
                                      JdbcTemplate jdbcTemplate,
                                      AiSensitiveDataMasker masker,
                                      AiAuditLogService auditLogService) {
        this.modelGateway = modelGateway;
        this.sqlSafetyValidator = sqlSafetyValidator;
        this.jdbcTemplate = jdbcTemplate;
        this.masker = masker;
        this.auditLogService = auditLogService;
    }

    public AiGeneratedSqlQueryResult query(String message) {
        if (!shouldUseGeneratedSql(message)) {
            return AiGeneratedSqlQueryResult.skipped();
        }
        // 客户账号存在天然数据范围限制，临时关联 SQL 很难安全补齐客户隔离条件，先统一走普通只读查询。
        if (isCustomerRole()) {
            return AiGeneratedSqlQueryResult.message(CUSTOMER_SCOPE_MESSAGE);
        }
        if (!modelGateway.configured()) {
            return AiGeneratedSqlQueryResult.message("当前未配置模型，无法生成临时只读 SQL。请使用普通业务查询。");
        }
        try {
            Optional<String> candidate = modelGateway.chat(systemPrompt(), userPrompt(message));
            if (candidate.isEmpty()) {
                recordSqlStage("生成", message, "SQL_GENERATE_EMPTY：模型未生成 SQL", false);
                return AiGeneratedSqlQueryResult.message("模型暂时无法生成只读查询语句，请稍后重试。");
            }
            recordSqlStage("生成", message, "候选 SQL 已生成", true);

            Optional<String> checked = modelGateway.chat(selfCheckSystemPrompt(), selfCheckUserPrompt(message, candidate.get()));
            if (checked.isEmpty() || !StringUtils.hasText(checked.get())) {
                recordSqlStage("自检", message, "SQL_SELF_CHECK_FAILED：模型自检未返回可用 SQL", false);
                return AiGeneratedSqlQueryResult.message("临时只读查询自检失败，请换一种描述后重试。");
            }
            recordSqlStage("自检", message, "模型自检完成", true);

            // 模型输出只当作候选文本，自检后仍必须经过表白名单、权限、敏感字段和单语句校验后才允许执行。
            AiSqlSafetyValidator.ValidatedSql validatedSql = sqlSafetyValidator.validate(checked.get());
            recordSqlStage("安全校验", message, "校验通过，tables=" + validatedSql.tables(), true);
            preflight(validatedSql.sql());
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(wrapLimit(validatedSql.sql()));
            List<Map<String, Object>> formatted = formatRows(rows);
            String summary = summary(formatted);
            log.info("AI 生成 SQL 查询完成，tables={}, rows={}, sql={}",
                    validatedSql.tables(), formatted.size(), LogMaskUtils.maskText(validatedSql.sql()));
            recordSqlStage("执行", message, "执行成功，rows=" + formatted.size(), true);
            return new AiGeneratedSqlQueryResult(true, summary, formatted);
        } catch (IllegalArgumentException exception) {
            log.info("AI 生成 SQL 查询被安全校验拦截，errorCode=SQL_SECURITY_BLOCKED, reason={}", exception.getMessage());
            recordSqlStage("安全校验", message, "SQL_SECURITY_BLOCKED：" + exception.getMessage(), false);
            return AiGeneratedSqlQueryResult.message(exception.getMessage());
        } catch (BadSqlGrammarException exception) {
            log.warn("AI 生成 SQL 语法预检或执行失败，errorCode=SQL_SYNTAX_ERROR, exceptionType={}",
                    exception.getClass().getSimpleName());
            recordSqlStage("执行", message, "SQL_SYNTAX_ERROR：临时查询语法错误", false);
            return AiGeneratedSqlQueryResult.message("临时只读查询存在语法问题，请换一种描述后重试。");
        } catch (RuntimeException exception) {
            // 执行异常可能包含完整 SQL 或数据库细节，日志中只保留异常类型，详细 SQL 不落控制台。
            log.warn("AI 生成 SQL 查询失败，errorCode=SQL_EXECUTION_ERROR, exceptionType={}", exception.getClass().getSimpleName());
            recordSqlStage("执行", message, "SQL_EXECUTION_ERROR：临时查询执行失败", false);
            return AiGeneratedSqlQueryResult.message("临时只读查询执行失败，请换一种描述或联系系统管理员。");
        }
    }

    private boolean shouldUseGeneratedSql(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        // 普通“查某客户/查某订单”仍走模块查询；这里仅识别需要统计、关联或连表分析的表达。
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("sql")
                || lower.contains("连表")
                || lower.contains("关联")
                || lower.contains("统计")
                || lower.contains("数量")
                || lower.contains("总数")
                || lower.contains("排名")
                || lower.contains("最多")
                || lower.contains("最少")
                || lower.contains("平均")
                || lower.contains("group")
                || lower.contains("join");
    }

    private boolean isCustomerRole() {
        try {
            // 优先从 SSE 异步线程读取 Controller 传递的登录标识
            String sseLoginId = SseChatContext.getLoginId();
            if (sseLoginId != null && !sseLoginId.isBlank() && !"null".equals(sseLoginId)) {
                return "CUSTOMER".equals(SseChatContext.getRoleCode());
            }
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId == null) {
                return false;
            }
            Object roleCode = StpUtil.getSessionByLoginId(loginId).get("roleCode");
            return "CUSTOMER".equals(String.valueOf(roleCode));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String systemPrompt() {
        return """
                你是物流管理系统的只读 SQL 生成器。
                只返回一条 MySQL 兼容 SELECT 查询，不要解释，不要 Markdown，不要分号。
                可以使用 JOIN、GROUP BY、ORDER BY、聚合函数和 WHERE 条件。
                SELECT 输出列尽量使用中文别名，例如 customer_name as 客户名称。
                禁止 INSERT、UPDATE、DELETE、DROP、ALTER、CREATE、TRUNCATE、REPLACE、CALL、EXECUTE。
                查询必须使用下面的白名单表和字段：
                """ + sqlSafetyValidator.schemaPrompt();
    }

    private String userPrompt(String message) {
        return "请根据用户问题生成只读 SELECT 查询，最多返回必要字段。用户问题：" + message;
    }

    private String selfCheckSystemPrompt() {
        return """
                你是物流管理系统的 MySQL SELECT 自检器。
                你会收到用户问题和一条候选 SQL。请检查表名、字段名、JOIN 条件、聚合、别名和 MySQL 语法。
                只允许返回一条修正后的 MySQL SELECT 查询，不要解释，不要 Markdown，不要分号。
                如果候选 SQL 已经正确，也原样返回规范化后的 SELECT。
                禁止 INSERT、UPDATE、DELETE、DROP、ALTER、CREATE、TRUNCATE、REPLACE、CALL、EXECUTE。
                查询必须使用下面的白名单表和字段：
                """ + sqlSafetyValidator.schemaPrompt();
    }

    private String selfCheckUserPrompt(String message, String candidateSql) {
        return "用户问题：" + message + "\n候选 SQL：\n" + candidateSql + "\n请自检并返回最终可执行的单条 SELECT。";
    }

    private String wrapLimit(String sql) {
        // 外层统一加 limit，避免模型遗漏限制条件导致一次性返回过多数据。
        return "select * from (" + sql + ") ai_readonly_result limit " + MAX_ROWS;
    }

    private void preflight(String sql) {
        // 先用 limit 0 做语法预检，避免真实查询阶段才暴露 SQL 语法问题。
        jdbcTemplate.queryForList("select * from (" + sql + ") ai_readonly_check limit 0");
    }

    private void recordSqlStage(String stage, String promptSummary, String resultSummary, boolean success) {
        auditLogService.recordSqlStage("-", stage, promptSummary, resultSummary, success);
    }

    private List<Map<String, Object>> formatRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> formatted = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                formatted.put(entry.getKey(), formatValue(entry.getValue()));
            }
            result.add(formatted);
        }
        return result;
    }

    private Object formatValue(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.format(DATE_TIME_FORMATTER);
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().format(DATE_TIME_FORMATTER);
        }
        return value;
    }

    private String summary(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "临时只读 SQL 查询完成，未找到符合条件的数据。";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("临时只读 SQL 查询完成，返回 ").append(rows.size()).append(" 条记录。");
        builder.append("\n\n").append(markdownTable(rows.subList(0, Math.min(SUMMARY_ROWS, rows.size()))));
        return masker.mask(builder.toString());
    }

    private String markdownTable(List<Map<String, Object>> rows) {
        List<String> columns = new ArrayList<>(rows.getFirst().keySet());
        StringJoiner header = new StringJoiner(" | ", "| ", " |");
        StringJoiner separator = new StringJoiner(" | ", "| ", " |");
        for (String column : columns) {
            header.add(column);
            separator.add("---");
        }
        StringBuilder table = new StringBuilder(header.toString()).append("\n").append(separator).append("\n");
        for (Map<String, Object> row : rows) {
            StringJoiner values = new StringJoiner(" | ", "| ", " |");
            for (String column : columns) {
                values.add(masker.mask(String.valueOf(row.getOrDefault(column, ""))).replace("|", "\\|"));
            }
            table.append(values).append("\n");
        }
        return table.toString();
    }
}
