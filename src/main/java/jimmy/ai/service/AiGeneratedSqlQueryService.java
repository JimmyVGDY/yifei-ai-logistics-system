package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.model.AiPromptTemplateCodes;
import jimmy.ai.util.SseChatContext;
import jimmy.ai.model.AiGeneratedSqlQueryResult;
import jimmy.ai.model.PromptRenderResult;
import jimmy.common.util.LogMaskUtils;
import jimmy.system.config.ModuleManifest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 生成 SQL 查询服务：模型只生成候选 SELECT，后端校验后执行只读查询。
 */
@Slf4j
@Service
public class AiGeneratedSqlQueryService {

    private static final int MAX_ROWS = 20;
    private static final int MAX_SQL_REPAIR_ATTEMPTS = 3;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CUSTOMER_SCOPE_MESSAGE = "当前账号存在客户数据范围限制，AI 暂不支持自定义关联 SQL 查询。请使用客户、订单或轨迹等普通只读查询。";
    private static final Pattern SIMPLE_LIMIT_PATTERN = Pattern.compile("(?is)\\s+limit\\s+(\\d+)\\s*$");
    private static final String DETAIL_QUERY_MESSAGE = "这个问题属于普通业务明细查询，请使用标准业务模块查询。临时统计分析仅用于统计、汇总、排名和关联分析。";
    private static final Set<String> INTERNAL_DISPLAY_FIELDS = Set.of(
            "id", "deleted", "version", "create_by", "update_by", "created_by", "updated_by",
            "trace_id", "operation_id", "login_session_id", "cursor_id", "password", "token",
            "secret", "api_key", "access_token", "refresh_token", "mobile_hash"
    );
    private static final Map<String, String> FIELD_LABELS = buildFieldLabels();

    private final AiModelGateway modelGateway;
    private final AiSqlSafetyValidator sqlSafetyValidator;
    private final JdbcTemplate jdbcTemplate;
    private final AiSensitiveDataMasker masker;
    private final AiAuditLogService auditLogService;
    private final ColumnPermissionResolver columnPermissionResolver;
    private final AiPromptTemplateService promptTemplateService;
    private final AiSqlOutputValidator sqlOutputValidator;

    public AiGeneratedSqlQueryService(AiModelGateway modelGateway,
                                      AiSqlSafetyValidator sqlSafetyValidator,
                                      JdbcTemplate jdbcTemplate,
                                      AiSensitiveDataMasker masker,
                                      AiAuditLogService auditLogService,
                                      ColumnPermissionResolver columnPermissionResolver,
                                      AiPromptTemplateService promptTemplateService,
                                      AiSqlOutputValidator sqlOutputValidator) {
        this.modelGateway = modelGateway;
        this.sqlSafetyValidator = sqlSafetyValidator;
        this.jdbcTemplate = jdbcTemplate;
        this.masker = masker;
        this.auditLogService = auditLogService;
        this.columnPermissionResolver = columnPermissionResolver;
        this.promptTemplateService = promptTemplateService;
        this.sqlOutputValidator = sqlOutputValidator;
    }

