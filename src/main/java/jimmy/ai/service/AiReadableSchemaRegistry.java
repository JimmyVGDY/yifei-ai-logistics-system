package jimmy.ai.service;

import jimmy.system.config.StandardColumnRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 临时只读 SQL 可读 Schema 注册表。
 * <p>
 * 业务表的列定义从 {@link StandardColumnRegistry} 组合而来，确保与 RBAC 列权限系统
 * 使用同一份列清单和敏感标记。AI 专属表和系统内部表保留硬编码列定义。
 * <p>
 * 所有允许 AI 查询的表、字段和权限要求都集中维护在这里。模型提示词、SQL 安全校验和后续测试
 * 都从同一份注册表读取。
 */
@Component
public class AiReadableSchemaRegistry {

    /**
     * 数据库表名 → StandardColumnRegistry 模块码映射。
     * 在此映射中的表，其列定义从 StandardColumnRegistry 动态获取。
     * <p>
     * 注意：多张表共享同一模块（如 resource 包含 warehouse/route/inventory）
     * 的表采用硬编码列定义，避免不同表列混淆。
     */
    private static final Map<String, String> TABLE_TO_MODULE = Map.ofEntries(
            Map.entry("logistics_customer", "customer"),
            Map.entry("logistics_order", "order"),
            Map.entry("logistics_waybill", "waybill"),
            Map.entry("logistics_dispatch", "dispatch"),
            Map.entry("logistics_task", "task"),
            Map.entry("logistics_track", "track"),
            Map.entry("logistics_driver", "driver"),
            Map.entry("logistics_vehicle", "vehicle"),
            Map.entry("logistics_exception", "exception"),
            Map.entry("sys_user", "system:user"),
            Map.entry("sys_role", "system:role"),
            Map.entry("sys_operation_log", "system:log"),
            Map.entry("sys_uploaded_file", "file")
            // logistics_fee, logistics_warehouse, logistics_route, logistics_inventory,
            // logistics_freight_bill 共享模块（多表同模块），使用硬编码列定义
    );

    /** 禁止通过自定义 SQL 直接查询的 AI 内部表 */
    private static final Set<String> RESTRICTED_AI_TABLES = Set.of(
            "ai_conversation", "ai_conversation_message", "ai_user_profile",
            "ai_user_memory", "ai_memory_event", "ai_query_cursor"
    );

    private final Map<String, TableRule> tableRules;

    public AiReadableSchemaRegistry() {
        this(null);
    }

    public AiReadableSchemaRegistry(StandardColumnRegistry columnRegistry) {
        this.tableRules = buildTableRules(columnRegistry);
    }

    public Map<String, TableRule> tableRules() {
        return tableRules;
    }

    public TableRule findTable(String tableName) {
        if (tableName == null) return null;
        return tableRules.get(tableName.toLowerCase());
    }

