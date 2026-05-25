package jimmy.logistics.service;

import jimmy.common.id.CompactSnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LogisticsCrudService {

    private final JdbcTemplate jdbcTemplate;
    private final CompactSnowflakeIdGenerator idGenerator;
    private final Map<String, CrudConfig> configs;

    public LogisticsCrudService(JdbcTemplate jdbcTemplate, CompactSnowflakeIdGenerator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.configs = buildConfigs();
    }

    public Map<String, Object> create(String module, Map<String, Object> payload) {
        CrudConfig config = requireConfig(module);
        Map<String, Object> values = filteredPayload(config, payload, false);
        Long id = idGenerator.nextId();
        values.put("id", id);
        fillCreateDefaults(config, values);

        StringBuilder fields = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (fields.length() > 0) {
                fields.append(", ");
                placeholders.append(", ");
            }
            fields.append(entry.getKey());
            placeholders.append("?");
            args.add(entry.getValue());
        }
        jdbcTemplate.update("insert into " + config.tableName + " (" + fields + ") values (" + placeholders + ")", args.toArray());
        log.info("管理模块新增完成，module={}, id={}", module, id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        return result;
    }

    public Map<String, Object> update(String module, long id, Map<String, Object> payload) {
        CrudConfig config = requireConfig(module);
        Map<String, Object> values = filteredPayload(config, payload, true);
        fillUpdateDefaults(config, values);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("没有可更新字段");
        }

        StringBuilder setClause = new StringBuilder();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (setClause.length() > 0) {
                setClause.append(", ");
            }
            setClause.append(entry.getKey()).append(" = ?");
            args.add(entry.getValue());
        }
        args.add(id);
        int updated = jdbcTemplate.update("update " + config.tableName + " set " + setClause + " where id = ?", args.toArray());
        if (updated == 0) {
            throw new IllegalArgumentException("记录不存在");
        }
        log.info("管理模块更新完成，module={}, id={}", module, id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        return result;
    }

    public Map<String, Object> delete(String module, long id) {
        CrudConfig config = requireConfig(module);
        int updated = jdbcTemplate.update("delete from " + config.tableName + " where id = ?", id);
        if (updated == 0) {
            throw new IllegalArgumentException("记录不存在");
        }
        log.info("管理模块删除完成，module={}, id={}", module, id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("deleted", true);
        return result;
    }

    private Map<String, Object> filteredPayload(CrudConfig config, Map<String, Object> payload, boolean update) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (payload == null) {
            return values;
        }
        for (String column : config.columns) {
            if (update && (column.equals(config.createTimeColumn) || column.equals(config.updateTimeColumn))) {
                continue;
            }
            if (payload.containsKey(column)) {
                values.put(column, payload.get(column));
            }
        }
        return values;
    }

    private void fillCreateDefaults(CrudConfig config, Map<String, Object> values) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        if (config.createTimeColumn != null && !values.containsKey(config.createTimeColumn)) {
            values.put(config.createTimeColumn, now);
        }
        if (config.updateTimeColumn != null && !values.containsKey(config.updateTimeColumn)) {
            values.put(config.updateTimeColumn, now);
        }
    }

    private void fillUpdateDefaults(CrudConfig config, Map<String, Object> values) {
        if (config.updateTimeColumn != null) {
            values.put(config.updateTimeColumn, new Timestamp(System.currentTimeMillis()));
        }
    }

    private CrudConfig requireConfig(String module) {
        CrudConfig config = configs.get(module);
        if (config == null) {
            throw new IllegalArgumentException("当前模块不支持增删改操作");
        }
        return config;
    }

    private Map<String, CrudConfig> buildConfigs() {
        Map<String, CrudConfig> map = new HashMap<>();
        map.put("customers", new CrudConfig("logistics_customer", "created_at", "updated_at", "customer_code", "customer_name", "contact_name", "contact_phone", "province", "city", "address", "status"));
        map.put("orders", new CrudConfig("logistics_order", "created_at", "updated_at", "order_no", "customer_id", "route_id", "warehouse_id", "vehicle_id", "driver_id", "customer_name", "sender_address", "receiver_address", "cargo_name", "cargo_weight", "cargo_volume", "status", "planned_pickup_time", "planned_delivery_time"));
        map.put("waybills", new CrudConfig("logistics_waybill", "create_time", "update_time", "waybill_no", "order_id", "start_site", "target_site", "current_location", "transport_status"));
        map.put("dispatches", new CrudConfig("logistics_dispatch", "create_time", "update_time", "order_id", "waybill_id", "driver_id", "vehicle_id", "start_site", "target_site", "planned_departure_time", "planned_arrival_time", "dispatch_status"));
        map.put("tasks", new CrudConfig("logistics_task", "create_time", "update_time", "task_no", "order_id", "waybill_id", "dispatch_id", "driver_id", "vehicle_id", "task_status", "proof_url"));
        map.put("tracks", new CrudConfig("logistics_track", "operation_time", null, "order_id", "waybill_id", "current_status", "current_location", "operator_name", "operation_desc"));
        map.put("drivers", new CrudConfig("logistics_driver", "created_at", "updated_at", "driver_code", "driver_name", "phone", "license_no", "license_type", "status"));
        map.put("vehicles", new CrudConfig("logistics_vehicle", "created_at", "updated_at", "vehicle_no", "vehicle_type", "load_capacity_kg", "volume_capacity_cubic", "current_city", "status"));
        map.put("exceptions", new CrudConfig("logistics_exception", "report_time", "handle_time", "order_id", "task_id", "exception_type", "exception_desc", "exception_status", "report_user", "handle_user"));
        map.put("fees", new CrudConfig("logistics_fee", "create_time", "update_time", "order_id", "base_fee", "weight_fee", "distance_fee", "additional_fee", "discount_fee", "payable_fee", "actual_fee", "payment_status"));
        map.put("users", new CrudConfig("sys_user", "create_time", "update_time", "user_code", "username", "real_name", "mobile", "email", "password", "role_id", "status"));
        map.put("roles", new CrudConfig("sys_role", "create_time", "update_time", "role_code", "role_name", "status"));
        return map;
    }

    private static class CrudConfig {
        private final String tableName;
        private final String createTimeColumn;
        private final String updateTimeColumn;
        private final List<String> columns;

        private CrudConfig(String tableName, String createTimeColumn, String updateTimeColumn, String... columns) {
            this.tableName = tableName;
            this.createTimeColumn = createTimeColumn;
            this.updateTimeColumn = updateTimeColumn;
            this.columns = Arrays.asList(columns);
        }
    }
}
