package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.util.SseChatContext;
import jimmy.system.config.StandardColumnRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 生成 SQL 安全校验器：只放行单条 SELECT，并基于表白名单做权限校验。
 * <p>
 * 敏感列检测从 {@link StandardColumnRegistry} 动态获取，不再使用硬编码正则。
 * 当 SQL 包含敏感列时，拒绝执行并返回精确的敏感列清单，供 AI 自行修正。
 */
@Component
public class AiSqlSafetyValidator {

    private static final Pattern TABLE_PATTERN = Pattern.compile("(?i)\\b(?:from|join)\\s+([`\\w.]+)");
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile("(?i)\\b(insert|update|delete|drop|alter|create|truncate|replace|merge|grant|revoke|call|execute|load_file|outfile|infile)\\b");
    private static final Pattern SELECT_ALL_PATTERN = Pattern.compile("(?i)(^|\\s|,)\\*\\s*(,|from)\\b|\\b\\w+\\.\\*\\b");
    private static final Pattern UNION_PATTERN = Pattern.compile("(?i)\\bunion\\b");
    private static final Pattern SUBQUERY_TABLE_PATTERN = Pattern.compile("(?i)\\b(?:from|join)\\s*\\(");
    private static final Pattern COMMA_JOIN_PATTERN = Pattern.compile("(?is)\\bfrom\\s+[`\\w.]+(?:\\s+(?:as\\s+)?\\w+)?\\s*,");
    private static final Set<String> SYSTEM_SCHEMAS = Set.of("information_schema", "mysql", "performance_schema");

    /** 仍保留静态禁止列作为底层防线（与 RBAC 敏感标记互补） */
    private static final Set<String> STATIC_FORBIDDEN = Set.of(
            "password", "token", "secret", "api_key", "access_token", "refresh_token", "mobile_hash"
    );

    private final AiReadableSchemaRegistry schemaRegistry;
    private final Map<String, Set<String>> dynamicSensitiveFieldsByModule;
    private final ColumnPermissionResolver columnPermissionResolver;
    private final PermissionEvaluator permissionEvaluator;

    public AiSqlSafetyValidator() {
        this(new AiReadableSchemaRegistry(), null, null, new PermissionEvaluator());
    }

    public AiSqlSafetyValidator(AiReadableSchemaRegistry schemaRegistry,
                                StandardColumnRegistry columnRegistry) {
        this(schemaRegistry, columnRegistry, null, new PermissionEvaluator());
    }

    @Autowired
    public AiSqlSafetyValidator(AiReadableSchemaRegistry schemaRegistry,
                                StandardColumnRegistry columnRegistry,
                                ColumnPermissionResolver columnPermissionResolver,
                                PermissionEvaluator permissionEvaluator) {
        this.schemaRegistry = schemaRegistry;
        this.dynamicSensitiveFieldsByModule = buildSensitiveFields(columnRegistry);
        this.columnPermissionResolver = columnPermissionResolver;
        this.permissionEvaluator = permissionEvaluator;
    }

    /**
     * 从 StandardColumnRegistry 动态构建“模块 -> 敏感列”索引。
     * <p>
     * 临时 SQL 里的手机号、费用金额等业务敏感列不是一律禁止：
     * 当前账号具备对应模块的列权限时可以查询；密码、token 等静态禁止列始终拦截。
     */
    private static Map<String, Set<String>> buildSensitiveFields(StandardColumnRegistry columnRegistry) {
        if (columnRegistry == null) return Collections.emptyMap();
        Map<String, Set<String>> fields = new LinkedHashMap<>();
        for (String module : columnRegistry.moduleNames()) {
            Set<String> moduleFields = new LinkedHashSet<>();
            for (StandardColumnRegistry.ColumnDef col : columnRegistry.columns(module)) {
                if (col.sensitive()) {
                    moduleFields.add(col.fieldName());
                }
            }
            if (!moduleFields.isEmpty()) {
                fields.put(module, Collections.unmodifiableSet(moduleFields));
            }
        }
        return Collections.unmodifiableMap(fields);
    }