    public String schemaPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append("可查询表和字段如下，只能使用这些数据库真实字段，禁止写操作；带 deleted 字段的表默认追加 deleted = 0：\n");
        for (TableRule rule : tableRules.values()) {
            // 限制表不出现在模型提示词中，防止模型生成 SQL 直接查询
            if (RESTRICTED_AI_TABLES.contains(rule.tableName())) continue;
            builder.append("- ")
                    .append(rule.tableName())
                    .append("(")
                    .append(rule.columns())
                    .append(")\n");
        }
        return builder.toString();
    }

    private Map<String, TableRule> buildTableRules(StandardColumnRegistry columnRegistry) {
        Map<String, TableRule> rules = new LinkedHashMap<>();

        // ── 业务表：列定义来自 StandardColumnRegistry ──
        for (var entry : TABLE_TO_MODULE.entrySet()) {
            String tableName = entry.getKey();
            String moduleCode = entry.getValue();
            String permission = moduleCode + ":query";

            String columns;
            if (columnRegistry != null) {
                List<StandardColumnRegistry.ColumnDef> colDefs = columnRegistry.columns(moduleCode);
                List<String> fieldNames = colDefs.stream()
                        .map(StandardColumnRegistry.ColumnDef::fieldName)
                        .collect(Collectors.toCollection(ArrayList::new));
                // 追加系统字段（仅当模块未定义时，避免重复）
                for (String sysField : List.of("created_at", "updated_at", "create_time", "update_time", "deleted", "version")) {
                    if (!fieldNames.contains(sysField)) {
                        fieldNames.add(sysField);
                    }
                }
                columns = String.join(", ", fieldNames);
            } else {
                // Fallback：无 StandardColumnRegistry 时使用空列表
                columns = "";
            }
            rules.put(tableName, new TableRule(tableName, permission, columns));
        }

        // ── 共享模块表（多表同模块，StandardColumnRegistry 无法区分，硬编码列定义） ──
        rules.put("logistics_fee", table("logistics_fee", "fee:query",
                "id, order_id, base_fee, weight_fee, distance_fee, additional_fee, discount_fee, payable_fee, actual_fee, payment_status, create_time, update_time, deleted, version"));
        rules.put("logistics_freight_bill", table("logistics_freight_bill", "fee:query",
                "id, bill_no, order_no, base_amount, fuel_surcharge, discount_amount, payable_amount, pay_status, created_at, updated_at, deleted, version"));
        rules.put("logistics_warehouse", table("logistics_warehouse", "resource:query",
                "id, warehouse_code, warehouse_name, province, city, address, manager_name, contact_phone, capacity_cubic, status, created_at, updated_at, deleted, version"));
        rules.put("logistics_route", table("logistics_route", "resource:query",
                "id, route_code, origin_city, destination_city, distance_km, estimated_hours, status, created_at, updated_at, deleted, version"));
        rules.put("logistics_inventory", table("logistics_inventory", "resource:query",
                "id, warehouse_id, sku_code, sku_name, quantity, locked_quantity, updated_at, deleted, version"));

        // ── 系统内部表（硬编码列定义，AI 可查询但不在 StandardColumnRegistry 中） ──
        rules.put("sys_menu", table("sys_menu", "system:permission:query",
                "id, parent_id, menu_name, menu_path, permission_code, sort_no, status, create_time, update_time, deleted, version"));
        rules.put("sys_permission", table("sys_permission", "system:permission:query",
                "id, permission_code, permission_name, permission_type, module_code, action_code, menu_id, sensitive_flag, sort_no, status, create_time, update_time"));
        rules.put("sys_role_menu", table("sys_role_menu", "system:permission:query",
                "id, role_id, menu_id, deleted, version"));
        rules.put("sys_role_permission", table("sys_role_permission", "system:permission:query",
                "id, role_id, permission_id"));
        rules.put("sys_user_role", table("sys_user_role", "system:permission:query",
                "id, user_id, role_id, deleted, version"));
        rules.put("sys_user_permission", table("sys_user_permission", "system:permission:query",
                "id, user_id, permission_id, grant_type, create_time, update_time"));

        // ── AI 内部表（仅用于工具查询，不允许自定义 SQL 直查） ──
        rules.put("ai_conversation", table("ai_conversation", "ai:conversation:query",
                "id, conversation_id, user_id, user_code, title, status, message_count, last_message_at, archived_at, created_at, updated_at, deleted"));
        rules.put("ai_conversation_message", table("ai_conversation_message", "ai:conversation:query",
                "id, message_id, conversation_id, user_id, user_code, role, status, trace_id, operation_id, login_session_id, created_at, updated_at, deleted"));
        rules.put("ai_user_profile", table("ai_user_profile", "ai:conversation:query",
                "id, user_id, user_code, memory_enabled, answer_style, favorite_modules, last_recall_time, created_at, updated_at, deleted"));
        rules.put("ai_user_memory", table("ai_user_memory", "ai:log:analyze",
                "id, user_id, user_code, memory_type, memory_title, confidence, source_conversation_id, source_trace_id, recall_count, reinforce_count, last_reinforced_at, status, last_recall_time, created_at, updated_at, deleted"));
        rules.put("ai_memory_event", table("ai_memory_event", "ai:log:analyze",
                "id, memory_id, event_type, event_source, user_id, user_code, trace_id, operation_id, login_session_id, ai_conversation_id, created_at"));
        rules.put("ai_query_cursor", table("ai_query_cursor", "ai:conversation:query",
                "id, cursor_id, conversation_id, user_id, user_code, tool_type, tool_name, module_code, module_name, keyword, start_time, end_time, status_filter, page, page_size, total, returned_count, query_summary, expires_at, created_at, updated_at, deleted"));

        // sys_login_history 已移除——安全风险，不允许通过 AI 查询登录历史
        // logistics_order_tracking 同 track 模块，合并到 logistics_track

        return Collections.unmodifiableMap(rules);
    }

    private TableRule table(String tableName, String permission, String columns) {
        return new TableRule(tableName, permission, columns);
    }

    public record TableRule(String tableName, String permission, String columns) {
    }
}
