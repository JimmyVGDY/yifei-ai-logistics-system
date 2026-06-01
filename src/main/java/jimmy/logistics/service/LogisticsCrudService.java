package jimmy.logistics.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.mapper.LogisticsCrudMapper;
import jimmy.logistics.model.CrudFieldValue;
import jimmy.logistics.model.OperationResultVO;
import jimmy.logistics.util.ColumnExistenceChecker;
import jimmy.util.FieldEncryptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LogisticsCrudService {

    private static final String BUSINESS_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final LogisticsCrudMapper logisticsCrudMapper;
    private final ColumnExistenceChecker columnChecker;
    private final FieldEncryptor fieldEncryptor;
    private final CompactSnowflakeIdGenerator idGenerator;
    private final Map<String, CrudConfig> configs;

    public LogisticsCrudService(LogisticsCrudMapper logisticsCrudMapper,
                                ColumnExistenceChecker columnChecker,
                                FieldEncryptor fieldEncryptor,
                                CompactSnowflakeIdGenerator idGenerator) {
        this.logisticsCrudMapper = logisticsCrudMapper;
        this.columnChecker = columnChecker;
        this.fieldEncryptor = fieldEncryptor;
        this.idGenerator = idGenerator;
        this.configs = buildConfigs();
    }

    public OperationResultVO create(String module, Map<String, Object> payload) {
        CrudConfig config = requireConfig(module);
        Map<String, Object> values = filteredPayload(config, payload, false);
        Long id = idGenerator.nextId();
        values.put("id", id);
        // 业务编号由后端统一生成,前端不需要手填,避免人工输入重复或格式不统一。
        fillBusinessCodeDefaults(config, values);
        fillCreateDefaults(config, values);
        fillAuditDefaults(config, values, true);
        // 敏感字段加密：手机号等字段入库前加密
        encryptValues(values);

        logisticsCrudMapper.insertRecord(config.tableName, toFieldValues(values));
        log.info("管理模块新增完成,module={}, id={}", module, id);
        return new OperationResultVO(id);
    }

    public OperationResultVO update(String module, long id, Map<String, Object> payload) {
        CrudConfig config = requireConfig(module);
        Map<String, Object> values = filteredPayload(config, payload, true);
        fillUpdateDefaults(config, values);
        fillAuditDefaults(config, values, false);
        // 敏感字段加密：手机号等字段入库前加密
        encryptValues(values);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("没有可更新字段");
        }

        int updated = logisticsCrudMapper.updateRecord(config.tableName, id, toFieldValues(values), columnChecker.hasColumn(config.tableName, "version"));
        if (updated == 0) {
            throw new IllegalArgumentException("记录不存在");
        }
        log.info("管理模块更新完成,module={}, id={}", module, id);
        return new OperationResultVO(id);
    }

    public OperationResultVO delete(String module, long id) {
        CrudConfig config = requireConfig(module);
        int updated;
        // 已执行增量迁移的表优先逻辑删除,未迁移的旧表回退物理删除,保证旧库仍能运行。
        if (columnChecker.hasColumn(config.tableName, "deleted")) {
            Map<String, Object> deleteValues = new LinkedHashMap<>();
            if (config.updateTimeColumn != null && columnChecker.hasColumn(config.tableName, config.updateTimeColumn)) {
                deleteValues.put(config.updateTimeColumn, new Timestamp(System.currentTimeMillis()));
            }
            if (columnChecker.hasColumn(config.tableName, "update_by")) {
                deleteValues.put("update_by", currentUserId());
            }
            updated = logisticsCrudMapper.logicalDelete(config.tableName, id, toFieldValues(deleteValues), columnChecker.hasColumn(config.tableName, "version"));
        } else {
            updated = logisticsCrudMapper.physicalDelete(config.tableName, id);
        }
        if (updated == 0) {
            throw new IllegalArgumentException("记录不存在");
        }
        log.info("管理模块删除完成,module={}, id={}", module, id);
        return OperationResultVO.deleted(id);
    }

    private Map<String, Object> filteredPayload(CrudConfig config, Map<String, Object> payload, boolean update) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (payload == null) {
            return values;
        }
        // 只接受后端白名单字段;更新时未传入的字段不覆盖旧值,允许清空的字段由 nullableColumns 单独声明。
        for (String column : config.columns) {
            if (!update && column.equals(config.generatedCodeColumn)) {
                continue;
            }
            if (update && (column.equals(config.createTimeColumn) || column.equals(config.updateTimeColumn))) {
                continue;
            }
            if (update && payload.get(column) == null && !config.nullableColumns.contains(column)) {
                continue;
            }
            if (columnChecker.hasColumn(config.tableName, column) && payload.containsKey(column)) {
                values.put(column, payload.get(column));
            }
        }
        return values;
    }

    private void fillCreateDefaults(CrudConfig config, Map<String, Object> values) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        if (config.createTimeColumn != null && columnChecker.hasColumn(config.tableName, config.createTimeColumn) && !values.containsKey(config.createTimeColumn)) {
            values.put(config.createTimeColumn, now);
        }
        if (config.updateTimeColumn != null && columnChecker.hasColumn(config.tableName, config.updateTimeColumn) && !values.containsKey(config.updateTimeColumn)) {
            values.put(config.updateTimeColumn, now);
        }
    }

    private void fillBusinessCodeDefaults(CrudConfig config, Map<String, Object> values) {
        if (config.generatedCodeColumn == null || !columnChecker.hasColumn(config.tableName, config.generatedCodeColumn)) {
            return;
        }
        Object current = values.get(config.generatedCodeColumn);
        if (current != null && String.valueOf(current).trim().length() > 0) {
            return;
        }
        values.put(config.generatedCodeColumn, nextBusinessCode(config.tableName, config.generatedCodeColumn, config.generatedCodePrefix));
    }

    private String nextBusinessCode(String tableName, String columnName, String prefix) {
        for (int i = 0; i < 20; i++) {
            String code = prefix + randomCode(6);
            // 随机业务编号先查重再入库,极低概率连续冲突时再回退到雪花 ID 片段。
            if (logisticsCrudMapper.countByBusinessCode(tableName, columnName, code) == 0) {
                return code;
            }
        }
        return prefix + String.valueOf(idGenerator.nextId()).substring(7);
    }

    private String randomCode(int length) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append(BUSINESS_CODE_CHARS.charAt(RANDOM.nextInt(BUSINESS_CODE_CHARS.length())));
        }
        return builder.toString();
    }

    private List<CrudFieldValue> toFieldValues(Map<String, Object> values) {
        List<CrudFieldValue> fields = new ArrayList<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            fields.add(new CrudFieldValue(entry.getKey(), entry.getValue()));
        }
        return fields;
    }

    private void fillUpdateDefaults(CrudConfig config, Map<String, Object> values) {
        if (config.updateTimeColumn != null && columnChecker.hasColumn(config.tableName, config.updateTimeColumn)) {
            values.put(config.updateTimeColumn, new Timestamp(System.currentTimeMillis()));
        }
    }

    private void fillAuditDefaults(CrudConfig config, Map<String, Object> values, boolean create) {
        Long userId = currentUserId();
        if (create && columnChecker.hasColumn(config.tableName, "create_by")) {
            values.put("create_by", userId);
        }
        if (columnChecker.hasColumn(config.tableName, "update_by")) {
            values.put("update_by", userId);
        }
    }

    private CrudConfig requireConfig(String module) {
        CrudConfig config = configs.get(module);
        if (config == null) {
            throw new IllegalArgumentException("当前模块不支持增删改操作");
        }
        return config;
    }

    /** 加密 values 中的敏感字段（手机号等） */
    private void encryptValues(Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (FieldEncryptor.isEncryptedField(entry.getKey()) && entry.getValue() instanceof String) {
                entry.setValue(fieldEncryptor.encrypt((String) entry.getValue()));
            }
        }
    }

    private Long currentUserId() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            return 0L;
        }
        return Long.valueOf(String.valueOf(loginId));
    }



    private Map<String, CrudConfig> buildConfigs() {
        Map<String, CrudConfig> map = new HashMap<>();
        // 通用 CRUD 只能操作这里声明的模块和字段,Mapper XML 中的动态表名/列名都来自该白名单。
        map.put("customers", CrudConfig.withGeneratedCode("logistics_customer", "created_at", "updated_at", "customer_code", "CUST", "customer_code", "customer_name", "contact_name", "contact_phone", "province", "city", "address", "status"));
        map.put("orders", CrudConfig.withNullable("logistics_order", "created_at", "updated_at",
                Arrays.asList("cargo_name", "cargo_weight", "cargo_volume", "planned_pickup_time", "planned_delivery_time", "route_id", "warehouse_id", "vehicle_id", "driver_id"),
                "order_no", "customer_id", "route_id", "warehouse_id", "vehicle_id", "driver_id", "customer_name", "sender_address", "receiver_address", "cargo_name", "cargo_weight", "cargo_volume", "status", "planned_pickup_time", "planned_delivery_time"));
        map.put("waybills", CrudConfig.withGeneratedCode("logistics_waybill", "create_time", "update_time", "waybill_no", "WB", "waybill_no", "order_id", "start_site", "target_site", "current_location", "transport_status"));
        map.put("dispatches", new CrudConfig("logistics_dispatch", "create_time", "update_time", "order_id", "waybill_id", "driver_id", "vehicle_id", "start_site", "target_site", "planned_departure_time", "planned_arrival_time", "dispatch_status"));
        map.put("tasks", CrudConfig.withGeneratedCode("logistics_task", "create_time", "update_time", "task_no", "TASK", "task_no", "order_id", "waybill_id", "dispatch_id", "driver_id", "vehicle_id", "task_status", "proof_url"));
        map.put("tracks", new CrudConfig("logistics_track", "operation_time", null, "order_id", "waybill_id", "current_status", "current_location", "operator_name", "operation_desc"));
        map.put("drivers", CrudConfig.withGeneratedCode("logistics_driver", "created_at", "updated_at", "driver_code", "DRV", "driver_code", "driver_name", "phone", "license_no", "license_type", "status"));
        map.put("vehicles", new CrudConfig("logistics_vehicle", "created_at", "updated_at", "vehicle_no", "vehicle_type", "load_capacity_kg", "volume_capacity_cubic", "current_city", "status"));
        map.put("exceptions", new CrudConfig("logistics_exception", "report_time", "handle_time", "order_id", "task_id", "exception_type", "exception_desc", "exception_status", "report_user", "handle_user"));
        map.put("fees", new CrudConfig("logistics_fee", "create_time", "update_time", "order_id", "base_fee", "weight_fee", "distance_fee", "additional_fee", "discount_fee", "payable_fee", "actual_fee", "payment_status"));
        map.put("users", CrudConfig.withGeneratedCode("sys_user", "create_time", "update_time", "user_code", "U", "user_code", "username", "real_name", "mobile", "email", "password", "role_id", "status"));
        map.put("roles", CrudConfig.withGeneratedCode("sys_role", "create_time", "update_time", "role_code", "R", "role_code", "role_name", "status"));
        return map;
    }

    private static class CrudConfig {
        private final String tableName;
        private final String createTimeColumn;
        private final String updateTimeColumn;
        private final List<String> columns;
        private final List<String> nullableColumns;
        private final String generatedCodeColumn;
        private final String generatedCodePrefix;

        private CrudConfig(String tableName, String createTimeColumn, String updateTimeColumn, String... columns) {
            this(tableName, createTimeColumn, updateTimeColumn, new ArrayList<>(), null, null, columns);
        }

        private static CrudConfig withGeneratedCode(String tableName, String createTimeColumn, String updateTimeColumn,
                                                    String generatedCodeColumn, String generatedCodePrefix, String... columns) {
            return new CrudConfig(tableName, createTimeColumn, updateTimeColumn, new ArrayList<>(), generatedCodeColumn, generatedCodePrefix, columns);
        }

        private static CrudConfig withNullable(String tableName, String createTimeColumn, String updateTimeColumn,
                                               List<String> nullableColumns, String... columns) {
            return new CrudConfig(tableName, createTimeColumn, updateTimeColumn, nullableColumns, null, null, columns);
        }

        private CrudConfig(String tableName, String createTimeColumn, String updateTimeColumn, List<String> nullableColumns,
                           String generatedCodeColumn, String generatedCodePrefix, String... columns) {
            this.tableName = tableName;
            this.createTimeColumn = createTimeColumn;
            this.updateTimeColumn = updateTimeColumn;
            this.columns = Arrays.asList(columns);
            this.nullableColumns = nullableColumns;
            this.generatedCodeColumn = generatedCodeColumn;
            this.generatedCodePrefix = generatedCodePrefix;
        }
    }
}
