package jimmy.logistics.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.mapper.LogisticsCrudMapper;
import jimmy.logistics.model.CreateCustomerAccountRequest;
import jimmy.logistics.model.OperationChangeContext;
import jimmy.logistics.model.CrudFieldValue;
import jimmy.logistics.model.OperationResultVO;
import jimmy.logistics.util.ColumnExistenceChecker;
import jimmy.logistics.util.CrudBusinessUtils;
import jimmy.util.FieldEncryptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户账号创建服务 —— 独立的客户注册流程，支持个人/企业两种主体类型。
 * <p>
 * 规则：
 * <ul>
 *   <li>个人客户只能创建 1 个账号</li>
 *   <li>企业客户允许多个子账号，首账号标记为 MAIN</li>
 *   <li>企业客户只能有 1 个主账号</li>
 *   <li>自动查找或创建客户记录（按名称匹配，无匹配则新建）</li>
 *   <li>密码 BCrypt 加密存储</li>
 *   <li>手机号入库前加密</li>
 * </ul>
 */
@Slf4j
@Service
public class CustomerAccountService {

    private static final String CUSTOMER_ROLE_CODE = "CUSTOMER";
    private static final String PERSONAL = "PERSONAL";
    private static final String ENTERPRISE = "ENTERPRISE";
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final LogisticsCrudMapper logisticsCrudMapper;
    private final CrudBusinessUtils utils;
    private final CompactSnowflakeIdGenerator idGenerator;
    private final FieldEncryptor fieldEncryptor;
    private final ColumnExistenceChecker columnChecker;

    public CustomerAccountService(LogisticsCrudMapper logisticsCrudMapper,
                                  CrudBusinessUtils utils,
                                  CompactSnowflakeIdGenerator idGenerator,
                                  FieldEncryptor fieldEncryptor,
                                  ColumnExistenceChecker columnChecker) {
        this.logisticsCrudMapper = logisticsCrudMapper;
        this.utils = utils;
        this.idGenerator = idGenerator;
        this.fieldEncryptor = fieldEncryptor;
        this.columnChecker = columnChecker;
    }

    /** 创建客户账号，校验唯一性后写入 sys_user */
    public OperationResultVO createCustomerAccount(CreateCustomerAccountRequest request) {
        String subjectType = CrudBusinessUtils.trim(request.getCustomerSubjectType()).toUpperCase();
        String mobile = CrudBusinessUtils.trim(request.getMobile());
        String encryptedMobile = fieldEncryptor.encrypt(mobile);
        String legacyEncryptedMobile = fieldEncryptor.legacyEncryptForLookup(mobile);
        String mobileHash = fieldEncryptor.lookupHash(mobile);
        validateUniqueUsername(request.getUsername());
        validateUniqueMobile(mobile, encryptedMobile, legacyEncryptedMobile, mobileHash);

        Long customerId = resolveCustomerId(subjectType, request);
        int existingCustomerAccounts = logisticsCrudMapper.countCustomerAccounts(customerId, null);
        String accountType = existingCustomerAccounts == 0 ? "MAIN" : "SUB";
        if (PERSONAL.equals(subjectType) && existingCustomerAccounts > 0) {
            throw new IllegalArgumentException("个人客户只能创建一个账号");
        }
        if (ENTERPRISE.equals(subjectType) && "MAIN".equals(accountType)
                && logisticsCrudMapper.countEnterpriseMainAccountByCustomerName(CrudBusinessUtils.trim(request.getCustomerName())) > 0) {
            throw new IllegalArgumentException("该企业客户已经存在主账号");
        }

        Long userId = idGenerator.nextId();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", userId);
        values.put("user_code", utils.nextBusinessCode("sys_user", "user_code", "U"));
        values.put("username", CrudBusinessUtils.trim(request.getUsername()));
        values.put("real_name", CrudBusinessUtils.trim(request.getRealName()));
        values.put("mobile", encryptedMobile);
        if (columnChecker.hasColumn("sys_user", "mobile_hash")) {
            values.put("mobile_hash", mobileHash);
        }
        values.put("email", CrudBusinessUtils.trim(request.getEmail()));
        values.put("password", PASSWORD_ENCODER.encode(request.getPassword()));
        values.put("role_id", requireCustomerRoleId());
        values.put("customer_id", customerId);
        values.put("customer_subject_type", subjectType);
        values.put("customer_account_type", accountType);
        values.put("status", 1);
        values.put("create_time", now);
        values.put("update_time", now);
        if (columnChecker.hasColumn("sys_user", "create_by")) {
            values.put("create_by", currentUserId());
        }
        if (columnChecker.hasColumn("sys_user", "update_by")) {
            values.put("update_by", currentUserId());
        }

        logisticsCrudMapper.insertRecord("sys_user", utils.toFieldValues(values));
        OperationChangeContext.setChangeSummary("账号=" + request.getUsername() + ", 姓名=" + request.getRealName() + ", 类型=" + subjectType + ", 角色=" + accountType);
        log.info("客户账号创建完成，userId={}, customerId={}, subjectType={}, accountType={}",
                userId, customerId, subjectType, accountType);
        return new OperationResultVO(userId);
    }

