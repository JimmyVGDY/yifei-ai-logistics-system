package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.util.SseChatContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final Pattern SENSITIVE_COLUMN_PATTERN = Pattern.compile("(?i)\\b(password|token|secret|api_key|access_token|refresh_token)\\b");
    private static final Pattern SELECT_ALL_PATTERN = Pattern.compile("(?i)(^|\\s|,)\\*\\s*(,|from)\\b|\\b\\w+\\.\\*\\b");
    private static final Set<String> SYSTEM_SCHEMAS = Set.of("information_schema", "mysql", "performance_schema");

    private final Map<String, TableRule> tableRules = buildTableRules();

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
            TableRule rule = tableRules.get(table);
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
        StringBuilder builder = new StringBuilder();
        // 暴露给模型的是精简后的业务 schema，不包含密码、token 等敏感字段。
        builder.append("可查询表和字段如下，只能使用这些表字段，禁止写操作：\n");
        for (TableRule rule : tableRules.values()) {
            builder.append("- ").append(rule.tableName()).append("(").append(rule.columns()).append(")\n");
        }
        return builder.toString();
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

    private Map<String, TableRule> buildTableRules() {
        Map<String, TableRule> rules = new LinkedHashMap<>();
        rules.put("logistics_customer", new TableRule("logistics_customer", "customer:query", "id, customer_code, customer_name, contact_name, contact_phone, province, city, address, status, created_at, updated_at"));
        rules.put("logistics_order", new TableRule("logistics_order", "order:query", "id, order_no, customer_id, customer_name, sender_address, receiver_address, cargo_name, cargo_weight, status, created_at, updated_at"));
        rules.put("logistics_waybill", new TableRule("logistics_waybill", "waybill:query", "id, waybill_no, order_id, order_no, start_site, target_site, current_location, transport_status, create_time, update_time"));
        rules.put("logistics_dispatch", new TableRule("logistics_dispatch", "dispatch:query", "id, order_id, order_no, waybill_id, waybill_no, driver_id, driver_name, vehicle_id, vehicle_no, dispatch_status, create_time, update_time"));
        rules.put("logistics_task", new TableRule("logistics_task", "task:query", "id, task_no, order_id, order_no, driver_name, vehicle_no, task_status, create_time, update_time"));
        rules.put("logistics_track", new TableRule("logistics_track", "track:query", "id, order_id, order_no, waybill_id, waybill_no, current_status, current_location, operator_name, operation_desc, operation_time"));
        rules.put("logistics_driver", new TableRule("logistics_driver", "driver:query", "id, driver_code, driver_name, phone, license_no, license_type, status, created_at, updated_at"));
        rules.put("logistics_vehicle", new TableRule("logistics_vehicle", "vehicle:query", "id, vehicle_no, vehicle_type, load_capacity, volume_capacity, current_city, status, created_at, updated_at"));
        rules.put("logistics_exception", new TableRule("logistics_exception", "exception:query", "id, order_id, order_no, task_id, exception_type, exception_desc, exception_status, report_user, report_time, handle_user, handle_time"));
        rules.put("logistics_fee", new TableRule("logistics_fee", "fee:query", "id, order_id, order_no, transport_fee, service_fee, other_fee, total_fee, payment_status, create_time, update_time"));
        rules.put("sys_user", new TableRule("sys_user", "system:user:query", "id, user_code, username, real_name, mobile, email, role_id, role_name, customer_id, status, create_time, update_time"));
        rules.put("sys_role", new TableRule("sys_role", "system:role:query", "id, role_code, role_name, status, create_time, update_time"));
        rules.put("sys_operation_log", new TableRule("sys_operation_log", "system:log:query", "id, operation_id, trace_id, login_session_id, user_id, user_code, username, role_code, operation, request_uri, request_method, operation_status, cost_ms, operation_time"));
        rules.put("sys_uploaded_file", new TableRule("sys_uploaded_file", "file:query", "id, original_name, relative_path, content_type, file_size, upload_user, upload_time"));
        return rules;
    }

    public record ValidatedSql(String sql, List<String> tables) {
    }

    private record TableRule(String tableName, String permission, String columns) {
    }
}
