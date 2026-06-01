package jimmy.logistics.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.mapper.LogisticsCrudMapper;
import jimmy.logistics.model.CreateCustomerAccountRequest;
import jimmy.logistics.model.CrudFieldValue;
import jimmy.logistics.model.OperationResultVO;
import jimmy.logistics.util.ColumnExistenceChecker;
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

@Slf4j
@Service
public class CustomerAccountService {

    private static final String CUSTOMER_ROLE_CODE = "CUSTOMER";
    private static final String PERSONAL = "PERSONAL";
    private static final String ENTERPRISE = "ENTERPRISE";
    private static final String BUSINESS_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final LogisticsCrudMapper logisticsCrudMapper;
    private final CompactSnowflakeIdGenerator idGenerator;
    private final FieldEncryptor fieldEncryptor;
    private final ColumnExistenceChecker columnChecker;

    public CustomerAccountService(LogisticsCrudMapper logisticsCrudMapper,
                                  CompactSnowflakeIdGenerator idGenerator,
                                  FieldEncryptor fieldEncryptor,
                                  ColumnExistenceChecker columnChecker) {
        this.logisticsCrudMapper = logisticsCrudMapper;
        this.idGenerator = idGenerator;
        this.fieldEncryptor = fieldEncryptor;
        this.columnChecker = columnChecker;
    }

    public OperationResultVO createCustomerAccount(CreateCustomerAccountRequest request) {
        String subjectType = trim(request.getCustomerSubjectType()).toUpperCase();
        String mobile = trim(request.getMobile());
        String encryptedMobile = fieldEncryptor.encrypt(mobile);
        validateUniqueUsername(request.getUsername());
        validateUniqueMobile(mobile, encryptedMobile);

        Long customerId = resolveCustomerId(subjectType, request);
        int existingCustomerAccounts = logisticsCrudMapper.countCustomerAccounts(customerId, null);
        String accountType = existingCustomerAccounts == 0 ? "MAIN" : "SUB";
        if (PERSONAL.equals(subjectType) && existingCustomerAccounts > 0) {
            throw new IllegalArgumentException("个人客户只能创建一个账号");
        }
        if (ENTERPRISE.equals(subjectType) && "MAIN".equals(accountType)
                && logisticsCrudMapper.countEnterpriseMainAccountByCustomerName(trim(request.getCustomerName())) > 0) {
            throw new IllegalArgumentException("该企业客户已经存在主账号");
        }

        Long userId = idGenerator.nextId();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", userId);
        values.put("user_code", nextBusinessCode("sys_user", "user_code", "U"));
        values.put("username", trim(request.getUsername()));
        values.put("real_name", trim(request.getRealName()));
        values.put("mobile", encryptedMobile);
        values.put("email", trim(request.getEmail()));
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

        logisticsCrudMapper.insertRecord("sys_user", toFieldValues(values));
        log.info("客户账号创建完成，userId={}, customerId={}, subjectType={}, accountType={}",
                userId, customerId, subjectType, accountType);
        return new OperationResultVO(userId);
    }

    private void validateUniqueUsername(String username) {
        if (logisticsCrudMapper.countUserByUsername(trim(username)) > 0) {
            throw new IllegalArgumentException("登录账号已存在");
        }
    }

    private void validateUniqueMobile(String mobile, String encryptedMobile) {
        if (logisticsCrudMapper.countUserByMobile(mobile, encryptedMobile) > 0) {
            throw new IllegalArgumentException("手机号已被其他账号使用");
        }
        if (logisticsCrudMapper.countPersonalCustomerByMobile(mobile, encryptedMobile) > 0) {
            throw new IllegalArgumentException("该手机号已创建个人客户账号");
        }
    }

    private Long resolveCustomerId(String subjectType, CreateCustomerAccountRequest request) {
        if (PERSONAL.equals(subjectType)) {
            String customerName = StringUtils.hasText(request.getCustomerName()) ? trim(request.getCustomerName()) : trim(request.getRealName());
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
        return findOrCreateCustomer(trim(request.getCustomerName()));
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
                nextBusinessCode("logistics_customer", "customer_code", "CUST"),
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

    private String nextBusinessCode(String tableName, String columnName, String prefix) {
        for (int i = 0; i < 20; i++) {
            String code = prefix + randomCode(6);
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
            if (entry.getValue() != null) {
                fields.add(new CrudFieldValue(entry.getKey(), entry.getValue()));
            }
        }
        return fields;
    }

    private Long currentUserId() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId == null ? 0L : Long.valueOf(String.valueOf(loginId));
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
