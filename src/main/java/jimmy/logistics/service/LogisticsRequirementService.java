package jimmy.logistics.service;

import jimmy.logistics.model.LogisticsDashboardSummary;
import jimmy.logistics.model.ModuleQueryDTO;
import jimmy.logistics.model.ModuleRecordVO;
import jimmy.model.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class LogisticsRequirementService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat LEGACY_DATE_TIME_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, String> moduleSql;
    private final Map<String, ModuleQueryConfig> moduleQueryConfigs;
    private final Map<String, Boolean> columnExistsCache = new ConcurrentHashMap<>();

    public LogisticsRequirementService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.moduleSql = buildModuleSql();
        this.moduleQueryConfigs = buildModuleQueryConfigs();
    }

    public LogisticsDashboardSummary dashboardSummary() {
        log.info("开始查询物流运营看板统计数据");
        LogisticsDashboardSummary summary = new LogisticsDashboardSummary();
        summary.setTodayOrders(count("select count(1) from logistics_order where date(created_at) = current_date"));
        summary.setCompletedOrders(count("select count(1) from logistics_order where status in ('COMPLETED', 'SIGNED', 'DELIVERED')"));
        summary.setWaitDispatchOrders(count("select count(1) from logistics_order where status in ('WAIT_DISPATCH', 'CREATED')"));
        summary.setInTransitOrders(count("select count(1) from logistics_order where status = 'IN_TRANSIT'"));
        summary.setExceptionOrders(count("select count(1) from logistics_exception where exception_status <> 'CLOSED'"));
        summary.setMonthIncome(sum("select coalesce(sum(actual_fee), 0) from logistics_fee where payment_status = 'PAID'"));
        summary.setStatusDistribution(jdbcTemplate.queryForList(
                "select status, count(1) total from logistics_order group by status order by total desc"
        ));
        summary.setRecentExceptions(jdbcTemplate.queryForList(
                "select e.id, o.order_no, e.exception_type, e.exception_status, e.report_user, e.report_time " +
                        "from logistics_exception e join logistics_order o on o.id = e.order_id " +
                        "order by e.report_time desc limit 5"
        ));
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
        String sql = resolveModuleSql(module);
        ModuleQueryConfig queryConfig = moduleQueryConfigs.get(module);
        if (sql == null || queryConfig == null) {
            log.warn("查询物流模块列表失败：不支持的模块，module={}", module);
            return new PageResult<>(Collections.emptyList(), safePage(query), safePageSize(query), 0);
        }

        int page = safePage(query);
        int pageSize = safePageSize(query);
        QueryBuildResult filtered = buildFilteredSql(sql, queryConfig, query);
        Long total = jdbcTemplate.queryForObject("select count(1) from (" + filtered.sql + ") page_count", Long.class, filtered.args.toArray());

        List<Object> pageArgs = new ArrayList<>(filtered.args);
        pageArgs.add(pageSize);
        pageArgs.add((page - 1) * pageSize);
        String pageSql = filtered.sql + " order by " + queryConfig.orderColumn + " desc limit ? offset ?";
        List<Map<String, Object>> records = jdbcTemplate.queryForList(pageSql, pageArgs.toArray());
        records = formatDateTimeValues(records);
        List<ModuleRecordVO> voRecords = new ArrayList<>();
        for (Map<String, Object> record : records) {
            voRecords.add(new ModuleRecordVO(record));
        }
        log.info("查询物流模块分页完成，module={}, page={}, pageSize={}, total={}, keyword={}, startTime={}, endTime={}",
                module, page, pageSize, total, query.getKeyword(), query.getStartTime(), query.getEndTime());
        return new PageResult<>(voRecords, page, pageSize, total == null ? 0 : total);
    }

    private QueryBuildResult buildFilteredSql(String sql, ModuleQueryConfig queryConfig, ModuleQueryDTO query) {
        StringBuilder builder = new StringBuilder(sql).append(" where 1 = 1");
        List<Object> args = new ArrayList<>();

        if (hasColumn(queryConfig.tableName, "deleted")) {
            builder.append(" and ").append(queryConfig.deletedColumn).append(" = 0");
        }
        if (StringUtils.hasText(query.getKeyword()) && !queryConfig.keywordColumns.isEmpty()) {
            builder.append(" and (");
            for (int i = 0; i < queryConfig.keywordColumns.size(); i++) {
                if (i > 0) {
                    builder.append(" or ");
                }
                builder.append(queryConfig.keywordColumns.get(i)).append(" like ?");
                args.add("%" + query.getKeyword().trim() + "%");
            }
            builder.append(")");
        }
        if (StringUtils.hasText(queryConfig.timeColumn) && StringUtils.hasText(query.getStartTime())) {
            builder.append(" and ").append(queryConfig.timeColumn).append(" >= ?");
            args.add(query.getStartTime().trim());
        }
        if (StringUtils.hasText(queryConfig.timeColumn) && StringUtils.hasText(query.getEndTime())) {
            builder.append(" and ").append(queryConfig.timeColumn).append(" <= ?");
            args.add(query.getEndTime().trim());
        }
        return new QueryBuildResult(builder.toString(), args);
    }

    private List<Map<String, Object>> formatDateTimeValues(List<Map<String, Object>> records) {
        for (int i = 0; i < records.size(); i++) {
            Map<String, Object> formatted = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : records.get(i).entrySet()) {
                formatted.put(entry.getKey(), formatValue(entry.getValue()));
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
            return LEGACY_DATE_TIME_FORMATTER.format((Timestamp) value);
        }
        return value;
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private BigDecimal sum(String sql) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class);
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

    private boolean hasColumn(String tableName, String columnName) {
        String cacheKey = tableName + "." + columnName;
        return columnExistsCache.computeIfAbsent(cacheKey, key -> {
            try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                String[] tableCandidates = {tableName, tableName.toUpperCase(), tableName.toLowerCase()};
                String[] columnCandidates = {columnName, columnName.toUpperCase(), columnName.toLowerCase()};
                for (String table : tableCandidates) {
                    for (String column : columnCandidates) {
                        try (ResultSet rs = metaData.getColumns(connection.getCatalog(), null, table, column)) {
                            if (rs.next()) {
                                return true;
                            }
                        }
                    }
                }
            } catch (Exception exception) {
                log.warn("检测表字段失败，tableName={}, columnName={}, reason={}", tableName, columnName, exception.getMessage());
            }
            return false;
        });
    }

    private String resolveModuleSql(String module) {
        if ("users".equals(module) && hasColumn("sys_user", "user_code")) {
            return "select u.id, u.user_code, u.username, u.real_name, u.mobile, u.email, u.password, u.role_id, r.role_name, u.status, u.create_time, u.update_time from sys_user u left join sys_role r on r.id = u.role_id";
        }
        return moduleSql.get(module);
    }

    private Map<String, String> buildModuleSql() {
        Map<String, String> sql = new HashMap<>();
        sql.put("customers", "select id, customer_code, customer_name, contact_name, contact_phone, province, city, address, status, created_at, updated_at from logistics_customer");
        sql.put("orders", "select id, order_no, customer_id, route_id, warehouse_id, vehicle_id, driver_id, customer_name, sender_address, receiver_address, cargo_name, cargo_weight, cargo_volume, status, planned_pickup_time, planned_delivery_time, created_at, updated_at from logistics_order");
        sql.put("waybills", "select w.id, w.waybill_no, w.order_id, o.order_no, w.start_site, w.target_site, w.current_location, w.transport_status, w.create_time, w.update_time from logistics_waybill w join logistics_order o on o.id = w.order_id");
        sql.put("dispatches", "select d.id, d.order_id, o.order_no, d.waybill_id, w.waybill_no, d.driver_id, dr.driver_name, d.vehicle_id, v.vehicle_no, d.start_site, d.target_site, d.planned_departure_time, d.planned_arrival_time, d.dispatch_status, d.create_time, d.update_time from logistics_dispatch d join logistics_order o on o.id = d.order_id join logistics_waybill w on w.id = d.waybill_id join logistics_driver dr on dr.id = d.driver_id join logistics_vehicle v on v.id = d.vehicle_id");
        sql.put("tasks", "select t.id, t.task_no, t.order_id, o.order_no, t.waybill_id, t.dispatch_id, t.driver_id, dr.driver_name, t.vehicle_id, v.vehicle_no, t.task_status, t.proof_url, t.create_time, t.update_time from logistics_task t join logistics_order o on o.id = t.order_id join logistics_driver dr on dr.id = t.driver_id join logistics_vehicle v on v.id = t.vehicle_id");
        sql.put("tracks", "select tr.id, tr.order_id, o.order_no, tr.waybill_id, w.waybill_no, tr.current_status, tr.current_location, tr.operator_name, tr.operation_desc, tr.operation_time from logistics_track tr join logistics_order o on o.id = tr.order_id join logistics_waybill w on w.id = tr.waybill_id");
        sql.put("drivers", "select id, driver_code, driver_name, phone, license_no, license_type, status, created_at, updated_at from logistics_driver");
        sql.put("vehicles", "select id, vehicle_no, vehicle_type, load_capacity_kg, volume_capacity_cubic, current_city, status, created_at, updated_at from logistics_vehicle");
        sql.put("exceptions", "select e.id, e.order_id, o.order_no, e.task_id, e.exception_type, e.exception_desc, e.exception_status, e.report_user, e.report_time, e.handle_user, e.handle_time from logistics_exception e join logistics_order o on o.id = e.order_id");
        sql.put("fees", "select f.id, f.order_id, o.order_no, f.base_fee, f.weight_fee, f.distance_fee, f.additional_fee, f.discount_fee, f.payable_fee, f.actual_fee, f.payment_status, f.create_time, f.update_time from logistics_fee f join logistics_order o on o.id = f.order_id");
        sql.put("users", "select u.id, concat('U-', u.id) user_code, u.username, u.real_name, u.mobile, u.email, u.password, u.role_id, r.role_name, u.status, u.create_time, u.update_time from sys_user u left join sys_role r on r.id = u.role_id");
        sql.put("roles", "select id, role_code, role_name, status, create_time, update_time from sys_role");
        sql.put("operationLogs", "select id, username, operation, request_uri, request_method, operation_status, operation_time from sys_operation_log");
        sql.put("files", "select id, original_name, relative_path, file_size, content_type, upload_user, upload_time from sys_uploaded_file");
        return sql;
    }

    private Map<String, ModuleQueryConfig> buildModuleQueryConfigs() {
        Map<String, ModuleQueryConfig> configs = new HashMap<>();
        configs.put("customers", new ModuleQueryConfig("logistics_customer", "deleted", "created_at", "id", "customer_code", "customer_name", "contact_name", "contact_phone", "city", "address", "status"));
        configs.put("orders", new ModuleQueryConfig("logistics_order", "deleted", "created_at", "id", "order_no", "customer_name", "sender_address", "receiver_address", "cargo_name", "status"));
        configs.put("waybills", new ModuleQueryConfig("logistics_waybill", "w.deleted", "w.create_time", "w.id", "w.waybill_no", "o.order_no", "w.start_site", "w.target_site", "w.current_location", "w.transport_status"));
        configs.put("dispatches", new ModuleQueryConfig("logistics_dispatch", "d.deleted", "d.create_time", "d.id", "o.order_no", "w.waybill_no", "dr.driver_name", "v.vehicle_no", "d.start_site", "d.target_site", "d.dispatch_status"));
        configs.put("tasks", new ModuleQueryConfig("logistics_task", "t.deleted", "t.create_time", "t.id", "t.task_no", "o.order_no", "dr.driver_name", "v.vehicle_no", "t.task_status"));
        configs.put("tracks", new ModuleQueryConfig("logistics_track", "tr.deleted", "tr.operation_time", "tr.id", "o.order_no", "w.waybill_no", "tr.current_status", "tr.current_location", "tr.operator_name", "tr.operation_desc"));
        configs.put("drivers", new ModuleQueryConfig("logistics_driver", "deleted", "created_at", "id", "driver_code", "driver_name", "phone", "license_no", "license_type", "status"));
        configs.put("vehicles", new ModuleQueryConfig("logistics_vehicle", "deleted", "created_at", "id", "vehicle_no", "vehicle_type", "current_city", "status"));
        configs.put("exceptions", new ModuleQueryConfig("logistics_exception", "e.deleted", "e.report_time", "e.id", "o.order_no", "e.exception_type", "e.exception_desc", "e.exception_status", "e.report_user", "e.handle_user"));
        configs.put("fees", new ModuleQueryConfig("logistics_fee", "f.deleted", "f.create_time", "f.id", "o.order_no", "f.payment_status"));
        configs.put("users", new ModuleQueryConfig("sys_user", "u.deleted", "u.create_time", "u.id", "u.username", "u.real_name", "u.mobile", "u.email", "r.role_name"));
        configs.put("roles", new ModuleQueryConfig("sys_role", "deleted", "create_time", "id", "role_code", "role_name"));
        configs.put("operationLogs", new ModuleQueryConfig("sys_operation_log", "deleted", "operation_time", "id", "username", "operation", "request_uri", "request_method", "operation_status"));
        configs.put("files", new ModuleQueryConfig("sys_uploaded_file", "deleted", "upload_time", "id", "original_name", "relative_path", "content_type", "upload_user"));
        return configs;
    }

    private static class ModuleQueryConfig {
        private final String tableName;
        private final String deletedColumn;
        private final String timeColumn;
        private final String orderColumn;
        private final List<String> keywordColumns;

        private ModuleQueryConfig(String tableName, String deletedColumn, String timeColumn, String orderColumn, String... keywordColumns) {
            this.tableName = tableName;
            this.deletedColumn = deletedColumn;
            this.timeColumn = timeColumn;
            this.orderColumn = orderColumn;
            this.keywordColumns = Arrays.asList(keywordColumns);
        }
    }

    private static class QueryBuildResult {
        private final String sql;
        private final List<Object> args;

        private QueryBuildResult(String sql, List<Object> args) {
            this.sql = sql;
            this.args = args;
        }
    }
}
