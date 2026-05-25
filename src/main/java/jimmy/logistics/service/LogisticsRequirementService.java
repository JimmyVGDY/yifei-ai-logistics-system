package jimmy.logistics.service;

import jimmy.logistics.model.LogisticsDashboardSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
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

@Slf4j
@Service
public class LogisticsRequirementService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat LEGACY_DATE_TIME_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, String> moduleSql;
    private final Map<String, ModuleQueryConfig> moduleQueryConfigs;

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
        return moduleRecords(module, limit, null, null, null);
    }

    public List<Map<String, Object>> moduleRecords(String module, int limit, String keyword, String startTime, String endTime) {
        String sql = moduleSql.get(module);
        if (sql == null) {
            log.warn("查询物流模块列表失败：不支持的模块，module={}", module);
            return Collections.emptyList();
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        ModuleQueryConfig queryConfig = moduleQueryConfigs.get(module);
        QueryBuildResult queryBuildResult = buildFilteredSql(sql, queryConfig, keyword, startTime, endTime);
        queryBuildResult.args.add(safeLimit);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(queryBuildResult.sql, queryBuildResult.args.toArray());
        records = formatDateTimeValues(records);
        log.info("查询物流模块列表完成，module={}, limit={}, safeLimit={}, keyword={}, startTime={}, endTime={}, resultSize={}",
                module, limit, safeLimit, keyword, startTime, endTime, records.size());
        return records;
    }

    private QueryBuildResult buildFilteredSql(String sql,
                                              ModuleQueryConfig queryConfig,
                                              String keyword,
                                              String startTime,
                                              String endTime) {
        String lowerSql = sql.toLowerCase();
        int orderByIndex = lowerSql.lastIndexOf(" order by ");
        String selectPart = orderByIndex < 0 ? sql : sql.substring(0, orderByIndex);
        String orderPart = orderByIndex < 0 ? " order by id desc limit ?" : sql.substring(orderByIndex);
        StringBuilder builder = new StringBuilder(selectPart).append(" where 1 = 1");
        List<Object> args = new ArrayList<>();

        if (queryConfig != null && StringUtils.hasText(keyword) && !queryConfig.keywordColumns.isEmpty()) {
            builder.append(" and (");
            for (int i = 0; i < queryConfig.keywordColumns.size(); i++) {
                if (i > 0) {
                    builder.append(" or ");
                }
                builder.append(queryConfig.keywordColumns.get(i)).append(" like ?");
                args.add("%" + keyword.trim() + "%");
            }
            builder.append(")");
        }
        if (queryConfig != null && StringUtils.hasText(queryConfig.timeColumn) && StringUtils.hasText(startTime)) {
            builder.append(" and ").append(queryConfig.timeColumn).append(" >= ?");
            args.add(startTime.trim());
        }
        if (queryConfig != null && StringUtils.hasText(queryConfig.timeColumn) && StringUtils.hasText(endTime)) {
            builder.append(" and ").append(queryConfig.timeColumn).append(" <= ?");
            args.add(endTime.trim());
        }

        return new QueryBuildResult(builder.append(orderPart).toString(), args);
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
        sql.put("users", "select u.id, u.username, u.real_name, u.mobile, u.email, u.password, u.role_id, r.role_name, u.status, u.create_time, u.update_time from sys_user u left join sys_role r on r.id = u.role_id");
        sql.put("roles", "select id, role_code, role_name, status, create_time, update_time from sys_role");
        sql.put("operationLogs", "select id, username, operation, request_uri, request_method, operation_status, operation_time from sys_operation_log");
        sql.put("files", "select id, original_name, relative_path, file_size, content_type, upload_user, upload_time from sys_uploaded_file");
        return sql;
    }

    private Map<String, ModuleQueryConfig> buildModuleQueryConfigs() {
        Map<String, ModuleQueryConfig> configs = new HashMap<>();
        configs.put("customers", new ModuleQueryConfig("created_at", "customer_code", "customer_name", "contact_name", "contact_phone", "city", "address", "status"));
        configs.put("orders", new ModuleQueryConfig("created_at", "order_no", "customer_name", "sender_address", "receiver_address", "cargo_name", "status"));
        configs.put("waybills", new ModuleQueryConfig("w.create_time", "w.waybill_no", "o.order_no", "w.start_site", "w.target_site", "w.current_location", "w.transport_status"));
        configs.put("dispatches", new ModuleQueryConfig("d.create_time", "o.order_no", "w.waybill_no", "dr.driver_name", "v.vehicle_no", "d.start_site", "d.target_site", "d.dispatch_status"));
        configs.put("tasks", new ModuleQueryConfig("t.create_time", "t.task_no", "o.order_no", "dr.driver_name", "v.vehicle_no", "t.task_status"));
        configs.put("tracks", new ModuleQueryConfig("tr.operation_time", "o.order_no", "w.waybill_no", "tr.current_status", "tr.current_location", "tr.operator_name", "tr.operation_desc"));
        configs.put("drivers", new ModuleQueryConfig("created_at", "driver_code", "driver_name", "phone", "license_no", "license_type", "status"));
        configs.put("vehicles", new ModuleQueryConfig("created_at", "vehicle_no", "vehicle_type", "current_city", "status"));
        configs.put("exceptions", new ModuleQueryConfig("e.report_time", "o.order_no", "e.exception_type", "e.exception_desc", "e.exception_status", "e.report_user", "e.handle_user"));
        configs.put("fees", new ModuleQueryConfig("f.create_time", "o.order_no", "f.payment_status"));
        configs.put("users", new ModuleQueryConfig("u.create_time", "u.username", "u.real_name", "u.mobile", "u.email", "r.role_name"));
        configs.put("roles", new ModuleQueryConfig("create_time", "role_code", "role_name"));
        configs.put("operationLogs", new ModuleQueryConfig("operation_time", "username", "operation", "request_uri", "request_method", "operation_status"));
        configs.put("files", new ModuleQueryConfig("upload_time", "original_name", "relative_path", "content_type", "upload_user"));
        return configs;
    }

    private static class ModuleQueryConfig {
        private final String timeColumn;
        private final List<String> keywordColumns;

        private ModuleQueryConfig(String timeColumn, String... keywordColumns) {
            this.timeColumn = timeColumn;
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
