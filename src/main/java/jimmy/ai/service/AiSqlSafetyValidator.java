package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.util.SseChatContext;
import jimmy.system.config.StandardColumnRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private static final Set<String> SYSTEM_SCHEMAS = Set.of("information_schema", "mysql", "performance_schema");

    /** 仍保留静态禁止列作为底层防线（与 RBAC 敏感标记互补） */
    private static final Set<String> STATIC_FORBIDDEN = Set.of(
            "password", "token", "secret", "api_key", "access_token", "refresh_token", "mobile_hash"
    );

    /**
     * 业务标识符白名单：这些字段名在某些模块可能被标记为敏感（如 sys_user 的 customer_name），
     * 但在业务表中是普通的 FK/业务标识。SQL 安全校验时不应拦截。
     */
    private static final Set<String> BUSINESS_IDENTIFIER_OVERRIDE = Set.of(
            "customer_id", "customer_name", "order_no", "order_id",
            "waybill_no", "waybill_id", "bill_no", "task_no", "dispatch_id",
            "driver_id", "driver_name", "vehicle_no", "vehicle_id",
            "warehouse_id", "warehouse_name", "route_id", "route_code",
            "role_id", "role_code", "role_name", "user_id", "user_code",
            "menu_id", "menu_name", "permission_id", "permission_code"
    );

    private final AiReadableSchemaRegistry schemaRegistry;
    private final Set<String> dynamicSensitiveFields;

    public AiSqlSafetyValidator() {
        this(new AiReadableSchemaRegistry(), null);
    }

    public AiSqlSafetyValidator(AiReadableSchemaRegistry schemaRegistry,
                                StandardColumnRegistry columnRegistry) {
        this.schemaRegistry = schemaRegistry;
        this.dynamicSensitiveFields = buildSensitiveFields(columnRegistry);
    }

    /**
     * 从 StandardColumnRegistry 动态构建敏感列名集合。
     */
    private static Set<String> buildSensitiveFields(StandardColumnRegistry columnRegistry) {
        if (columnRegistry == null) return Collections.emptySet();
        Set<String> fields = new LinkedHashSet<>();
        for (String module : columnRegistry.moduleNames()) {
            for (StandardColumnRegistry.ColumnDef col : columnRegistry.columns(module)) {
                if (col.sensitive()) {
                    fields.add(col.fieldName());
                }
            }
        }
        fields.addAll(STATIC_FORBIDDEN);
        return Collections.unmodifiableSet(fields);
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
        // 动态敏感列检测 + SELECT * 检测
        List<String> violatedColumns = findSensitiveColumns(sql);
        if (!violatedColumns.isEmpty() || SELECT_ALL_PATTERN.matcher(sql).find()) {
            if (!violatedColumns.isEmpty()) {
                throw new IllegalArgumentException(
                        "SQL包含敏感列，请修改后重试。敏感列：" + String.join("、", violatedColumns));
            }
            throw new IllegalArgumentException("查询语句包含不允许返回的敏感字段");
        }
        if (hasMultipleStatements(sql)) {
            throw new IllegalArgumentException("AI 只允许生成单条查询语句");
        }
        List<String> tables = extractTables(sql);
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("查询语句未识别到可校验的数据表");
        }
        for (String table : tables) {
            AiReadableSchemaRegistry.TableRule rule = schemaRegistry.findTable(table);
            if (rule == null) {
                throw new IllegalArgumentException("查询涉及未开放的数据表");
            }
            if (!hasPermission(rule.permission())) {
                throw new IllegalArgumentException("当前账号权限不足，无法查询该类数据。如有需要，请联系系统管理员。");
            }
        }
        return new ValidatedSql(sql, tables);
    }

    /**
     * 检测 SQL 中是否包含敏感列名。
     * 使用简单的词边界匹配：将 SQL 按非标识符字符分割，逐个检查。
     */
    private List<String> findSensitiveColumns(String sql) {
        if (dynamicSensitiveFields.isEmpty()) return List.of();
        Set<String> identifiers = extractIdentifiers(sql);
        List<String> violated = new ArrayList<>();
        for (String field : dynamicSensitiveFields) {
            if (identifiers.contains(field) && !BUSINESS_IDENTIFIER_OVERRIDE.contains(field)) {
                violated.add(field);
            }
        }
        return violated;
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
        String sseLoginId = SseChatContext.getLoginId();
        if (StringUtils.hasText(sseLoginId) && !"null".equalsIgnoreCase(sseLoginId)) {
            return SseChatContext.hasPermission(permission);
        }
        return StpUtil.hasPermission(permission);
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

    public record ValidatedSql(String sql, List<String> tables) {
    }
}