    public AiGeneratedSqlQueryResult query(String message) {
        if (!shouldUseGeneratedSql(message)) {
            return AiGeneratedSqlQueryResult.skipped();
        }
        if (looksLikePlainDetailQuery(message)) {
            return AiGeneratedSqlQueryResult.message(DETAIL_QUERY_MESSAGE);
        }
        // 客户账号存在天然数据范围限制，临时关联 SQL 很难安全补齐客户隔离条件，先统一走普通只读查询。
        if (isCustomerRole()) {
            return AiGeneratedSqlQueryResult.message(CUSTOMER_SCOPE_MESSAGE);
        }
        if (!modelGateway.configured()) {
            return AiGeneratedSqlQueryResult.message("当前未配置模型，无法生成临时只读 SQL。请使用普通业务查询。");
        }
        try {
            Optional<String> candidate = modelGateway.chat(
                    renderPrompt(AiPromptTemplateCodes.AI_SQL_GENERATE_SYSTEM, message, null, null, null, 0),
                    renderPrompt(AiPromptTemplateCodes.AI_SQL_GENERATE_USER, message, null, null, null, 0),
                    "sql_generate",
                    null
            );
            if (candidate.isEmpty()) {
                recordSqlStage("生成", message, "SQL_GENERATE_EMPTY：模型未生成 SQL", false);
                return AiGeneratedSqlQueryResult.message("模型暂时无法生成只读查询语句，请稍后重试。");
            }
            String candidateSql = validateSqlOutput("生成输出校验", message, candidate.get());
            recordSqlStage("生成", message, "候选 SQL 已生成", true);

            Optional<String> checked = modelGateway.chat(
                    renderPrompt(AiPromptTemplateCodes.AI_SQL_SELF_CHECK_SYSTEM, message, candidateSql, null, null, 0),
                    renderPrompt(AiPromptTemplateCodes.AI_SQL_SELF_CHECK_USER, message, candidateSql, null, null, 0),
                    "sql_self_check",
                    null
            );
            if (checked.isEmpty() || !StringUtils.hasText(checked.get())) {
                recordSqlStage("自检", message, "SQL_SELF_CHECK_FAILED：模型自检未返回可用 SQL", false);
                return AiGeneratedSqlQueryResult.message("临时只读查询自检失败，请换一种描述后重试。");
            }
            String checkedSql = validateSqlOutput("自检输出校验", message, checked.get());
            recordSqlStage("自检", message, "模型自检完成", true);

            return executeWithRepairLoop(message, checkedSql);
        } catch (IllegalArgumentException exception) {
            log.info("AI 生成 SQL 查询被安全校验拦截，errorCode=SQL_SECURITY_BLOCKED, reason={}", exception.getMessage());
            recordSqlStage("安全校验", message, "SQL_SECURITY_BLOCKED：" + exception.getMessage(), false);
            return AiGeneratedSqlQueryResult.message(exception.getMessage());
        } catch (RuntimeException exception) {
            // 执行异常可能包含完整 SQL 或数据库细节，日志中只保留异常类型，详细 SQL 不落控制台。
            log.warn("AI 生成 SQL 查询失败，errorCode=SQL_EXECUTION_ERROR, exceptionType={}", exception.getClass().getSimpleName());
            recordSqlStage("执行", message, "SQL_EXECUTION_ERROR：临时查询执行失败", false);
            return AiGeneratedSqlQueryResult.message("临时只读查询执行失败，请换一种描述或联系系统管理员。");
        }
    }

    private AiGeneratedSqlQueryResult executeWithRepairLoop(String message, String checkedSql) {
        String currentSql = checkedSql;
        for (int repairAttempt = 0; repairAttempt <= MAX_SQL_REPAIR_ATTEMPTS; repairAttempt++) {
            try {
                // 模型输出只当作候选文本；每一轮纠错后都重新做表白名单、权限、敏感字段和单语句校验。
                AiSqlSafetyValidator.ValidatedSql validatedSql = sqlSafetyValidator.validate(currentSql);
                recordSqlStage("安全校验", message, "校验通过，tables=" + validatedSql.tables(), true);
                preflight(validatedSql.sql());
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(applyResultLimit(validatedSql.sql()));
                List<Map<String, Object>> filteredRows = filterByColumnPermission(rows, validatedSql);
                if (!rows.isEmpty() && filteredRows.stream().allMatch(Map::isEmpty)) {
                    recordSqlStage("列权限过滤", message, "查询命中数据，但当前账号无权查看返回列", false);
                    return AiGeneratedSqlQueryResult.message("临时只读 SQL 查询完成，但当前账号无权查看返回字段。如有需要，请联系系统管理员。");
                }
                DisplayRows displayRows = displayRows(formatRows(filteredRows), validatedSql);
                if (!filteredRows.isEmpty() && displayRows.rows().stream().allMatch(Map::isEmpty)) {
                    recordSqlStage("展示过滤", message, "查询命中数据，但没有可安全展示的字段", false);
                    return AiGeneratedSqlQueryResult.message("统计分析完成，但当前结果不包含可安全展示的字段。");
                }
                String summary = summary(displayRows.rows());
                log.info("AI 生成 SQL 查询完成，tables={}, rows={}, repairAttempt={}, sql={}",
                        validatedSql.tables(), displayRows.rows().size(), repairAttempt, LogMaskUtils.maskText(validatedSql.sql()));
                recordSqlStage("执行", message, "执行成功，rows=" + displayRows.rows().size()
                        + (repairAttempt > 0 ? "，纠错次数=" + repairAttempt : ""), true);
                return new AiGeneratedSqlQueryResult(true, summary, displayRows.rows(), displayRows.columns(), "统计分析", "统计结果");
            } catch (BadSqlGrammarException exception) {
                if (repairAttempt >= MAX_SQL_REPAIR_ATTEMPTS) {
                    log.warn("AI 生成 SQL 语法预检或执行失败，errorCode=SQL_SYNTAX_ERROR, repairAttempts={}, exceptionType={}, reason={}, sqlShape={}",
                            repairAttempt, exception.getClass().getSimpleName(), syntaxErrorSummary(exception), sqlAuditDigest(currentSql));
                    recordSqlStage("执行", message, "SQL_SYNTAX_ERROR：临时查询语法错误，已纠错" + repairAttempt + "次", false);
                    return AiGeneratedSqlQueryResult.message("临时只读查询存在语法问题，已自动纠错多次仍未成功，请换一种描述后重试。");
                }
                int nextAttempt = repairAttempt + 1;
                recordSqlStage("语法纠错", message, "SQL_SYNTAX_ERROR：开始第" + nextAttempt + "次自动纠错", false);
                Optional<String> repaired = modelGateway.chat(
                        renderPrompt(AiPromptTemplateCodes.AI_SQL_REPAIR_SYSTEM, message, null, currentSql, exception, nextAttempt),
                        renderPrompt(AiPromptTemplateCodes.AI_SQL_REPAIR_USER, message, null, currentSql, exception, nextAttempt),
                        "sql_repair",
                        null
                );
                if (repaired.isEmpty() || !StringUtils.hasText(repaired.get())) {
                    recordSqlStage("语法纠错", message, "SQL_SELF_CHECK_FAILED：第" + nextAttempt + "次纠错未返回可用 SQL", false);
                    return AiGeneratedSqlQueryResult.message("临时只读查询自动纠错失败，请换一种描述后重试。");
                }
                currentSql = validateSqlOutput("纠错输出校验", message, repaired.get());
                recordSqlStage("语法纠错", message, "第" + nextAttempt + "次纠错完成，准备重新校验", true);
            }
        }
        return AiGeneratedSqlQueryResult.message("临时只读查询存在语法问题，请换一种描述后重试。");
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
                || lower.contains("多少")
                || lower.contains("占比")
                || lower.contains("比例")
                || lower.contains("group")
                || lower.contains("join");
    }

