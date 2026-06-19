package jimmy.ai.service;

import jimmy.system.config.StandardColumnRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 临时只读 SQL 可读 Schema 注册表。
 * <p>
 * 临时 SQL 必须使用数据库真实字段，不能直接复用前端/API 展示列。
 * 例如运单中心接口会返回 order_no，但真实表 logistics_waybill 只有 order_id，
 * 如果把展示列暴露给模型，模型会生成无法执行的 SQL。
 * <p>
 * 所有允许 AI 查询的表、字段和权限要求都集中维护在这里。模型提示词、SQL 安全校验和后续测试
 * 都从同一份注册表读取。
 */
@Component
public class AiReadableSchemaRegistry {

    /**
     * 数据库表名 → 权限模块码映射。
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

    /**
     * AI 临时 SQL 可见的数据库真实字段。
     * <p>
     * 这里故意不从 StandardColumnRegistry 读取，因为后者描述的是接口返回字段，
     * 可能包含 JOIN 后的展示列或前端虚拟列，不一定是表内真实字段。
     */
    private static final Map<String, String> REAL_TABLE_COLUMNS = Map.ofEntries(
            Map.entry("logistics_customer", "id, customer_code, customer_name, contact_name, contact_phone, province, city, address, status, created_at, updated_at, create_by, update_by, deleted, version"),
            Map.entry("logistics_order", "id, order_no, customer_id, route_id, warehouse_id, vehicle_id, driver_id, customer_name, sender_address, receiver_address, cargo_name, cargo_weight, cargo_volume, status, planned_pickup_time, planned_delivery_time, created_at, updated_at, create_by, update_by, deleted, version"),
            Map.entry("logistics_waybill", "id, waybill_no, order_id, start_site, target_site, current_location, transport_status, create_time, update_time, create_by, update_by, deleted, version"),
            Map.entry("logistics_dispatch", "id, order_id, waybill_id, driver_id, vehicle_id, start_site, target_site, planned_departure_time, planned_arrival_time, dispatch_status, create_time, update_time, create_by, update_by, deleted, version"),
            Map.entry("logistics_task", "id, task_no, order_id, waybill_id, dispatch_id, driver_id, vehicle_id, task_status, proof_url, create_time, update_time, create_by, update_by, deleted, version"),
            Map.entry("logistics_track", "id, order_id, waybill_id, current_status, current_location, operator_name, operation_desc, operation_time, create_by, update_by, deleted, version"),
            Map.entry("logistics_driver", "id, driver_code, driver_name, phone, license_no, license_type, status, created_at, updated_at, create_by, update_by, deleted, version"),
            Map.entry("logistics_vehicle", "id, vehicle_no, vehicle_type, load_capacity_kg, volume_capacity_cubic, current_city, status, created_at, updated_at, create_by, update_by, deleted, version"),
            Map.entry("logistics_exception", "id, order_id, task_id, exception_type, exception_desc, exception_status, report_user, report_time, handle_user, handle_time, create_by, update_by, deleted, version"),
            Map.entry("sys_user", "id, username, real_name, mobile, email, role_id, status, create_time, update_time, create_by, update_by, deleted, version, user_code, customer_id, customer_subject_type, customer_account_type"),
            Map.entry("sys_role", "id, role_code, role_name, status, create_time, update_time, create_by, update_by, deleted, version"),
            Map.entry("sys_operation_log", "id, username, operation, request_uri, request_method, operation_status, operation_time, create_by, update_by, deleted, version, operation_id, trace_id, login_session_id, user_id, user_code, role_code, cost_ms, error_message, client_ip, user_agent, request_params, target_id, change_summary, operation_source, executor_type, ai_conversation_id, ai_message_id, ai_tool_name, ai_tool_target, ai_readonly, ai_prompt_summary, ai_result_summary, ai_memory_id, ai_memory_event_type, ai_memory_source, ai_memory_hit_count, ai_memory_trace_summary"),
            Map.entry("sys_uploaded_file", "id, original_name, stored_name, relative_path, file_size, content_type, upload_user, upload_time, create_by, update_by, deleted, version")
    );

    private final Map<String, TableRule> tableRules;

    public AiReadableSchemaRegistry() {
        // 兼容单元测试或手工 new 的场景：即使没有 Spring 注入，也必须给 AI 提供真实业务字段。
        this(new StandardColumnRegistry());
    }

    @Autowired
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
            // 工具内部表不出现在模型提示词中，真正的硬拦截在 AiSqlSafetyValidator 中做。
            if (!rule.generatedSqlAllowed()) continue;
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

        // ── 业务表：列定义必须使用数据库真实字段 ──
        for (var entry : TABLE_TO_MODULE.entrySet()) {
            String tableName = entry.getKey();
            String moduleCode = entry.getValue();
            String permission = moduleCode + ":query";
            String columns = REAL_TABLE_COLUMNS.getOrDefault(tableName, "");
            rules.put(tableName, new TableRule(tableName, moduleCode, permission, columns, true));
        }

