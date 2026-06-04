package jimmy.logistics.service;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用 CRUD 白名单注册表。
 * <p>
 * 新增可被通用管理页维护的模块时，只在这里登记表名、时间列、业务编号和允许写入字段。
 */
@Component
public class CrudConfigRegistry {

    private final Map<String, CrudConfig> configs = buildConfigs();

    CrudConfig requireConfig(String module) {
        CrudConfig config = configs.get(module);
        if (config == null) {
            throw new IllegalArgumentException("当前模块不支持增删改操作");
        }
        return config;
    }

    private Map<String, CrudConfig> buildConfigs() {
        Map<String, CrudConfig> map = new HashMap<>();
        map.put("customers", CrudConfig.withGeneratedCode("logistics_customer", "created_at", "updated_at", "customer_code", "CUST", "customer_code", "customer_name", "contact_name", "contact_phone", "province", "city", "address", "status"));
        map.put("orders", CrudConfig.withNullable("logistics_order", "created_at", "updated_at",
                List.of("cargo_name", "cargo_weight", "cargo_volume", "planned_pickup_time", "planned_delivery_time", "route_id", "warehouse_id", "vehicle_id", "driver_id"),
                "order_no", "customer_id", "route_id", "warehouse_id", "vehicle_id", "driver_id", "customer_name", "sender_address", "receiver_address", "cargo_name", "cargo_weight", "cargo_volume", "status", "planned_pickup_time", "planned_delivery_time"));
        map.put("waybills", CrudConfig.withGeneratedCode("logistics_waybill", "create_time", "update_time", "waybill_no", "WB", "waybill_no", "order_id", "start_site", "target_site", "current_location", "transport_status"));
        map.put("dispatches", new CrudConfig("logistics_dispatch", "create_time", "update_time", "order_id", "waybill_id", "driver_id", "vehicle_id", "start_site", "target_site", "planned_departure_time", "planned_arrival_time", "dispatch_status"));
        map.put("tasks", CrudConfig.withGeneratedCode("logistics_task", "create_time", "update_time", "task_no", "TASK", "task_no", "order_id", "waybill_id", "dispatch_id", "driver_id", "vehicle_id", "task_status", "proof_url"));
        map.put("tracks", new CrudConfig("logistics_track", "operation_time", null, "order_id", "waybill_id", "current_status", "current_location", "operator_name", "operation_desc"));
        map.put("drivers", CrudConfig.withGeneratedCode("logistics_driver", "created_at", "updated_at", "driver_code", "DRV", "driver_code", "driver_name", "phone", "license_no", "license_type", "status"));
        map.put("vehicles", new CrudConfig("logistics_vehicle", "created_at", "updated_at", "vehicle_no", "vehicle_type", "load_capacity_kg", "volume_capacity_cubic", "current_city", "status"));
        map.put("exceptions", new CrudConfig("logistics_exception", "report_time", "handle_time", "order_id", "task_id", "exception_type", "exception_desc", "exception_status", "report_user", "handle_user"));
        map.put("fees", new CrudConfig("logistics_fee", "create_time", "update_time", "order_id", "base_fee", "weight_fee", "distance_fee", "additional_fee", "discount_fee", "payable_fee", "actual_fee", "payment_status"));
        map.put("users", CrudConfig.withGeneratedCode("sys_user", "create_time", "update_time", "user_code", "U", "user_code", "username", "real_name", "mobile", "email", "password", "role_id", "customer_id", "customer_account_type", "status"));
        map.put("roles", CrudConfig.withGeneratedCode("sys_role", "create_time", "update_time", "role_code", "R", "role_code", "role_name", "status"));
        return map;
    }
}