    private boolean looksLikePlainDetailQuery(String message) {
        if (!StringUtils.hasText(message)) {
            return true;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        boolean explicitAnalysis = lower.contains("sql")
                || lower.contains("连表")
                || lower.contains("关联")
                || lower.contains("统计")
                || lower.contains("汇总")
                || lower.contains("数量")
                || lower.contains("总数")
                || lower.contains("排名")
                || lower.contains("最多")
                || lower.contains("最少")
                || lower.contains("平均")
                || lower.contains("多少")
                || lower.contains("占比")
                || lower.contains("比例")
                || lower.contains("group")
                || lower.contains("join");
        if (explicitAnalysis) {
            return false;
        }
        return lower.contains("全部")
                || lower.contains("所有")
                || lower.contains("全量")
                || lower.contains("明细")
                || lower.contains("列表")
                || lower.contains("查看")
                || lower.contains("看一下")
                || lower.contains("看看")
                || lower.contains("查询");
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

    private PromptRenderResult renderPrompt(String templateCode,
                                            String message,
                                            String candidateSql,
                                            String sql,
                                            BadSqlGrammarException exception,
                                            int attempt) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("message", message);
        variables.put("candidateSql", candidateSql == null ? "" : candidateSql);
        variables.put("sql", sql == null ? "" : sql);
        variables.put("attempt", attempt);
        variables.put("errorSummary", exception == null ? "" : syntaxErrorSummary(exception));
        variables.put("schemaPrompt", sqlSafetyValidator.schemaPrompt());
        variables.put("currentBusinessDate", LocalDateTime.now().toLocalDate().toString());
        return promptTemplateService.render(templateCode, variables);
    }

    private String validateSqlOutput(String stage, String message, String output) {
        try {
            return sqlOutputValidator.normalizeSelect(output);
        } catch (IllegalArgumentException exception) {
            recordSqlStage(stage, message, exception.getMessage(), false);
            throw exception;
        }
    }

    private String systemPrompt() {
        return """
                你是物流管理系统的只读 SQL 生成器。
                只返回一条 MySQL 兼容 SELECT 查询，不要解释，不要 Markdown，不要分号。
                可以使用显式 JOIN、GROUP BY、ORDER BY、聚合函数和 WHERE 条件。
                禁止子查询、UNION 和逗号连表；多表查询必须使用显式 JOIN。
                普通字段不要使用中文别名，保持数据库字段名作为返回列名；聚合字段可使用英文别名，例如 order_count。
                禁止 INSERT、UPDATE、DELETE、DROP、ALTER、CREATE、TRUNCATE、REPLACE、CALL、EXECUTE。
                常用关联关系：
                - logistics_waybill.order_id = logistics_order.id
                - logistics_dispatch.order_id = logistics_order.id，logistics_dispatch.waybill_id = logistics_waybill.id
                - logistics_task.order_id = logistics_order.id，logistics_task.waybill_id = logistics_waybill.id
                - logistics_track.order_id = logistics_order.id，logistics_track.waybill_id = logistics_waybill.id
                - logistics_exception.order_id = logistics_order.id，logistics_exception.task_id = logistics_task.id
                - logistics_fee.order_id = logistics_order.id
                如果需要订单号，只能使用 logistics_order.order_no，或通过 order_id JOIN logistics_order 后读取。
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
                禁止子查询、UNION 和逗号连表；多表查询必须使用显式 JOIN。
                普通字段不要使用中文别名，保持数据库字段名作为返回列名；聚合字段可使用英文别名。
                禁止 INSERT、UPDATE、DELETE、DROP、ALTER、CREATE、TRUNCATE、REPLACE、CALL、EXECUTE。
                常用关联关系：
                - logistics_waybill.order_id = logistics_order.id
                - logistics_dispatch.order_id = logistics_order.id，logistics_dispatch.waybill_id = logistics_waybill.id
                - logistics_task.order_id = logistics_order.id，logistics_task.waybill_id = logistics_waybill.id
                - logistics_track.order_id = logistics_order.id，logistics_track.waybill_id = logistics_waybill.id
                - logistics_exception.order_id = logistics_order.id，logistics_exception.task_id = logistics_task.id
                - logistics_fee.order_id = logistics_order.id
                如果需要订单号，只能使用 logistics_order.order_no，或通过 order_id JOIN logistics_order 后读取。
                查询必须使用下面的白名单表和字段：
                """ + sqlSafetyValidator.schemaPrompt();
    }

    private String selfCheckUserPrompt(String message, String candidateSql) {
        return "用户问题：" + message + "\n候选 SQL：\n" + candidateSql + "\n请自检并返回最终可执行的单条 SELECT。";
    }

    private String repairSystemPrompt() {
        return """
                你是物流管理系统的 MySQL SELECT 语法纠错器。
                你会收到用户问题、上一轮 SQL 和数据库语法错误摘要。请修复语法、字段、别名、聚合和 JOIN 问题。
                只允许返回一条修正后的 MySQL SELECT 查询，不要解释，不要 Markdown，不要分号。
                不要扩大查询范围，不要新增写操作，不要使用 select *。
                禁止子查询、UNION 和逗号连表；多表查询必须使用显式 JOIN。
                普通字段不要使用中文别名，保持数据库字段名作为返回列名；聚合字段可使用英文别名。
                如果错误原因是 Unknown column，请只使用白名单里的真实字段；需要订单号时通过 order_id JOIN logistics_order。
                查询必须使用下面的白名单表和字段：
                """ + sqlSafetyValidator.schemaPrompt();
    }

    private String repairUserPrompt(String message, String sql, BadSqlGrammarException exception, int attempt) {
        return "用户问题：" + message
                + "\n第" + attempt + "次纠错。上一轮 SQL：\n" + sql
                + "\n数据库语法错误摘要：" + syntaxErrorSummary(exception)
                + "\n请修正后只返回单条可执行 SELECT。";
    }

    private String syntaxErrorSummary(BadSqlGrammarException exception) {
        Throwable cause = exception.getMostSpecificCause();
        if (cause == null || !StringUtils.hasText(cause.getMessage())) {
            return exception.getClass().getSimpleName();
        }
        return LogMaskUtils.maskText(cause.getMessage());
    }

    private String applyResultLimit(String sql) {
        // 不再把 SQL 包成子查询，避免 ORDER/GROUP/JOIN 场景被外层包装破坏；只在顶层追加或收紧 LIMIT。
        String normalized = sql.strip().replaceAll(";+$", "").strip();
        Matcher matcher = SIMPLE_LIMIT_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return normalized + " limit " + MAX_ROWS;
        }
        int limit = Integer.parseInt(matcher.group(1));
        if (limit <= MAX_ROWS) {
            return normalized;
        }
        return matcher.replaceFirst(" limit " + MAX_ROWS).strip();
    }