    private void validateUniqueUsername(String username) {
        if (logisticsCrudMapper.countUserByUsername(CrudBusinessUtils.trim(username)) > 0) {
            throw new IllegalArgumentException("登录账号已存在");
        }
    }

    private void validateUniqueMobile(String mobile, String encryptedMobile, String legacyEncryptedMobile, String mobileHash) {
        if (logisticsCrudMapper.countUserByMobile(mobile, encryptedMobile, legacyEncryptedMobile, mobileHash) > 0) {
            throw new IllegalArgumentException("手机号已被其他账号使用");
        }
        if (logisticsCrudMapper.countPersonalCustomerByMobile(mobile, encryptedMobile, legacyEncryptedMobile, mobileHash) > 0) {
            throw new IllegalArgumentException("该手机号已创建个人客户账号");
        }
    }

    private Long resolveCustomerId(String subjectType, CreateCustomerAccountRequest request) {
        if (PERSONAL.equals(subjectType)) {
            String customerName = StringUtils.hasText(request.getCustomerName()) ? CrudBusinessUtils.trim(request.getCustomerName()) : CrudBusinessUtils.trim(request.getRealName());
            return findOrCreateCustomer(customerName);
        }
        if (!ENTERPRISE.equals(subjectType)) {
            throw new IllegalArgumentException("客户账号类型只能是个人账号或企业账号");
        }
        if (!StringUtils.hasText(request.getCustomerName())) {
            throw new IllegalArgumentException("企业客户必须填写公司名称");
        }
        if (request.getCustomerId() != null && request.getCustomerId() > 0) {
            return request.getCustomerId();
        }
        return findOrCreateCustomer(CrudBusinessUtils.trim(request.getCustomerName()));
    }

    private Long findOrCreateCustomer(String customerName) {
        Long orderCustomerId = logisticsCrudMapper.selectCustomerIdFromOrdersByName(customerName);
        if (orderCustomerId != null) {
            logisticsCrudMapper.updateOrderCustomerIdByName(orderCustomerId, customerName);
            return orderCustomerId;
        }
        Long existingCustomerId = logisticsCrudMapper.selectCustomerIdByName(customerName);
        if (existingCustomerId != null) {
            logisticsCrudMapper.updateOrderCustomerIdByName(existingCustomerId, customerName);
            return existingCustomerId;
        }
        Long customerId = idGenerator.nextId();
        logisticsCrudMapper.insertCustomerForAccount(
                customerId,
                utils.nextBusinessCode("logistics_customer", "customer_code", "CUST"),
                customerName,
                new Timestamp(System.currentTimeMillis()));
        logisticsCrudMapper.updateOrderCustomerIdByName(customerId, customerName);
        return customerId;
    }

    private Long requireCustomerRoleId() {
        Long roleId = logisticsCrudMapper.selectRoleIdByCode(CUSTOMER_ROLE_CODE);
        if (roleId == null) {
            throw new IllegalArgumentException("客户角色不存在，请先初始化 CUSTOMER 角色");
        }
        return roleId;
    }




    private Long currentUserId() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId == null ? 0L : Long.valueOf(String.valueOf(loginId));
    }

}
