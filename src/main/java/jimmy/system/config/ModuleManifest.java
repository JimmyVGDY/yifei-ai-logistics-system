package jimmy.system.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模块定义统一清单 —— 后端唯一的模块元数据源。
 * <p>
 * 合并了原本散落在 3 个位置的模块定义：
 * <ol>
 *   <li>{@link StandardColumnRegistry} — 列定义（名称、中文标签、敏感标记）</li>
 *   <li>{@code CrudConfigRegistry} — CRUD 表名、时间列、可写字段</li>
 *   <li>{@code SaTokenConfig.MODULE_PERMISSION_PREFIXES} — 模块路径→权限前缀</li>
 * </ol>
 * <p>
 * 新增模块时只需在本类中添加一个 {@link ModuleEntry}，所有下游消费者从同一数据源读取。
 */
public final class ModuleManifest {

    private ModuleManifest() {}

    // ==================== 模块入口 ====================

    public static List<ModuleEntry> all() {
        return List.copyOf(REGISTRY.values());
    }

    public static ModuleEntry get(String moduleCode) {
        return REGISTRY.get(moduleCode);
    }

    public static Set<String> moduleCodes() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

    /**
     * 前端路由模块名（复数）→ 权限前缀的映射。
     */
    public static Map<String, String> routeModuleToPermissionPrefix() {
        Map<String, String> map = new LinkedHashMap<>();
        for (var entry : REGISTRY.entrySet()) {
            ModuleEntry m = entry.getValue();
            map.put(m.frontendModule(), entry.getKey());
        }
        return Collections.unmodifiableMap(map);
    }

    // ==================== 数据对象 ====================

    public record ColumnDef(String fieldName, String label, boolean sensitive) {}

    public record ModuleEntry(
            String moduleCode,       // 权限模块码，如 "order"
            String frontendModule,   // 前端路由模块名，如 "orders"
            String displayName,      // 中文名称，如 "运单管理"
            String tableName,        // 数据库表名
            String createTimeColumn, // 创建时间列（null 表示无）
            String updateTimeColumn, // 更新时间列（null 表示无）
            List<ColumnDef> columns  // 全部列定义
    ) {
        /** 所有列名列表（snake_case） */
        public List<String> columnNames() {
            return columns.stream().map(ColumnDef::fieldName).toList();
        }

        /** 非敏感列 */
        public List<ColumnDef> nonSensitiveColumns() {
            return columns.stream().filter(c -> !c.sensitive()).toList();
        }

        /** 权限前缀（与 moduleCode 相同，提供语义别名） */
        public String permissionPrefix() {
            return moduleCode;
        }
    }

    // ==================== 16 模块定义 ====================

    private static final Map<String, ModuleEntry> REGISTRY = new LinkedHashMap<>();