    private void preflight(String sql) {
        // 使用 EXPLAIN 做语法和字段预检，不执行真实数据读取，也不改变原 SQL 结构。
        jdbcTemplate.queryForList("explain " + sql);
    }

    private String sqlAuditDigest(String sql) {
        if (!StringUtils.hasText(sql)) {
            return "-";
        }
        String digest = sql
                .replaceAll("'([^'\\\\]|\\\\.)*'", "'?'")
                .replaceAll("\"([^\"\\\\]|\\\\.)*\"", "\"?\"")
                .replaceAll("\\b\\d+(?:\\.\\d+)?\\b", "?")
                .replaceAll("\\s+", " ")
                .strip();
        if (digest.length() > 300) {
            return digest.substring(0, 300) + "...";
        }
        return digest;
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

    private DisplayRows displayRows(List<Map<String, Object>> rows, AiSqlSafetyValidator.ValidatedSql validatedSql) {
        if (rows == null || rows.isEmpty()) {
            return new DisplayRows(List.of(), List.of());
        }
        Set<String> registeredColumns = new LinkedHashSet<>();
        for (AiReadableSchemaRegistry.TableRule rule : validatedSql.tableRules()) {
            registeredColumns.addAll(rule.columnSet());
        }
        Map<String, String> labelsByResultColumn = new LinkedHashMap<>();
        int derivedIndex = 1;
        for (Map<String, Object> row : rows) {
            for (String column : row.keySet()) {
                if (labelsByResultColumn.containsKey(column)) {
                    continue;
                }
                String label = displayLabel(column, validatedSql.resultColumnSources(), registeredColumns, derivedIndex);
                if (StringUtils.hasText(label) && label.startsWith("统计字段")) {
                    derivedIndex++;
                }
                labelsByResultColumn.put(column, label);
            }
        }

        List<Map<String, Object>> displayRows = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        for (String label : labelsByResultColumn.values()) {
            if (StringUtils.hasText(label) && !columns.contains(label)) {
                columns.add(label);
            }
        }
        for (Map<String, Object> row : rows) {
            Map<String, Object> displayRow = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String label = labelsByResultColumn.get(entry.getKey());
                if (!StringUtils.hasText(label)) {
                    continue;
                }
                displayRow.put(uniqueLabel(label, displayRow), entry.getValue());
            }
            displayRows.add(displayRow);
        }
        return new DisplayRows(displayRows, columns);
    }

