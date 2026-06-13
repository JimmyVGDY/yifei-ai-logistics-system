package jimmy.ai.service;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 临时只读 SQL 可读 Schema 注册表。
 * <p>
 * 所有允许 AI 查询的表、字段和权限要求都集中维护在这里。模型提示词、SQL 安全校验和后续测试
 * 都从同一份注册表读取，避免“提示词说能查、后端却拦截”或“字段新增后漏同步”的问题。
 */
@Component
public class AiReadableSchemaRegistry {

    private final Map<String, TableRule> tableRules = buildTableRules();

    public Map<String, TableRule> tableRules() {
        return tableRules;
    }

    public TableRule findTable(String tableName) {
        return tableRules.get(tableName);
    }

    public String schemaPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append("可查询表和字段如下，只能使用这些数据库真实字段，禁止写操作；带 deleted 字段的表默认追加 deleted = 0：\n");
        for (TableRule rule : tableRules.values()) {
            builder.append("- ")
                    .append(rule.tableName())
                    .append("(")
                    .append(rule.columns())
                    .append(")\n");
        }
        return builder.toString();
    }

    private Map<String, TableRule> buildTableRules() {
        Map<String, TableRule> rules = new LinkedHashMap<>();
        rules.put("logistics_customer", table("logistics_customer", "customer:query", "id, customer_code, customer_name, contact_name, contact_phone, province, city, address, status, created_at, updated_at, deleted, version"));
        rules.put("logistics_warehouse", table("logistics_warehouse", "resource:query", "id, warehouse_code, warehouse_name, province, city, address, manager_name, contact_phone, capacity_cubic, status, created_at, updated_at, deleted, version"));
        rules.put("logistics_driver", table("logistics_driver", "driver:query", "id, driver_code, driver_name, phone, license_no, license_type, status, created_at, updated_at, deleted, version"));
        rules.put("logistics_vehicle", table("logistics_vehicle", "vehicle:query", "id, vehicle_no, vehicle_type, load_capacity_kg, volume_capacity_cubic, current_city, status, created_at, updated_at, deleted, version"));
        rules.put("logistics_route", table("logistics_route", "resource:query", "id, route_code, origin_city, destination_city, distance_km, estimated_hours, status, created_at, updated_at, deleted, version"));
        rules.put("logistics_order", table("logistics_order", "order:query", "id, order_no, customer_id, route_id, warehouse_id, vehicle_id, driver_id, customer_name, sender_address, receiver_address, cargo_name, cargo_weight, cargo_volume, status, planned_pickup_time, planned_delivery_time, created_at, updated_at, deleted, version"));
        rules.put("logistics_waybill", table("logistics_waybill", "waybill:query", "id, waybill_no, order_id, start_site, target_site, current_location, transport_status, create_time, update_time, deleted, version"));
        rules.put("logistics_dispatch", table("logistics_dispatch", "dispatch:query", "id, order_id, waybill_id, driver_id, vehicle_id, start_site, target_site, planned_departure_time, planned_arrival_time, dispatch_status, create_time, update_time, deleted, version"));
        rules.put("logistics_task", table("logistics_task", "task:query", "id, task_no, order_id, waybill_id, dispatch_id, driver_id, vehicle_id, task_status, proof_url, create_time, update_time, deleted, version"));
        rules.put("logistics_track", table("logistics_track", "track:query", "id, order_id, waybill_id, current_status, current_location, operator_name, operation_desc, operation_time, deleted, version"));
        rules.put("logistics_exception", table("logistics_exception", "exception:query", "id, order_id, task_id, exception_type, exception_desc, exception_status, report_user, report_time, handle_user, handle_time, deleted, version"));
        rules.put("logistics_fee", table("logistics_fee", "fee:query", "id, order_id, base_fee, weight_fee, distance_fee, additional_fee, discount_fee, payable_fee, actual_fee, payment_status, create_time, update_time, deleted, version"));
        rules.put("logistics_order_tracking", table("logistics_order_tracking", "track:query", "id, order_no, tracking_status, location, description, operator_name, occurred_at, created_at, deleted, version"));
        rules.put("logistics_inventory", table("logistics_inventory", "resource:query", "id, warehouse_id, sku_code, sku_name, quantity, locked_quantity, updated_at, deleted, version"));
        rules.put("logistics_freight_bill", table("logistics_freight_bill", "fee:query", "id, bill_no, order_no, base_amount, fuel_surcharge, discount_amount, payable_amount, pay_status, created_at, updated_at, deleted, version"));
        rules.put("sys_user", table("sys_user", "system:user:query", "id, user_code, username, real_name, mobile, email, role_id, customer_id, customer_subject_type, customer_account_type, status, create_time, update_time, deleted, version"));
        rules.put("sys_role", table("sys_role", "system:role:query", "id, role_code, role_name, status, create_time, update_time, deleted, version"));
        rules.put("sys_menu", table("sys_menu", "system:permission:query", "id, parent_id, menu_name, menu_path, permission_code, sort_no, status, create_time, update_time, deleted, version"));
        rules.put("sys_permission", table("sys_permission", "system:permission:query", "id, permission_code, permission_name, permission_type, module_code, action_code, menu_id, sort_no, status, create_time, update_time"));
        rules.put("sys_role_menu", table("sys_role_menu", "system:permission:query", "id, role_id, menu_id, deleted, version"));
        rules.put("sys_role_permission", table("sys_role_permission", "system:permission:query", "id, role_id, permission_id"));
        rules.put("sys_user_role", table("sys_user_role", "system:permission:query", "id, user_id, role_id, deleted, version"));
        rules.put("sys_user_permission", table("sys_user_permission", "system:permission:query", "id, user_id, permission_id, grant_type, create_time, update_time"));
        rules.put("sys_operation_log", table("sys_operation_log", "system:log:query", "id, operation_id, trace_id, login_session_id, user_id, user_code, username, role_code, operation, request_uri, request_method, operation_status, cost_ms, operation_source, executor_type, ai_conversation_id, ai_message_id, ai_tool_name, ai_tool_target, ai_readonly, ai_prompt_summary, ai_result_summary, ai_memory_id, ai_memory_event_type, ai_memory_source, ai_memory_hit_count, ai_memory_trace_summary, operation_time, deleted, version"));
        rules.put("sys_uploaded_file", table("sys_uploaded_file", "file:query", "id, original_name, stored_name, relative_path, content_type, file_size, upload_user, upload_time, deleted, version"));
        rules.put("sys_login_history", table("sys_login_history", "system:log:query", "id, user_id, username, login_result, require_captcha, login_time"));
        rules.put("ai_conversation", table("ai_conversation", "ai:log:analyze", "id, conversation_id, user_id, user_code, title, status, message_count, last_message_at, archived_at, deleted_at, created_at, updated_at, deleted"));
        rules.put("ai_conversation_message", table("ai_conversation_message", "ai:log:analyze", "id, message_id, conversation_id, user_id, user_code, role, status, trace_id, operation_id, login_session_id, created_at, updated_at, deleted"));
        rules.put("ai_user_profile", table("ai_user_profile", "ai:log:analyze", "id, user_id, user_code, memory_enabled, answer_style, favorite_modules, last_recall_time, created_at, updated_at, deleted"));
        rules.put("ai_user_memory", table("ai_user_memory", "ai:log:analyze", "id, user_id, user_code, memory_type, memory_title, confidence, qdrant_point_id, source_conversation_id, source_trace_id, recall_count, reinforce_count, last_reinforced_at, status, last_recall_time, created_at, updated_at, deleted"));
        rules.put("ai_memory_event", table("ai_memory_event", "ai:log:analyze", "id, memory_id, event_type, event_source, user_id, user_code, trace_id, operation_id, login_session_id, ai_conversation_id, created_at"));
        rules.put("ai_query_cursor", table("ai_query_cursor", "ai:conversation:query", "id, cursor_id, conversation_id, user_id, user_code, tool_type, tool_name, module_code, module_name, keyword, start_time, end_time, status_filter, page, page_size, total, returned_count, query_summary, expires_at, created_at, updated_at, deleted"));
        return Collections.unmodifiableMap(rules);
    }

    private TableRule table(String tableName, String permission, String columns) {
        return new TableRule(tableName, permission, columns);
    }

    public record TableRule(String tableName, String permission, String columns) {
    }
}
