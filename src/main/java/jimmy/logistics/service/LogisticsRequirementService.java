package jimmy.logistics.service;

import jimmy.logistics.model.LogisticsDashboardSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LogisticsRequirementService {

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, String> moduleSql;

    public LogisticsRequirementService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.moduleSql = buildModuleSql();
    }

    public LogisticsDashboardSummary dashboardSummary() {
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
        return summary;
    }

    public List<Map<String, Object>> moduleRecords(String module, int limit) {
        String sql = moduleSql.get(module);
        if (sql == null) {
            return Collections.emptyList();
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.queryForList(sql, safeLimit);
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
        sql.put("customers", "select id, customer_code, customer_name, contact_name, contact_phone, city, status from logistics_customer order by id desc limit ?");
        sql.put("orders", "select id, order_no, customer_name, sender_address, receiver_address, cargo_name, cargo_weight, status from logistics_order order by id desc limit ?");
        sql.put("waybills", "select w.id, w.waybill_no, o.order_no, w.start_site, w.target_site, w.current_location, w.transport_status from logistics_waybill w join logistics_order o on o.id = w.order_id order by w.id desc limit ?");
        sql.put("dispatches", "select d.id, o.order_no, w.waybill_no, dr.driver_name, v.vehicle_no, d.start_site, d.target_site, d.dispatch_status from logistics_dispatch d join logistics_order o on o.id = d.order_id join logistics_waybill w on w.id = d.waybill_id join logistics_driver dr on dr.id = d.driver_id join logistics_vehicle v on v.id = d.vehicle_id order by d.id desc limit ?");
        sql.put("tasks", "select t.id, t.task_no, o.order_no, dr.driver_name, v.vehicle_no, t.task_status, t.create_time from logistics_task t join logistics_order o on o.id = t.order_id join logistics_driver dr on dr.id = t.driver_id join logistics_vehicle v on v.id = t.vehicle_id order by t.id desc limit ?");
        sql.put("tracks", "select tr.id, o.order_no, w.waybill_no, tr.current_status, tr.current_location, tr.operator_name, tr.operation_desc, tr.operation_time from logistics_track tr join logistics_order o on o.id = tr.order_id join logistics_waybill w on w.id = tr.waybill_id order by tr.operation_time desc limit ?");
        sql.put("drivers", "select id, driver_code, driver_name, phone, license_no, license_type, status from logistics_driver order by id desc limit ?");
        sql.put("vehicles", "select id, vehicle_no, vehicle_type, load_capacity_kg, volume_capacity_cubic, current_city, status from logistics_vehicle order by id desc limit ?");
        sql.put("exceptions", "select e.id, o.order_no, e.exception_type, e.exception_desc, e.exception_status, e.report_user, e.report_time from logistics_exception e join logistics_order o on o.id = e.order_id order by e.id desc limit ?");
        sql.put("fees", "select f.id, o.order_no, f.base_fee, f.weight_fee, f.distance_fee, f.payable_fee, f.actual_fee, f.payment_status from logistics_fee f join logistics_order o on o.id = f.order_id order by f.id desc limit ?");
        sql.put("users", "select u.id, u.username, u.real_name, u.mobile, u.email, r.role_name, u.status from sys_user u left join sys_role r on r.id = u.role_id order by u.id desc limit ?");
        sql.put("roles", "select id, role_code, role_name, status, create_time from sys_role order by id desc limit ?");
        return sql;
    }
}