    private String displayLabel(String column,
                                Map<String, String> resultColumnSources,
                                Set<String> registeredColumns,
                                int derivedIndex) {
        String resultColumn = normalizeResultColumn(column);
        String sourceColumn = resultColumnSources.get(resultColumn);
        if (StringUtils.hasText(sourceColumn)) {
            return businessLabel(sourceColumn);
        }
        if (registeredColumns.contains(resultColumn)) {
            return businessLabel(resultColumn);
        }
        if (isSafeDerivedColumn(resultColumn)) {
            return derivedLabel(resultColumn, derivedIndex);
        }
        return "";
    }

    private String businessLabel(String column) {
        String normalized = normalizeResultColumn(column);
        if (INTERNAL_DISPLAY_FIELDS.contains(normalized)) {
            return "";
        }
        if (isForeignKeyDisplayField(normalized)) {
            return "";
        }
        return FIELD_LABELS.getOrDefault(normalized, "");
    }

    private boolean isForeignKeyDisplayField(String column) {
        return StringUtils.hasText(column) && (column.endsWith("_id") || column.equals("id"));
    }

    private String derivedLabel(String columnName, int index) {
        String lower = normalizeResultColumn(columnName);
        if (lower.contains("order") && lower.contains("count")) {
            return "订单数量";
        }
        if (lower.contains("customer") && lower.contains("count")) {
            return "客户数量";
        }
        if (lower.contains("waybill") && lower.contains("count")) {
            return "运单数量";
        }
        if (lower.contains("task") && lower.contains("count")) {
            return "任务数量";
        }
        if (lower.contains("count") || lower.equals("cnt") || lower.equals("total")) {
            return "数量";
        }
        if (lower.contains("amount") || lower.contains("fee") || lower.contains("sum")) {
            return "金额合计";
        }
        if (lower.contains("avg") || lower.contains("average")) {
            return "平均值";
        }
        if (lower.contains("max")) {
            return "最大值";
        }
        if (lower.contains("min")) {
            return "最小值";
        }
        if (lower.matches("[\\u4e00-\\u9fa5]+")) {
            return lower;
        }
        return "统计字段" + index;
    }