    public ValidatedSql validate(String rawSql) {
        String sql = normalize(rawSql);
        if (!StringUtils.hasText(sql)) {
            throw new IllegalArgumentException("AI 未生成可执行的只读查询语句");
        }
        String lower = sql.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("select ")) {
            throw new IllegalArgumentException("AI 只允许生成 SELECT 查询语句");
        }
        if (DANGEROUS_PATTERN.matcher(sql).find() || sql.contains("--") || sql.contains("/*") || sql.contains("*/")) {
            throw new IllegalArgumentException("查询语句包含不允许的关键字");
        }
        List<String> staticForbiddenColumns = findStaticForbiddenColumns(sql);
        if (!staticForbiddenColumns.isEmpty()) {
            throw new IllegalArgumentException(
                    "SQL包含敏感列，请修改后重试。敏感列：" + String.join("、", staticForbiddenColumns));
        }
        rejectUnsupportedSqlShape(sql);
        if (hasMultipleStatements(sql)) {
            throw new IllegalArgumentException("AI 只允许生成单条查询语句");
        }
        if (SELECT_ALL_PATTERN.matcher(sql).find()) {
            throw new IllegalArgumentException("查询语句包含不允许返回的敏感字段");
        }
        List<String> tables = extractTables(sql);
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("查询语句未识别到可校验的数据表");
        }
        List<AiReadableSchemaRegistry.TableRule> rules = new ArrayList<>();
        for (String table : tables) {
            AiReadableSchemaRegistry.TableRule rule = schemaRegistry.findTable(table);
            if (rule == null) {
                throw new IllegalArgumentException("查询涉及未开放的数据表");
            }
            if (!rule.generatedSqlAllowed()) {
                throw new IllegalArgumentException("临时只读 SQL 不支持查询该类内部数据，请使用系统提供的查询入口。");
            }
            if (!hasPermission(rule.permission())) {
                throw new IllegalArgumentException("当前账号权限不足，无法查询该类数据。如有需要，请联系系统管理员。");
            }
            rules.add(rule);
        }
        List<String> violatedColumns = findSensitiveColumns(sql, rules);
        if (!violatedColumns.isEmpty()) {
            throw new IllegalArgumentException(
                    "SQL包含当前账号无权查看的敏感列，请修改后重试。敏感列：" + String.join("、", violatedColumns));
        }
        Map<String, String> resultColumnSources = extractResultColumnSources(sql, rules);
        return new ValidatedSql(sql, tables, rules, resultColumnSources);
    }

    private List<String> findStaticForbiddenColumns(String sql) {
        Set<String> identifiers = extractIdentifiers(sql);
        List<String> violated = new ArrayList<>();
        for (String field : STATIC_FORBIDDEN) {
            if (identifiers.contains(field)) {
                violated.add(field);
            }
        }
        return violated;
    }

    /**
     * 临时 SQL 只开放简单、可审计的 SELECT 子集。
     * <p>
     * 子查询、UNION、逗号连表容易让正则表解析漏表；在引入 AST 级 SQL 解析器前先保守拦截。
     */
    private void rejectUnsupportedSqlShape(String sql) {
        if (UNION_PATTERN.matcher(sql).find()) {
            throw new IllegalArgumentException("临时只读 SQL 暂不支持 UNION 查询，请换一种统计描述。");
        }
        if (SUBQUERY_TABLE_PATTERN.matcher(sql).find()) {
            throw new IllegalArgumentException("临时只读 SQL 暂不支持子查询，请使用普通 JOIN 或换一种描述。");
        }
        if (COMMA_JOIN_PATTERN.matcher(sql).find()) {
            throw new IllegalArgumentException("临时只读 SQL 暂不支持逗号连表，请使用显式 JOIN。");
        }
    }

    /**
     * 检测 SQL 中是否包含当前账号无权查看的敏感列名。
     * <p>
     * 静态禁止列（密码、token 等）始终拦截；业务敏感列按照涉及表所属模块的列权限判断。
     */
    private List<String> findSensitiveColumns(String sql, List<AiReadableSchemaRegistry.TableRule> rules) {
        Set<String> identifiers = extractIdentifiers(sql);
        Set<String> violated = new LinkedHashSet<>();
        if (dynamicSensitiveFieldsByModule.isEmpty()) return new ArrayList<>(violated);
        for (AiReadableSchemaRegistry.TableRule rule : rules) {
            Set<String> sensitiveFields = dynamicSensitiveFieldsByModule.getOrDefault(rule.moduleCode(), Set.of());
            for (String field : sensitiveFields) {
                if (identifiers.contains(field) && !hasColumnPermission(rule.moduleCode(), field)) {
                    violated.add(field);
                }
            }
        }
        return new ArrayList<>(violated);
    }

    /**
     * 从 SQL 文本中提取所有标识符（字母、数字、下划线组成的单词）。
     */
    private Set<String> extractIdentifiers(String sql) {
        Set<String> ids = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("\\b([a-zA-Z_]\\w*)\\b").matcher(sql);
        while (matcher.find()) {
            String word = matcher.group(1).toLowerCase(Locale.ROOT);
            // 排除 SQL 关键字
            if (!SQL_KEYWORDS.contains(word)) {
                ids.add(word);
            }
        }
        return ids;
    }

    private static final Set<String> SQL_KEYWORDS = Set.of(
            "select", "from", "where", "and", "or", "not", "in", "like", "between",
            "join", "left", "right", "inner", "outer", "on", "as", "group", "by",
            "order", "asc", "desc", "having", "limit", "offset", "union", "all",
            "distinct", "case", "when", "then", "else", "end", "is", "null",
            "count", "sum", "avg", "min", "max", "coalesce", "cast", "convert",
            "true", "false", "exists", "date", "time", "timestamp", "varchar",
            "int", "bigint", "decimal", "char", "text", "boolean", "integer",
            "create", "table", "alter", "drop", "insert", "update", "delete",
            "primary", "key", "foreign", "references", "index", "unique", "constraint",
            "into", "values", "set", "default", "check", "view", "with", "if"
    );

    private boolean hasPermission(String permission) {
        return permissionEvaluator.hasPermission(permission);
    }

    private boolean hasColumnPermission(String moduleCode, String field) {
        if (columnPermissionResolver == null || !StringUtils.hasText(moduleCode)) {
            return false;
        }
        return columnPermissionResolver.allowedColumns(moduleCode).contains(field);
    }

    public String schemaPrompt() {
        return schemaRegistry.schemaPrompt();
    }

    private String normalize(String rawSql) {
        if (!StringUtils.hasText(rawSql)) {
            return "";
        }
        String sql = rawSql.trim();
        Matcher block = Pattern.compile("(?is)```sql\\s*(.*?)\\s*```").matcher(sql);
        if (block.find()) {
            sql = block.group(1).trim();
        } else {
            Matcher anyBlock = Pattern.compile("(?is)```\\s*(.*?)\\s*```").matcher(sql);
            if (anyBlock.find()) {
                sql = anyBlock.group(1).trim();
            }
        }
        sql = sql.replaceAll(";+$", "").trim();
        return sql;
    }

    private boolean hasMultipleStatements(String sql) {
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '\'') {
                inString = !inString;
            }
            if (c == ';' && !inString) {
                return true;
            }
        }
        return false;
    }

    private List<String> extractTables(String sql) {
        List<String> tables = new ArrayList<>();
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        while (matcher.find()) {
            String table = matcher.group(1).replace("`", "");
            if (table.contains(".")) {
                String[] parts = table.split("\\.");
                if (parts.length == 2 && SYSTEM_SCHEMAS.contains(parts[0].toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("查询涉及未开放的数据表");
                }
                table = parts[parts.length - 1];
            }
            String normalized = table.toLowerCase(Locale.ROOT);
            if (!tables.contains(normalized)) {
                tables.add(normalized);
            }
        }
        return tables;
    }

    /**
     * 建立“结果列别名 -> 原始业务字段”的映射，用于执行后做列权限过滤。
     * <p>
     * 这里只解析简单列引用，例如 {@code order_no}、{@code o.order_no as order_no_cn}。
     * 聚合表达式、计算表达式不映射为业务字段，后续按派生列处理。
     */
    private Map<String, String> extractResultColumnSources(String sql,
                                                           List<AiReadableSchemaRegistry.TableRule> rules) {
        String selectClause = extractSelectClause(sql);
        if (!StringUtils.hasText(selectClause)) {
            return Map.of();
        }
        Set<String> registeredColumns = new LinkedHashSet<>();
        for (AiReadableSchemaRegistry.TableRule rule : rules) {
            registeredColumns.addAll(rule.columnSet());
        }
        Map<String, String> sources = new LinkedHashMap<>();
        for (String item : splitTopLevelComma(selectClause)) {
            ColumnProjection projection = parseSimpleColumnProjection(item);
            if (projection != null && registeredColumns.contains(projection.sourceColumn())) {
                sources.put(normalizeAlias(projection.resultColumn()), projection.sourceColumn());
            }
        }
        return Collections.unmodifiableMap(sources);
    }

    private String extractSelectClause(String sql) {
        Matcher matcher = Pattern.compile("(?is)^\\s*select\\s+(.*?)\\s+from\\s+").matcher(sql);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1);
    }

    private List<String> splitTopLevelComma(String text) {
        List<String> items = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'') {
                inString = !inString;
            } else if (!inString && c == '(') {
                depth++;
            } else if (!inString && c == ')' && depth > 0) {
                depth--;
            } else if (!inString && depth == 0 && c == ',') {
                items.add(text.substring(start, i).trim());
                start = i + 1;
            }
        }
        items.add(text.substring(start).trim());
        return items;
    }

    private ColumnProjection parseSimpleColumnProjection(String item) {
        Matcher matcher = Pattern.compile("(?is)^\\s*(?:`?\\w+`?\\.)?`?([a-zA-Z_]\\w*)`?(?:\\s+(?:as\\s+)?`?([\\w\\u4e00-\\u9fa5]+)`?)?\\s*$")
                .matcher(item);
        if (!matcher.matches()) {
            return null;
        }
        String source = matcher.group(1).toLowerCase(Locale.ROOT);
        String alias = matcher.group(2);
        return new ColumnProjection(source, StringUtils.hasText(alias) ? alias : source);
    }

    private String normalizeAlias(String alias) {
        return alias == null ? "" : alias.replace("`", "").trim().toLowerCase(Locale.ROOT);
    }

    public record ValidatedSql(String sql,
                               List<String> tables,
                               List<AiReadableSchemaRegistry.TableRule> tableRules,
                               Map<String, String> resultColumnSources) {
    }

    private record ColumnProjection(String sourceColumn, String resultColumn) {
    }
}
