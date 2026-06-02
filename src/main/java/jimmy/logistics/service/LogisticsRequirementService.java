package jimmy.logistics.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.logistics.mapper.LogisticsDashboardMapper;
import jimmy.logistics.mapper.LogisticsModuleQueryMapper;
import jimmy.logistics.model.LogisticsDashboardSummary;
import jimmy.logistics.model.ModuleQueryDTO;
import jimmy.logistics.model.ModuleRecordVO;
import jimmy.logistics.model.StatusLabel;
import jimmy.logistics.util.ColumnExistenceChecker;
import jimmy.model.PageResult;
import jimmy.util.FieldEncryptor;
import jimmy.util.LogMaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LogisticsRequirementService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LogisticsDashboardMapper logisticsDashboardMapper;
    private final LogisticsModuleQueryMapper logisticsModuleQueryMapper;
    private final ColumnExistenceChecker columnChecker;
    private final FieldEncryptor fieldEncryptor;
    private final Map<String, ModuleQueryConfig> moduleQueryConfigs;

    public LogisticsRequirementService(LogisticsDashboardMapper logisticsDashboardMapper,
                                       LogisticsModuleQueryMapper logisticsModuleQueryMapper,
                                       ColumnExistenceChecker columnChecker,
                                       FieldEncryptor fieldEncryptor) {
        this.logisticsDashboardMapper = logisticsDashboardMapper;
        this.logisticsModuleQueryMapper = logisticsModuleQueryMapper;
        this.columnChecker = columnChecker;
        this.fieldEncryptor = fieldEncryptor;
        this.moduleQueryConfigs = buildModuleQueryConfigs();
    }

    public LogisticsDashboardSummary dashboardSummary() {
        log.info("开始查询物流运营看板统计数据");
        LogisticsDashboardSummary summary = new LogisticsDashboardSummary();
        Long customerId = getCurrentCustomerId();
        summary.setTodayOrders(safeLong(logisticsDashboardMapper.countTodayOrders(customerId)));
        summary.setCompletedOrders(safeLong(logisticsDashboardMapper.countCompletedOrders(customerId)));
        summary.setWaitDispatchOrders(safeLong(logisticsDashboardMapper.countWaitDispatchOrders(customerId)));
        summary.setInTransitOrders(safeLong(logisticsDashboardMapper.countInTransitOrders(customerId)));
        summary.setExceptionOrders(safeLong(logisticsDashboardMapper.countOpenExceptionOrders(customerId)));
        summary.setMonthIncome(safeBigDecimal(logisticsDashboardMapper.sumPaidMonthIncome(customerId)));
        summary.setStatusDistribution(logisticsDashboardMapper.selectStatusDistribution(customerId));
        summary.setRecentExceptions(formatDateTimeValues(logisticsDashboardMapper.selectRecentOpenExceptions(customerId)));
        // 合同到期预警：查询 30 天内到期的有效合同
        summary.setExpiringContracts(logisticsDashboardMapper.selectExpiringContracts(30));
        // 上月收入/订单/异常汇总
        summary.setLastMonthIncome(safeBigDecimal(logisticsDashboardMapper.sumPaidMonthIncome(customerId)));
        summary.setLastMonthOrders(safeLong(logisticsDashboardMapper.countLastMonthOrders()));
        summary.setLastMonthExceptions(logisticsDashboardMapper.countLastMonthExceptions());
        log.info("物流运营看板统计完成，todayOrders={}, waitDispatch={}, inTransit={}, exceptionOrders={}",
                summary.getTodayOrders(), summary.getWaitDispatchOrders(), summary.getInTransitOrders(), summary.getExceptionOrders());
        return summary;
    }

    public List<Map<String, Object>> moduleRecords(String module, int limit) {
        PageResult<ModuleRecordVO> page = modulePage(module, query(1, limit, null, null, null));
        return new ArrayList<Map<String, Object>>(page.getRecords());
    }

    public List<Map<String, Object>> moduleRecords(String module, int limit, String keyword, String startTime, String endTime) {
        PageResult<ModuleRecordVO> page = modulePage(module, query(1, limit, keyword, startTime, endTime));
        return new ArrayList<Map<String, Object>>(page.getRecords());
    }

    public PageResult<ModuleRecordVO> modulePage(String module, ModuleQueryDTO query) {
        ModuleQueryConfig queryConfig = moduleQueryConfigs.get(module);
        if (queryConfig == null) {
            log.warn("查询物流模块列表失败：不支持的模块，module={}", module);
            return new PageResult<>(Collections.emptyList(), safePage(query), safePageSize(query), 0);
        }

        int page = safePage(query);
        int pageSize = safePageSize(query);
        String keyword = trimToNull(query == null ? null : query.getKeyword());
        String startTime = trimToNull(query == null ? null : query.getStartTime());
        String endTime = trimToNull(query == null ? null : query.getEndTime());
        String usage = trimToNull(query == null ? null : query.getUsage());
        boolean deletedExists = columnChecker.hasColumn(queryConfig.tableName, "deleted");
        boolean userCodeExists = "users".equals(module) && columnChecker.hasColumn("sys_user", "user_code");
        boolean operationLogExtendedExists = "operationLogs".equals(module) && columnChecker.hasColumn("sys_operation_log", "operation_id");
        boolean operationLogErrorMessageExists = "operationLogs".equals(module) && columnChecker.hasColumn("sys_operation_log", "error_message");
        boolean operationLogClientContextExists = "operationLogs".equals(module) && columnChecker.hasColumn("sys_operation_log", "client_ip");

        // 模块、表名和可查询字段都来自后端白名单；关键词和时间范围仍通过 MyBatis 参数绑定。
        Long total = logisticsModuleQueryMapper.countModule(module, deletedExists, userCodeExists, operationLogExtendedExists,
                operationLogErrorMessageExists, operationLogClientContextExists, keyword,
                queryConfig.keywordColumns, queryConfig.timeColumn, startTime, endTime,
                getCurrentCustomerId());
        List<Map<String, Object>> records = logisticsModuleQueryMapper.selectModulePage(module, deletedExists,
                userCodeExists, operationLogExtendedExists, operationLogErrorMessageExists, operationLogClientContextExists, keyword,
                queryConfig.keywordColumns, queryConfig.timeColumn, startTime, endTime,
                queryConfig.orderColumn, pageSize, (page - 1) * pageSize,
                getCurrentCustomerId());
        // 敏感字段解密：手机号等字段从数据库读取后解密为原文
        decryptRecords(records);
        records = formatDateTimeValues(records);

        List<ModuleRecordVO> voRecords = new ArrayList<>();
        for (Map<String, Object> record : records) {
            voRecords.add(new ModuleRecordVO(record));
        }
        if ("relationOptions".equals(usage)) {
            log.debug("加载{}关联下拉备选完成，module={}, page={}, pageSize={}, total={}",
                    moduleDisplayName(module), module, page, pageSize, total);
        } else {
            log.info("查询{}列表完成，module={}, page={}, pageSize={}, total={}, keyword={}, startTime={}, endTime={}",
                    moduleDisplayName(module), module, page, pageSize, total, LogMaskUtils.maskText(keyword), startTime, endTime);
        }
        return new PageResult<>(voRecords, page, pageSize, total == null ? 0 : total);
    }

    /** 解密记录中的敏感字段（手机号等） */
    private void decryptRecords(List<Map<String, Object>> records) {
        for (Map<String, Object> record : records) {
            for (Map.Entry<String, Object> entry : record.entrySet()) {
                if (FieldEncryptor.isEncryptedField(entry.getKey()) && entry.getValue() instanceof String) {
                    entry.setValue(fieldEncryptor.decrypt((String) entry.getValue()));
                }
            }
        }
    }

    /** 获取当前登录用户的客户ID，用于客户角色数据权限隔离 */
    private Long getCurrentCustomerId() {
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId == null) {
                return null;
            }
            Object customerId = StpUtil.getSessionByLoginId(loginId).get("customerId");
            return customerId instanceof Number ? ((Number) customerId).longValue() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<Map<String, Object>> formatDateTimeValues(List<Map<String, Object>> records) {
        for (int i = 0; i < records.size(); i++) {
            Map<String, Object> formatted = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : records.get(i).entrySet()) {
                if ("deleted_marker".equals(entry.getKey())) {
                    continue;
                }
                Object value = formatValue(entry.getValue());
                formatted.put(entry.getKey(), value);
                if (entry.getKey().contains("status") || "status".equals(entry.getKey())) {
                    // 后端同时返回 statusLabel，前端未知状态也能兜底展示中文含义。
                    formatted.put(entry.getKey() + "Label", StatusLabel.label(value == null ? null : String.valueOf(value)));
                }
            }
            records.set(i, formatted);
        }
        return records;
    }

    private Object formatValue(Object value) {
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(DATE_TIME_FORMATTER);
        }
        if (value instanceof Timestamp) {
            // 使用线程安全的 DateTimeFormatter 替代 SimpleDateFormat，避免并发格式化异常。
            return ((Timestamp) value).toLocalDateTime().format(DATE_TIME_FORMATTER);
        }
        return value;
    }

    private long safeLong(Long value) {
        return value == null ? 0 : value;
    }

    private BigDecimal safeBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private ModuleQueryDTO query(int page, int pageSize, String keyword, String startTime, String endTime) {
        ModuleQueryDTO query = new ModuleQueryDTO();
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setKeyword(keyword);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        return query;
    }

    private int safePage(ModuleQueryDTO query) {
        return Math.max(1, query == null ? 1 : query.getPage());
    }

    private int safePageSize(ModuleQueryDTO query) {
        int pageSize = query == null ? 20 : query.getPageSize();
        return Math.max(1, Math.min(pageSize, 100));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }



    private Map<String, ModuleQueryConfig> buildModuleQueryConfigs() {
        Map<String, ModuleQueryConfig> configs = new HashMap<>();
        // 列表查询白名单：每个模块声明自己的主表、时间字段、排序字段和允许模糊查询的字段。
        configs.put("customers", new ModuleQueryConfig("logistics_customer", "created_at", "id", "customer_code", "customer_name", "contact_name", "contact_phone", "city", "address", "status"));
        configs.put("orders", new ModuleQueryConfig("logistics_order", "created_at", "id", "order_no", "customer_name", "sender_address", "receiver_address", "cargo_name", "status"));
        configs.put("waybills", new ModuleQueryConfig("logistics_waybill", "create_time", "id", "waybill_no", "order_no", "start_site", "target_site", "current_location", "transport_status"));
        configs.put("dispatches", new ModuleQueryConfig("logistics_dispatch", "create_time", "id", "order_no", "waybill_no", "driver_name", "vehicle_no", "start_site", "target_site", "dispatch_status"));
        configs.put("tasks", new ModuleQueryConfig("logistics_task", "create_time", "id", "task_no", "order_no", "driver_name", "vehicle_no", "task_status"));
        configs.put("tracks", new ModuleQueryConfig("logistics_track", "operation_time", "id", "order_no", "waybill_no", "current_status", "current_location", "operator_name", "operation_desc"));
        configs.put("drivers", new ModuleQueryConfig("logistics_driver", "created_at", "id", "driver_code", "driver_name", "phone", "license_no", "license_type", "status"));
        configs.put("vehicles", new ModuleQueryConfig("logistics_vehicle", "created_at", "id", "vehicle_no", "vehicle_type", "current_city", "status"));
        configs.put("exceptions", new ModuleQueryConfig("logistics_exception", "report_time", "id", "order_no", "exception_type", "exception_desc", "exception_status", "report_user", "handle_user"));
        configs.put("fees", new ModuleQueryConfig("logistics_fee", "create_time", "id", "order_no", "payment_status"));
        configs.put("users", new ModuleQueryConfig("sys_user", "create_time", "id", "user_code", "username", "real_name", "mobile", "email", "role_name"));
        configs.put("roles", new ModuleQueryConfig("sys_role", "create_time", "id", "role_code", "role_name", "status"));
        configs.put("operationLogs", new ModuleQueryConfig("sys_operation_log", "operation_time", "id", "operation_id", "trace_id", "user_id", "user_code", "username", "role_code", "operation", "request_uri", "request_method", "operation_status", "error_message", "client_ip", "target_id", "request_params"));
        configs.put("files", new ModuleQueryConfig("sys_uploaded_file", "upload_time", "id", "original_name", "relative_path", "content_type", "upload_user"));
        return configs;
    }

    private String moduleDisplayName(String module) {
        Map<String, String> names = new HashMap<>();
        names.put("customers", "客户管理");
        names.put("orders", "运单管理");
        names.put("waybills", "运单中心");
        names.put("dispatches", "调度管理");
        names.put("tasks", "运输任务");
        names.put("tracks", "物流轨迹");
        names.put("drivers", "司机管理");
        names.put("vehicles", "车辆管理");
        names.put("exceptions", "异常管理");
        names.put("fees", "费用结算");
        names.put("users", "用户管理");
        names.put("roles", "角色管理");
        names.put("operationLogs", "操作日志");
        names.put("files", "上传文件");
        return names.getOrDefault(module, module);
    }

    private static class ModuleQueryConfig {
        private final String tableName;
        private final String timeColumn;
        private final String orderColumn;
        private final List<String> keywordColumns;

        private ModuleQueryConfig(String tableName, String timeColumn, String orderColumn, String... keywordColumns) {
            this.tableName = tableName;
            this.timeColumn = timeColumn;
            this.orderColumn = orderColumn;
            this.keywordColumns = Arrays.asList(keywordColumns);
        }
    }
}