    static {
        register("order", "orders", "运单管理", "logistics_order", "created_at", "updated_at", List.of(
                col("id", "ID"),
                col("order_no", "订单号"),
                col("customer_id", "客户ID"),
                col("customer_name", "客户名称"),
                col("route_id", "路线ID"),
                col("warehouse_id", "仓库ID"),
                col("vehicle_id", "车辆ID"),
                col("driver_id", "司机ID"),
                col("sender_address", "发货地址"),
                col("receiver_address", "收货地址"),
                col("cargo_name", "货物名称"),
                col("cargo_weight", "货物重量"),
                col("cargo_volume", "货物体积"),
                col("status", "状态"),
                col("planned_pickup_time", "计划取件时间"),
                col("planned_delivery_time", "计划送达时间"),
                col("created_at", "创建时间"),
                col("updated_at", "更新时间")
        ));

        register("customer", "customers", "客户管理", "logistics_customer", "created_at", "updated_at", List.of(
                col("id", "ID"),
                col("customer_code", "客户编号"),
                col("customer_name", "客户名称"),
                col("contact_name", "联系人"),
                col("contact_phone", "联系电话", true),
                col("province", "省份"),
                col("city", "城市"),
                col("address", "地址"),
                col("status", "状态"),
                col("created_at", "创建时间"),
                col("updated_at", "更新时间")
        ));

        register("waybill", "waybills", "运单中心", "logistics_waybill", "create_time", "update_time", List.of(
                col("id", "ID"),
                col("waybill_no", "运单号"),
                col("order_id", "订单ID"),
                col("order_no", "订单号"),
                col("start_site", "始发网点"),
                col("target_site", "目的网点"),
                col("current_location", "当前位置"),
                col("transport_status", "运输状态"),
                col("create_time", "创建时间"),
                col("update_time", "更新时间")
        ));

        register("dispatch", "dispatches", "调度管理", "logistics_dispatch", "create_time", "update_time", List.of(
                col("id", "ID"),
                col("order_id", "订单ID"),
                col("order_no", "订单号"),
                col("waybill_id", "运单ID"),
                col("driver_id", "司机ID"),
                col("driver_name", "司机"),
                col("vehicle_id", "车辆ID"),
                col("vehicle_no", "车辆"),
                col("start_site", "始发网点"),
                col("target_site", "目的网点"),
                col("dispatch_status", "调度状态"),
                col("planned_departure_time", "计划出发时间"),
                col("planned_arrival_time", "计划到达时间"),
                col("create_time", "创建时间"),
                col("update_time", "更新时间")
        ));

        register("task", "tasks", "运输任务", "logistics_task", "create_time", "update_time", List.of(
                col("id", "ID"),
                col("task_no", "任务号"),
                col("order_id", "订单ID"),
                col("order_no", "订单号"),
                col("waybill_id", "运单ID"),
                col("dispatch_id", "调度ID"),
                col("driver_id", "司机ID"),
                col("driver_name", "司机"),
                col("vehicle_id", "车辆ID"),
                col("vehicle_no", "车辆"),
                col("task_status", "任务状态"),
                col("proof_url", "签收凭证", true),
                col("create_time", "创建时间"),
                col("update_time", "更新时间")
        ));

        register("track", "tracks", "物流轨迹", "logistics_track", null, null, List.of(
                col("id", "ID"),
                col("order_id", "订单ID"),
                col("order_no", "订单号"),
                col("waybill_id", "运单ID"),
                col("waybill_no", "运单号"),
                col("current_status", "当前状态"),
                col("current_location", "当前位置"),
                col("operator_name", "操作人"),
                col("operation_desc", "操作说明"),
                col("operation_time", "操作时间")
        ));

        register("driver", "drivers", "司机管理", "logistics_driver", "created_at", "updated_at", List.of(
                col("id", "ID"),
                col("driver_code", "司机编号"),
                col("driver_name", "司机姓名"),
                col("phone", "手机号", true),
                col("license_no", "驾驶证号", true),
                col("license_type", "准驾车型"),
                col("status", "状态"),
                col("created_at", "创建时间"),
                col("updated_at", "更新时间")
        ));

        register("vehicle", "vehicles", "车辆管理", "logistics_vehicle", "created_at", "updated_at", List.of(
                col("id", "ID"),
                col("vehicle_no", "车牌号"),
                col("vehicle_type", "车辆类型"),
                col("load_capacity_kg", "载重"),
                col("volume_capacity_cubic", "容积"),
                col("current_city", "当前城市"),
                col("status", "状态"),
                col("created_at", "创建时间"),
                col("updated_at", "更新时间")
        ));

        register("exception", "exceptions", "异常管理", "logistics_exception", "report_time", "handle_time", List.of(
                col("id", "ID"),
                col("order_id", "订单ID"),
                col("order_no", "订单号"),
                col("task_id", "任务ID"),
                col("exception_type", "异常类型"),
                col("exception_desc", "异常描述"),
                col("exception_status", "异常状态"),
                col("report_user", "上报人"),
                col("report_time", "上报时间"),
                col("handle_user", "处理人"),
                col("handle_time", "处理时间")
        ));

        register("fee", "fees", "费用结算", "logistics_fee", "create_time", "update_time", List.of(
                col("id", "ID"),
                col("order_id", "订单ID"),
                col("order_no", "订单号"),
                col("base_fee", "基础运费", true),
                col("weight_fee", "重量费用", true),
                col("distance_fee", "距离费用", true),
                col("additional_fee", "附加费", true),
                col("discount_fee", "优惠金额", true),
                col("payable_fee", "应收金额", true),
                col("actual_fee", "实收金额", true),
                col("payment_status", "付款状态"),
                col("create_time", "创建时间"),
                col("update_time", "更新时间")
        ));

        register("system:user", "users", "用户管理", "sys_user", "create_time", "update_time", List.of(
                col("id", "ID"),
                col("user_code", "用户编号"),
                col("username", "登录账号"),
                col("real_name", "姓名"),
                col("mobile", "手机号", true),
                col("email", "邮箱", true),
                col("role_id", "角色ID"),
                col("role_name", "角色"),
                col("customer_id", "关联客户ID", true),
                col("customer_name", "关联客户", true),
                col("customer_subject_type", "客户主体类型"),
                col("customer_account_type", "客户账号类型"),
                col("status", "状态"),
                col("create_time", "创建时间"),
                col("update_time", "更新时间")
        ));

        register("system:role", "roles", "角色管理", "sys_role", "create_time", "update_time", List.of(
                col("id", "ID"),
                col("role_code", "角色编码"),
                col("role_name", "角色名称"),
                col("status", "状态"),
                col("create_time", "创建时间"),
                col("update_time", "更新时间")
        ));

        register("system:log", "operationLogs", "操作日志", "sys_operation_log", null, null, List.of(
                col("id", "ID"),
                col("operation_id", "操作ID"),
                col("trace_id", "Trace ID"),
                col("login_session_id", "会话ID"),
                col("user_id", "用户主键"),
                col("user_code", "用户编号"),
                col("username", "操作人"),
                col("role_code", "角色编号"),
                col("operation", "操作内容"),
                col("request_uri", "请求地址"),
                col("request_method", "方法"),
                col("operation_status", "状态"),
                col("cost_ms", "耗时ms"),
                col("error_message", "异常信息", true),
                col("client_ip", "客户端IP", true),
                col("user_agent", "User Agent", true),
                col("request_params", "参数摘要", true),
                col("target_id", "对象ID"),
                col("change_summary", "变更摘要", true),
                col("operation_source", "操作来源"),
                col("executor_type", "执行者"),
                col("ai_conversation_id", "AI会话ID"),
                col("ai_message_id", "AI消息ID"),
                col("ai_tool_name", "AI工具"),
                col("ai_tool_target", "AI目标"),
                col("ai_readonly", "是否只读"),
                col("ai_prompt_summary", "AI问题摘要"),
                col("ai_result_summary", "AI结果摘要"),
                col("ai_memory_id", "AI记忆ID"),
                col("ai_memory_event_type", "记忆事件"),
                col("ai_memory_source", "记忆来源"),
                col("ai_memory_hit_count", "记忆命中数"),
                col("ai_memory_trace_summary", "记忆链路摘要"),
                col("operation_time", "操作时间")
        ));

        register("file", "files", "上传文件", "sys_uploaded_file", null, null, List.of(
                col("id", "ID"),
                col("original_name", "原文件名"),
                col("relative_path", "保存路径"),
                col("file_size", "大小"),
                col("content_type", "类型"),
                col("upload_user", "上传人"),
                col("upload_time", "上传时间")
        ));
    }

    private static void register(String moduleCode, String frontendModule, String displayName,
                                  String tableName, String createTimeColumn, String updateTimeColumn,
                                  List<ColumnDef> columns) {
        REGISTRY.put(moduleCode, new ModuleEntry(moduleCode, frontendModule, displayName,
                tableName, createTimeColumn, updateTimeColumn, List.copyOf(columns)));
    }

    private static ColumnDef col(String fieldName, String label) {
        return new ColumnDef(fieldName, label, false);
    }

    private static ColumnDef col(String fieldName, String label, boolean sensitive) {
        return new ColumnDef(fieldName, label, sensitive);
    }
}