    private String uniqueLabel(String label, Map<String, Object> row) {
        if (!row.containsKey(label)) {
            return label;
        }
        int index = 2;
        while (row.containsKey(label + index)) {
            index++;
        }
        return label + index;
    }

    private static Map<String, String> buildFieldLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (ModuleManifest.ModuleEntry module : ModuleManifest.all()) {
            for (ModuleManifest.ColumnDef column : module.columns()) {
                labels.putIfAbsent(column.fieldName(), column.label());
            }
        }
        labels.putIfAbsent("created_at", "创建时间");
        labels.putIfAbsent("updated_at", "更新时间");
        labels.putIfAbsent("create_time", "创建时间");
        labels.putIfAbsent("update_time", "更新时间");
        labels.putIfAbsent("payable_amount", "应付金额");
        labels.putIfAbsent("base_amount", "基础金额");
        labels.putIfAbsent("fuel_surcharge", "燃油附加费");
        labels.putIfAbsent("discount_amount", "优惠金额");
        labels.putIfAbsent("pay_status", "支付状态");
        return Map.copyOf(labels);
    }

    /**
     * 按 RBAC 列权限过滤查询结果：用户无权查看的业务列会被移除。
     * <p>
     * 普通业务列按 SQL 校验阶段识别出的“结果列 -> 原始字段”映射过滤；
     * 聚合统计列（如 order_count）没有对应原始字段，在安全校验已通过的前提下允许展示。
     */
    private List<Map<String, Object>> filterByColumnPermission(List<Map<String, Object>> rows,
                                                               AiSqlSafetyValidator.ValidatedSql validatedSql) {
        Set<String> allowed = new LinkedHashSet<>();
        Set<String> registeredColumns = new LinkedHashSet<>();
        for (AiReadableSchemaRegistry.TableRule rule : validatedSql.tableRules()) {
            registeredColumns.addAll(rule.columnSet());
            allowed.addAll(columnPermissionResolver.allowedColumns(rule.moduleCode()));
        }
        Map<String, String> resultColumnSources = validatedSql.resultColumnSources();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> filtered = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String resultColumn = normalizeResultColumn(entry.getKey());
                String sourceColumn = resultColumnSources.get(resultColumn);
                if (sourceColumn != null) {
                    if (allowed.contains(sourceColumn)) {
                        filtered.put(entry.getKey(), entry.getValue());
                    }
                    continue;
                }
                if (registeredColumns.contains(resultColumn)) {
                    if (allowed.contains(resultColumn)) {
                        filtered.put(entry.getKey(), entry.getValue());
                    }
                    continue;
                }
                if (isSafeDerivedColumn(resultColumn)) {
                    filtered.put(entry.getKey(), entry.getValue());
                }
            }
            result.add(filtered);
        }
        return result;
    }

    private String normalizeResultColumn(String columnName) {
        return columnName == null ? "" : columnName.replace("`", "").trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 聚合/统计结果列不在业务列注册表中，例如 order_count、total_amount。
     * 这里只允许简单别名，避免模型把复杂表达式或明显敏感名称作为“派生列”绕过过滤。
     */
    private boolean isSafeDerivedColumn(String columnName) {
        if (!StringUtils.hasText(columnName)) {
            return false;
        }
        if (!columnName.matches("[a-z0-9_\\u4e00-\\u9fa5]+")) {
            return false;
        }
        String lower = columnName.toLowerCase(Locale.ROOT);
        return !(lower.contains("password")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("mobile")
                || lower.contains("phone")
                || lower.contains("email")
                || lower.contains("address")
                || lower.contains("license")
                || lower.endsWith("_id"));
    }

    private String summary(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "统计分析完成，未找到符合条件的数据。";
        }
        return masker.mask("统计分析完成，返回 " + rows.size() + " 条记录。完整结果已在下方表格展示。");
    }

    private record DisplayRows(List<Map<String, Object>> rows, List<String> columns) {
    }
}
