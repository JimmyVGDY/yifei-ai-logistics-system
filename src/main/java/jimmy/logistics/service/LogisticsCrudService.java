package jimmy.logistics.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.mapper.LogisticsCrudMapper;
import jimmy.logistics.model.CrudFieldValue;
import jimmy.logistics.model.OperationResultVO;
import jimmy.logistics.util.ColumnExistenceChecker;
import jimmy.logistics.util.CrudBusinessUtils;
import jimmy.util.FieldEncryptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用 CRUD 服务 —— 通过动态模块白名单实现多表统一增删改。
 * <p>
 * 核心设计：
 * <ul>
 *   <li>模块配置白名单 {@link CrudConfig} 定义每个模块的表名、字段列表、业务编号前缀</li>
 *   <li>新增/更新/删除均先过滤只允许白名单字段，然后填充审计字段（create_by/update_by）</li>
 *   <li>业务编号（如订单号）由 {@link CrudBusinessUtils#nextBusinessCode} 统一生成</li>
 *   <li>手机号等敏感字段通过 {@link FieldEncryptor} 加密后入库</li>
 *   <li>每次写操作通过 {@link ChangeAuditService} 生成变更摘要</li>
 *   <li>删除优先逻辑删除（deleted 字段），旧表回退物理删除</li>
 * </ul>
 */
@Slf4j
@Service
public class LogisticsCrudService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final LogisticsCrudMapper logisticsCrudMapper;
    private final ColumnExistenceChecker columnChecker;
    private final CrudBusinessUtils utils;
    private final ChangeAuditService changeAudit;
    private final FieldEncryptor fieldEncryptor;
    private final CompactSnowflakeIdGenerator idGenerator;
    private final CrudConfigRegistry crudConfigRegistry;

    public LogisticsCrudService(LogisticsCrudMapper logisticsCrudMapper,
                                ChangeAuditService changeAudit,
                                ColumnExistenceChecker columnChecker,
                                CrudBusinessUtils utils,
                                FieldEncryptor fieldEncryptor,
                                CompactSnowflakeIdGenerator idGenerator,
                                CrudConfigRegistry crudConfigRegistry) {
        this.logisticsCrudMapper = logisticsCrudMapper;
        this.columnChecker = columnChecker;
        this.utils = utils;
        this.changeAudit = changeAudit;
        this.fieldEncryptor = fieldEncryptor;
        this.idGenerator = idGenerator;
        this.crudConfigRegistry = crudConfigRegistry;
    }

    /**
     * 通用新增记录。
     * <p>
     * 流程：过滤白名单字段 → 生成 Snowflake ID → 填充业务编号 → 填充审计字段 →
     * 验证手机号格式 → 加密敏感字段 → 记录变更摘要 → 写入数据库。
     *
     * @param module 模块名（如 customers/orders/users）
     * @param payload 前端提交的字段值
     * @return 含新记录 ID 的结果
     */
    public OperationResultVO create(String module, Map<String, Object> payload) {
        CrudConfig config = requireConfig(module);
        Map<String, Object> values = filteredPayload(config, payload, false);
        normalizeUserPassword(module, values, false);
        Long id = idGenerator.nextId();
        values.put("id", id);
        normalizeUserCustomerBinding(module, values, id, false);
        // 业务编号由后端统一生成,前端不需要手填,避免人工输入重复或格式不统一。
        fillBusinessCodeDefaults(config, values);
        fillCreateDefaults(config, values);
        fillAuditDefaults(config, values, true);
        validateMobileFields(values);
        fillSensitiveLookupHashes(config, values);
        // 敏感字段加密：手机号等字段入库前加密
        changeAudit.recordCreate(values);
        encryptValues(values);

        logisticsCrudMapper.insertRecord(config.tableName, utils.toFieldValues(values));
        log.info("管理模块新增完成,module={}, id={}", module, id);
        return new OperationResultVO(id);
    }

    /**
     * 通用更新记录。
     * <p>
     * 更新前先查询旧值，只提交实际变化的字段，加密字段解密后对比确保 diff 准确。
     *
     * @param module 模块名
     * @param id 记录 ID
     * @param payload 前端提交的变更字段
     * @return 含记录 ID 的结果
     * @throws IllegalArgumentException 无白名单字段可更新 或 记录不存在
     */
    public OperationResultVO update(String module, long id, Map<String, Object> payload) {
        CrudConfig config = requireConfig(module);
        Map<String, Object> values = filteredPayload(config, payload, true);
        normalizeUserPassword(module, values, true);
        normalizeUserCustomerBinding(module, values, id, true);
        Map<String, Object> changeValues = new LinkedHashMap<>(values);
        Map<String, Object> before = logisticsCrudMapper.selectRecordById(config.tableName, id);
        fillUpdateDefaults(config, values);
        fillAuditDefaults(config, values, false);
        validateMobileFields(values);
        fillSensitiveLookupHashes(config, values);
        // 敏感字段加密：手机号等字段入库前加密
        encryptValues(values);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("没有可更新字段");
        }

        int updated = logisticsCrudMapper.updateRecord(config.tableName, id, utils.toFieldValues(values), columnChecker.hasColumn(config.tableName, "version"));
        if (updated == 0) {
            throw new IllegalArgumentException("记录不存在");
        }
        changeAudit.recordUpdate(changeValues, before);
        log.info("管理模块更新完成,module={}, id={}", module, id);
        return new OperationResultVO(id);
    }

    /**
     * 通用删除。优先逻辑删除（deleted 标记），未迁移旧表回退物理删除。
     * 删除前查出完整记录供变更摘要记入审计日志。
     */
    public OperationResultVO delete(String module, long id) {
        CrudConfig config = requireConfig(module);
        Map<String, Object> before = logisticsCrudMapper.selectRecordById(config.tableName, id);
        if (!columnChecker.hasColumn(config.tableName, "deleted")) {
            throw new IllegalStateException("当前表未迁移 deleted 字段，禁止物理删除，请先执行增量迁移");
        }
        Map<String, Object> deleteValues = new LinkedHashMap<>();
        if (config.updateTimeColumn != null && columnChecker.hasColumn(config.tableName, config.updateTimeColumn)) {
            deleteValues.put(config.updateTimeColumn, new Timestamp(System.currentTimeMillis()));
        }
        if (columnChecker.hasColumn(config.tableName, "update_by")) {
            deleteValues.put("update_by", currentUserId());
        }
        int updated = logisticsCrudMapper.logicalDelete(config.tableName, id, utils.toFieldValues(deleteValues), columnChecker.hasColumn(config.tableName, "version"));
        if (updated == 0) {
            throw new IllegalArgumentException("记录不存在");
        }
        changeAudit.recordDelete(before);
        log.info("管理模块删除完成,module={}, id={}", module, id);
        return OperationResultVO.deleted(id);
    }


    /**
     * 过滤前端提交的字段，只保留白名单列。
     * <p>
     * 新增时跳过业务编号（后端生成），更新时跳过时间戳（由 fillUpdateDefaults 统一填充），
     * nullableColumns 声明允许清空的字段。
     */
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
        values.put(config.generatedCodeColumn, utils.nextBusinessCode(config.tableName, config.generatedCodeColumn, config.generatedCodePrefix));
    }

    /**
     * 标准化用户-客户绑定。
     * <p>
     * 仅对 users 模块生效：当角色为 CUSTOMER 时强制关联客户，
     * 支持按客户 ID 或客户名称查找/创建客户记录。首个客户账号标记为 MAIN，后续为 SUB。
     */
    private void normalizeUserCustomerBinding(String module, Map<String, Object> values, Long userId, boolean update) {
        if (!"users".equals(module)) {
            return;
        }
        Long roleId = parseLong(values.get("role_id"));
        String roleCode = roleId == null ? null : logisticsCrudMapper.selectRoleCodeById(roleId);
        boolean customerRole = "CUSTOMER".equals(roleCode);
        if (roleCode != null && !customerRole) {
            if (columnChecker.hasColumn("sys_user", "customer_id")) {
                values.put("customer_id", null);
            }
            if (columnChecker.hasColumn("sys_user", "customer_account_type")) {
                values.put("customer_account_type", null);
            }
            return;
        }
        if (!customerRole && !values.containsKey("customer_id")) {
            return;
        }
        Long customerId = resolveCustomerId(values.get("customer_id"));
        if (customerId == null) {
            if (customerRole && !update) {
                throw new IllegalArgumentException("客户角色账号必须选择或填写客户名称");
            }
            values.remove("customer_id");
            values.remove("customer_account_type");
            return;
        }
        values.put("customer_id", customerId);
        if (columnChecker.hasColumn("sys_user", "customer_account_type")) {
            int existingAccounts = logisticsCrudMapper.countCustomerAccounts(customerId, update ? userId : null);
            values.put("customer_account_type", existingAccounts == 0 ? "MAIN" : "SUB");
        }
    }

    private Long resolveCustomerId(Object customerValue) {
        if (customerValue == null) {
            return null;
        }
        String rawValue = String.valueOf(customerValue).trim();
        if (rawValue.isEmpty()) {
            return null;
        }
        Long numericId = parseLong(rawValue);
        if (numericId != null) {
            return numericId;
        }
        Long orderCustomerId = logisticsCrudMapper.selectCustomerIdFromOrdersByName(rawValue);
        if (orderCustomerId != null) {
            logisticsCrudMapper.updateOrderCustomerIdByName(orderCustomerId, rawValue);
            return orderCustomerId;
        }
        Long existingCustomerId = logisticsCrudMapper.selectCustomerIdByName(rawValue);
        if (existingCustomerId != null) {
            logisticsCrudMapper.updateOrderCustomerIdByName(existingCustomerId, rawValue);
            return existingCustomerId;
        }
        Long newCustomerId = idGenerator.nextId();
        String customerCode = utils.nextBusinessCode("logistics_customer", "customer_code", "CUST");
        logisticsCrudMapper.insertCustomerForAccount(newCustomerId, customerCode, rawValue, new Timestamp(System.currentTimeMillis()));
        logisticsCrudMapper.updateOrderCustomerIdByName(newCustomerId, rawValue);
        return newCustomerId;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
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
        return crudConfigRegistry.requireConfig(module);
    }

    /** 加密 values 中的敏感字段（手机号等） */
    private void encryptValues(Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (FieldEncryptor.isEncryptedField(entry.getKey()) && entry.getValue() instanceof String plainText) {
                entry.setValue(fieldEncryptor.encrypt(plainText));
            }
        }
    }

    private void fillSensitiveLookupHashes(CrudConfig config, Map<String, Object> values) {
        if (!"sys_user".equals(config.tableName) || !columnChecker.hasColumn("sys_user", "mobile_hash")) {
            return;
        }
        Object mobile = values.get("mobile");
        if (mobile instanceof String mobileText && StringUtils.hasText(mobileText)) {
            values.put("mobile_hash", fieldEncryptor.lookupHash(mobileText));
        }
    }

    private void normalizeUserPassword(String module, Map<String, Object> values, boolean update) {
        if (!"users".equals(module) || !values.containsKey("password")) {
            return;
        }
        Object rawPassword = values.get("password");
        if (rawPassword == null || !StringUtils.hasText(String.valueOf(rawPassword))) {
            values.remove("password");
            return;
        }
        String password = String.valueOf(rawPassword);
        if (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$")) {
            return;
        }
        values.put("password", PASSWORD_ENCODER.encode(password));
    }

    private void validateMobileFields(Map<String, Object> values) {
        for (String column : Arrays.asList("mobile", "phone")) {
            Object value = values.get(column);
            if (value == null || String.valueOf(value).trim().isEmpty()) {
                continue;
            }
            if (!String.valueOf(value).trim().matches("^1[3-9]\\d{9}$")) {
                throw new IllegalArgumentException("手机号必须是11位中国大陆手机号");
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

}
