package jimmy.logistics.service;

import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.mapper.LogisticsCrudMapper;
import jimmy.logistics.util.ColumnExistenceChecker;
import jimmy.logistics.util.CrudBusinessUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Map;

/**
 * 用户账号服务 —— 管理端创建/更新用户时的密码加密、客户绑定和手机号校验。
 * <p>
 * 从 {@link LogisticsCrudService} 拆分，独立管理用户账号相关逻辑：
 * <ol>
 *   <li>密码标准化：明文自动 BCrypt 编码，已加密密码跳过</li>
 *   <li>客户绑定：CUSTOMER 角色自动关联/创建客户档案，标记主账号/子账号</li>
 *   <li>手机号校验：11 位中国大陆手机号格式验证</li>
 * </ol>
 */
@Component
public class UserAccountService {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final LogisticsCrudMapper logisticsCrudMapper;
    private final CompactSnowflakeIdGenerator idGenerator;
    private final ColumnExistenceChecker columnChecker;
    private final CrudBusinessUtils utils;

    public UserAccountService(LogisticsCrudMapper logisticsCrudMapper,
                               CompactSnowflakeIdGenerator idGenerator,
                               ColumnExistenceChecker columnChecker,
                               CrudBusinessUtils utils) {
        this.logisticsCrudMapper = logisticsCrudMapper;
        this.idGenerator = idGenerator;
        this.columnChecker = columnChecker;
        this.utils = utils;
    }

    /**
     * 标准化用户密码：明文密码自动 BCrypt 编码入库，已加密的密码（$2a$/$2b$/$2y$ 开头）跳过。
     * <p>
     * 仅对 users 模块生效。空密码或 null 直接移除，不覆盖数据库已有密码。
     *
     * @param module 模块名
     * @param values 待入库的字段值
     * @param update 是否为更新操作（更新时空密码不处理）
     */
    public void normalizeUserPassword(String module, Map<String, Object> values, boolean update) {
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

    /**
     * 标准化用户-客户绑定。
     * <p>
     * 仅对 users 模块生效：当角色为 CUSTOMER 时强制关联客户，
     * 支持按客户 ID 或客户名称查找/创建客户记录。首个客户账号标记为 MAIN，后续为 SUB。
     *
     * @param module 模块名
     * @param values 待入库的字段值
     * @param userId 用户 ID（更新时用于排除自身计数）
     * @param update 是否为更新操作
     */
    public void normalizeUserCustomerBinding(String module, Map<String, Object> values, Long userId, boolean update) {
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

    /**
     * 校验手机号格式：11 位中国大陆手机号（1[3-9]xxxxxxxxx）。
     * <p>
     * 空值跳过，非空值必须匹配正则，否则抛出参数异常。
     *
     * @param values 待入库的字段值
     * @throws IllegalArgumentException 手机号格式不合法
     */
    public void validateMobileFields(Map<String, Object> values) {
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

    // ==================== 内部实现 ====================

    /**
     * 解析客户 ID：按数字 ID → 订单历史客户名 → 客户表客户名 → 自动创建客户档案的顺序查找。
     *
     * @param customerValue 前端提交的客户值（可能是 ID 数字或客户名称字符串）
     * @return 客户 ID，解析失败时 null
     */
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
        // 按客户名称查找：优先历史订单中的客户，其次客户档案表
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
        // 自动创建客户档案
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
}
