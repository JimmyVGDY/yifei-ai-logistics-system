package jimmy.system.config;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 标准列注册表 —— 定义每个模块在 API 响应中返回的全部字段及敏感标记。
 * <p>
 * 职责：
 * <ol>
 *   <li>作为后端唯一的列定义源，驱动 COLUMN 类型权限的自动生成</li>
 *   <li>敏感列（sensitive=true）仅在 {@code :manage} 级别自动授权，{@code :view} 不自动授权</li>
 *   <li>{@link #moduleNames()} 用于判别「模块化权限」vs「独立权限」</li>
 * </ol>
 * <p>
 * 列名使用 snake_case，与 SQL 查询返回的列别名和前端 module-metadata.js 的 prop 一致。
 *
 * @see jimmy.system.service.SystemPermissionService
 */
@Component
public class StandardColumnRegistry {

    private final Map<String, List<ColumnDef>> registry = new LinkedHashMap<>();

    public StandardColumnRegistry() {
        init();
    }

    /**
     * 列定义 —— fieldName 对应 SQL 列别名（snake_case）
     *
     * @param fieldName 列名（snake_case，与 SQL 返回的列别名一致）
     * @param label     中文名称（用于权限树展示）
     * @param sensitive 是否为敏感列（:view 不自动授权）
     */
    public record ColumnDef(String fieldName, String label, boolean sensitive) {
    }

    /**
     * 获取指定模块的列定义列表（不可变）。
     */
    public List<ColumnDef> columns(String module) {
        return Collections.unmodifiableList(registry.getOrDefault(module, List.of()));
    }

    /**
     * 返回所有已注册模块名称（用于判别「模块化权限」vs「_standalone」）。
     */
    public Set<String> moduleNames() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    // ==================== 15 模块列定义 ====================

    private void init() {
        customers();
        orders();
        waybills();
        dispatches();
        tasks();
        tracks();
        drivers();
        vehicles();
        exceptions();
        fees();
        users();
        roles();
        operationLogs();
        files();
        dashboard();
    }

    private void register(String module, List<ColumnDef> columns) {
        registry.put(module, columns);
    }

    private static ColumnDef col(String fieldName, String label, boolean sensitive) {
        return new ColumnDef(fieldName, label, sensitive);
    }

    // --- 客户管理 ---
    private void customers() {
        register("customer", List.of(
                col("id", "ID", false),
                col("customer_code", "客户编号", false),
                col("customer_name", "客户名称", false),
                col("contact_name", "联系人", false),
                col("contact_phone", "联系电话", true),     // PII
                col("province", "省份", false),
                col("city", "城市", false),
                col("address", "地址", false),
                col("status", "状态", false),
                col("created_at", "创建时间", false),
                col("updated_at", "更新时间", false)
        ));
    }

    // --- 运单管理 ---
    private void orders() {
        register("order", List.of(
                col("id", "ID", false),
                col("order_no", "订单号", false),
                col("customer_id", "客户ID", false),
                col("customer_name", "客户名称", false),
                col("route_id", "路线ID", false),
                col("warehouse_id", "仓库ID", false),
                col("vehicle_id", "车辆ID", false),
                col("driver_id", "司机ID", false),
                col("sender_address", "发货地址", false),
                col("receiver_address", "收货地址", false),
                col("cargo_name", "货物名称", false),
                col("cargo_weight", "货物重量", false),
                col("cargo_volume", "货物体积", false),
                col("status", "状态", false),
                col("planned_pickup_time", "计划取件时间", false),
                col("planned_delivery_time", "计划送达时间", false),
                col("created_at", "创建时间", false),
                col("updated_at", "更新时间", false)
        ));
    }

    // --- 运单中心 ---
    private void waybills() {
        register("waybill", List.of(
                col("id", "ID", false),
                col("waybill_no", "运单号", false),
                col("order_id", "订单ID", false),
                col("order_no", "订单号", false),
                col("start_site", "始发网点", false),
                col("target_site", "目的网点", false),
                col("current_location", "当前位置", false),
                col("transport_status", "运输状态", false),
                col("create_time", "创建时间", false),
                col("update_time", "更新时间", false)
        ));
    }

    // --- 调度管理 ---
    private void dispatches() {
        register("dispatch", List.of(
                col("id", "ID", false),
                col("order_id", "订单ID", false),
                col("order_no", "订单号", false),
                col("waybill_id", "运单ID", false),
                col("driver_id", "司机ID", false),
                col("driver_name", "司机", false),
                col("vehicle_id", "车辆ID", false),
                col("vehicle_no", "车辆", false),
                col("start_site", "始发网点", false),
                col("target_site", "目的网点", false),
                col("dispatch_status", "调度状态", false),
                col("planned_departure_time", "计划出发时间", false),
                col("planned_arrival_time", "计划到达时间", false),
                col("create_time", "创建时间", false),
                col("update_time", "更新时间", false)
        ));
    }

    // --- 运输任务 ---
    private void tasks() {
        register("task", List.of(
                col("id", "ID", false),
                col("task_no", "任务号", false),
                col("order_id", "订单ID", false),
                col("order_no", "订单号", false),
                col("waybill_id", "运单ID", false),
                col("dispatch_id", "调度ID", false),
                col("driver_id", "司机ID", false),
                col("driver_name", "司机", false),
                col("vehicle_id", "车辆ID", false),
                col("vehicle_no", "车辆", false),
                col("task_status", "任务状态", false),
                col("proof_url", "签收凭证", true),          // 含签名
                col("create_time", "创建时间", false),
                col("update_time", "更新时间", false)
        ));
    }

    // --- 物流轨迹 ---
    private void tracks() {
        register("track", List.of(
                col("id", "ID", false),
                col("order_id", "订单ID", false),
                col("order_no", "订单号", false),
                col("waybill_id", "运单ID", false),
                col("waybill_no", "运单号", false),
                col("current_status", "当前状态", false),
                col("current_location", "当前位置", false),
                col("operator_name", "操作人", false),
                col("operation_desc", "操作说明", false),
                col("operation_time", "操作时间", false)
        ));
    }

    // --- 司机管理 ---
    private void drivers() {
        register("driver", List.of(
                col("id", "ID", false),
                col("driver_code", "司机编号", false),
                col("driver_name", "司机姓名", false),
                col("phone", "手机号", true),                // PII
                col("license_no", "驾驶证号", true),          // PII
                col("license_type", "准驾车型", false),
                col("status", "状态", false),
                col("created_at", "创建时间", false),
                col("updated_at", "更新时间", false)
        ));
    }

    // --- 车辆管理 ---
    private void vehicles() {
        register("vehicle", List.of(
                col("id", "ID", false),
                col("vehicle_no", "车牌号", false),
                col("vehicle_type", "车辆类型", false),
                col("load_capacity_kg", "载重", false),
                col("volume_capacity_cubic", "容积", false),
                col("current_city", "当前城市", false),
                col("status", "状态", false),
                col("created_at", "创建时间", false),
                col("updated_at", "更新时间", false)
        ));
    }

    // --- 异常管理 ---
    private void exceptions() {
        register("exception", List.of(
                col("id", "ID", false),
                col("order_id", "订单ID", false),
                col("order_no", "订单号", false),
                col("task_id", "任务ID", false),
                col("exception_type", "异常类型", false),
                col("exception_desc", "异常描述", false),
                col("exception_status", "异常状态", false),
                col("report_user", "上报人", false),
                col("report_time", "上报时间", false),
                col("handle_user", "处理人", false),
                col("handle_time", "处理时间", false)
        ));
    }

    // --- 费用结算 ---
    private void fees() {
        register("fee", List.of(
                col("id", "ID", false),
                col("order_id", "订单ID", false),
                col("order_no", "订单号", false),
                col("base_fee", "基础运费", true),           // 金额
                col("weight_fee", "重量费用", true),         // 金额
                col("distance_fee", "距离费用", true),       // 金额
                col("additional_fee", "附加费", true),       // 金额
                col("discount_fee", "优惠金额", true),       // 金额
                col("payable_fee", "应收金额", true),        // 金额
                col("actual_fee", "实收金额", true),         // 金额
                col("payment_status", "付款状态", false),
                col("create_time", "创建时间", false),
                col("update_time", "更新时间", false)
        ));
    }

    // --- 用户管理 ---
    private void users() {
        register("system:user", List.of(     // 模块权限码为 system:user
                col("id", "ID", false),
                col("user_code", "用户编号", false),
                col("username", "登录账号", false),
                col("real_name", "姓名", false),
                col("mobile", "手机号", true),               // PII
                col("email", "邮箱", true),                  // PII
                col("role_id", "角色ID", false),
                col("role_name", "角色", false),
                col("customer_id", "关联客户ID", true),      // 关联信息
                col("customer_name", "关联客户", true),      // 关联信息
                col("customer_subject_type", "客户主体类型", false),
                col("customer_account_type", "客户账号类型", false),
                col("status", "状态", false),
                col("create_time", "创建时间", false),
                col("update_time", "更新时间", false)
        ));
    }

    // --- 角色管理 ---
    private void roles() {
        register("system:role", List.of(     // 模块权限码为 system:role
                col("id", "ID", false),
                col("role_code", "角色编码", false),
                col("role_name", "角色名称", false),
                col("status", "状态", false),
                col("create_time", "创建时间", false),
                col("update_time", "更新时间", false)
        ));
    }

    // --- 操作日志 ---
    private void operationLogs() {
        register("system:log", List.of(      // 模块权限码为 system:log
                col("id", "ID", false),
                col("operation_id", "操作ID", false),
                col("trace_id", "Trace ID", false),
                col("login_session_id", "会话ID", false),
                col("user_id", "用户主键", false),
                col("user_code", "用户编号", false),
                col("username", "操作人", false),
                col("role_code", "角色编号", false),
                col("operation", "操作内容", false),
                col("request_uri", "请求地址", false),
                col("request_method", "方法", false),
                col("operation_status", "状态", false),
                col("cost_ms", "耗时ms", false),
                col("error_message", "异常信息", true),      // 可能暴露内部信息
                col("client_ip", "客户端IP", true),          // 隐私
                col("user_agent", "User Agent", true),       // 隐私
                col("request_params", "参数摘要", true),     // 可能含敏感信息
                col("target_id", "对象ID", false),
                col("change_summary", "变更摘要", true),     // 可能含敏感信息
                col("operation_source", "操作来源", false),
                col("executor_type", "执行者", false),
                col("ai_conversation_id", "AI会话ID", false),
                col("ai_message_id", "AI消息ID", false),
                col("ai_tool_name", "AI工具", false),
                col("ai_tool_target", "AI目标", false),
                col("ai_readonly", "是否只读", false),
                col("ai_prompt_summary", "AI问题摘要", false),
                col("ai_result_summary", "AI结果摘要", false),
                col("ai_memory_id", "AI记忆ID", false),
                col("ai_memory_event_type", "记忆事件", false),
                col("ai_memory_source", "记忆来源", false),
                col("ai_memory_hit_count", "记忆命中数", false),
                col("ai_memory_trace_summary", "记忆链路摘要", false),
                col("operation_time", "操作时间", false)
        ));
    }

    // --- 上传文件 ---
    private void files() {
        register("file", List.of(
                col("id", "ID", false),
                col("original_name", "原文件名", false),
                col("relative_path", "保存路径", false),
                col("file_size", "大小", false),
                col("content_type", "类型", false),
                col("upload_user", "上传人", false),
                col("upload_time", "上传时间", false)
        ));
    }

    // --- 运营看板 ---
    private void dashboard() {
        register("dashboard", List.of(
                // 仪表盘为聚合视图，无标准列定义。actions 中只包含 "view"
        ));
    }
}
