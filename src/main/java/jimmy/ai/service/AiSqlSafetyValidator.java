package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.util.SseChatContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 生成 SQL 安全校验器：只放行单条 SELECT，并基于表白名单做权限校验。
 */
@Component
public class AiSqlSafetyValidator {

    private static final Pattern TABLE_PATTERN = Pattern.compile("(?i)\\b(?:from|join)\\s+([`\\w.]+)");
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile("(?i)\\b(insert|update|delete|drop|alter|create|truncate|replace|merge|grant|revoke|call|execute|load_file|outfile|infile)\\b");
    private static final Pattern SENSITIVE_COLUMN_PATTERN = Pattern.compile("(?i)\\b(password|token|secret|api_key|access_token|refresh_token|mobile_hash|content|request_params|change_summary|error_message|login_ip|client_ip|user_agent|fail_reason)\\b");
    private static final Pattern SELECT_ALL_PATTERN = Pattern.compile("(?i)(^|\\s|,)\\*\\s*(,|from)\\b|\\b\\w+\\.\\*\\b");
    private static final Set<String> SYSTEM_SCHEMAS = Set.of("information_schema", "mysql", "performance_schema");

    private final AiReadableSchemaRegistry schemaRegistry;

    public AiSqlSafetyValidator() {
        this(new AiReadableSchemaRegistry());
    }

    public AiSqlSafetyValidator(AiReadableSchemaRegistry schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
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
        if (SENSITIVE_COLUMN_PATTERN.matcher(sql).find() || SELECT_ALL_PATTERN.matcher(sql).find()) {
            throw new IllegalArgumentException("查询语句包含不允许返回的敏感字段");
        }
        if (hasMultipleStatements(sql)) {
            throw new IllegalArgumentException("AI 只允许生成单条查询语句");
        }
        // 只校验 FROM/JOIN 中出现的表，且每张表都必须在白名单内并绑定当前账号查询权限。
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
     * SSE 流式问答运行在异步线程，Sa-Token ThreadLocal 不可用时使用 Controller 预捕获的权限快照。
     */
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
        // 兼容模型偶尔返回 ```sql 代码块的情况，提取内部 SQL 后继续做统一校验。
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

    /**
     * 检查 SQL 中是否包含多条语句。
     * <p>
     * 只检查不在单引号字符串内的分号，避免字段值中的分号被误判。
     * 例如 {@code WHERE remark = '已签收;待支付'} 中的分号不会被误判为多语句分隔符。
     */
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