        // ── 共享模块表（多表同模块，StandardColumnRegistry 无法区分，硬编码列定义） ──
        rules.put("logistics_fee", table("logistics_fee", "fee", "fee:query",
                "id, order_id, base_fee, weight_fee, distance_fee, additional_fee, discount_fee, payable_fee, actual_fee, payment_status, create_time, update_time, deleted, version"));
        rules.put("logistics_freight_bill", table("logistics_freight_bill", "fee", "fee:query",
                "id, bill_no, order_no, base_amount, fuel_surcharge, discount_amount, payable_amount, pay_status, created_at, updated_at, deleted, version"));
        rules.put("logistics_warehouse", table("logistics_warehouse", "resource", "resource:query",
                "id, warehouse_code, warehouse_name, province, city, address, manager_name, contact_phone, capacity_cubic, status, created_at, updated_at, deleted, version"));
        rules.put("logistics_route", table("logistics_route", "resource", "resource:query",
                "id, route_code, origin_city, destination_city, distance_km, estimated_hours, status, created_at, updated_at, deleted, version"));
        rules.put("logistics_inventory", table("logistics_inventory", "resource", "resource:query",
                "id, warehouse_id, sku_code, sku_name, quantity, locked_quantity, updated_at, deleted, version"));

        // ── 系统内部表（硬编码列定义，AI 可查询但不在 StandardColumnRegistry 中） ──
        rules.put("sys_menu", table("sys_menu", "system:permission", "system:permission:query",
                "id, parent_id, menu_name, menu_path, permission_code, sort_no, status, create_time, update_time, deleted, version"));
        rules.put("sys_permission", table("sys_permission", "system:permission", "system:permission:query",
                "id, permission_code, permission_name, permission_type, module_code, action_code, menu_id, sensitive_flag, sort_no, status, create_time, update_time"));
        rules.put("sys_role_menu", table("sys_role_menu", "system:permission", "system:permission:query",
                "id, role_id, menu_id, deleted, version"));
        rules.put("sys_role_permission", table("sys_role_permission", "system:permission", "system:permission:query",
                "id, role_id, permission_id"));
        rules.put("sys_user_role", table("sys_user_role", "system:permission", "system:permission:query",
                "id, user_id, role_id, deleted, version"));
        rules.put("sys_user_permission", table("sys_user_permission", "system:permission", "system:permission:query",
                "id, user_id, permission_id, grant_type, create_time, update_time"));

        // ── AI 内部表（仅用于后端工具查询，不允许模型自定义 SQL 直查） ──
        rules.put("ai_conversation", internalTable("ai_conversation", "ai:conversation", "ai:conversation:query",
                "id, conversation_id, user_id, user_code, title, status, message_count, last_message_at, archived_at, created_at, updated_at, deleted"));
        rules.put("ai_conversation_message", internalTable("ai_conversation_message", "ai:conversation", "ai:conversation:query",
                "id, message_id, conversation_id, user_id, user_code, role, status, trace_id, operation_id, login_session_id, created_at, updated_at, deleted"));
        rules.put("ai_user_profile", internalTable("ai_user_profile", "ai:conversation", "ai:conversation:query",
                "id, user_id, user_code, memory_enabled, answer_style, favorite_modules, last_recall_time, created_at, updated_at, deleted"));
        rules.put("ai_user_memory", internalTable("ai_user_memory", "ai:memory", "ai:log:analyze",
                "id, user_id, user_code, memory_type, memory_title, confidence, source_conversation_id, source_trace_id, recall_count, reinforce_count, last_reinforced_at, status, last_recall_time, created_at, updated_at, deleted"));
        rules.put("ai_memory_event", internalTable("ai_memory_event", "ai:memory", "ai:log:analyze",
                "id, memory_id, event_type, event_source, user_id, user_code, trace_id, operation_id, login_session_id, ai_conversation_id, created_at"));
        rules.put("ai_query_cursor", internalTable("ai_query_cursor", "ai:conversation", "ai:conversation:query",
                "id, cursor_id, conversation_id, user_id, user_code, tool_type, tool_name, module_code, module_name, keyword, start_time, end_time, status_filter, page, page_size, total, returned_count, query_summary, expires_at, created_at, updated_at, deleted"));

        // sys_login_history 已移除——安全风险，不允许通过 AI 查询登录历史
        // logistics_order_tracking 同 track 模块，合并到 logistics_track

        return Collections.unmodifiableMap(rules);
    }

    private TableRule table(String tableName, String moduleCode, String permission, String columns) {
        return new TableRule(tableName, moduleCode, permission, columns, true);
    }

    private TableRule internalTable(String tableName, String moduleCode, String permission, String columns) {
        return new TableRule(tableName, moduleCode, permission, columns, false);
    }

    public record TableRule(String tableName,
                            String moduleCode,
                            String permission,
                            String columns,
                            boolean generatedSqlAllowed) {

        public Set<String> columnSet() {
            if (columns == null || columns.isBlank()) {
                return Set.of();
            }
            return Arrays.stream(columns.split(","))
                    .map(String::trim)
                    .filter(column -> !column.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }
}
