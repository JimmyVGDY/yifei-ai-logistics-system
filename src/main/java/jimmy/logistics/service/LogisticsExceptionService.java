package jimmy.logistics.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.model.ExceptionHandleDTO;
import jimmy.logistics.model.ExceptionReportDTO;
import jimmy.logistics.model.SimpleResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class LogisticsExceptionService {

    private final JdbcTemplate jdbcTemplate;
    private final CompactSnowflakeIdGenerator idGenerator;

    public LogisticsExceptionService(JdbcTemplate jdbcTemplate,
                                     CompactSnowflakeIdGenerator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
    }

    public SimpleResultVO reportException(ExceptionReportDTO request) {
        Long orderId = findOrderId(request.getOrderNo());
        Long taskId = findTaskId(orderId);
        String reportUser = currentUser();
        Long exceptionId = idGenerator.nextId();
        jdbcTemplate.update(
                "insert into logistics_exception (id, order_id, task_id, exception_type, exception_desc, exception_status, report_user, report_time, handle_user, handle_time) " +
                        "values (?, ?, ?, ?, ?, 'WAIT_HANDLE', ?, current_timestamp, null, null)",
                exceptionId, orderId, taskId, request.getExceptionType(), request.getExceptionDesc(), reportUser
        );
        jdbcTemplate.update("update logistics_order set status = 'EXCEPTION', updated_at = current_timestamp where id = ?", orderId);
        log.info("运输异常已上报，orderNo={}, exceptionType={}, reportUser={}",
                request.getOrderNo(), request.getExceptionType(), reportUser);
        return new SimpleResultVO().add("exceptionId", exceptionId).add("status", "WAIT_HANDLE");
    }

    public SimpleResultVO handleException(long exceptionId, ExceptionHandleDTO request) {
        String status = request == null || !StringUtils.hasText(request.getExceptionStatus())
                ? "CLOSED"
                : request.getExceptionStatus().trim();
        String handleUser = currentUser();
        int updated = jdbcTemplate.update(
                "update logistics_exception set exception_status = ?, handle_user = ?, handle_time = current_timestamp where id = ?",
                status, handleUser, exceptionId
        );
        if (updated == 0) {
            throw new IllegalArgumentException("异常记录不存在");
        }
        log.info("运输异常已处理，exceptionId={}, status={}, handleUser={}", exceptionId, status, handleUser);
        return new SimpleResultVO().add("exceptionId", exceptionId).add("status", status);
    }

    private Long findOrderId(String orderNo) {
        List<Long> ids = jdbcTemplate.queryForList("select id from logistics_order where order_no = ?", Long.class, orderNo);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("订单不存在");
        }
        return ids.get(0);
    }

    private Long findTaskId(Long orderId) {
        List<Long> ids = jdbcTemplate.queryForList("select id from logistics_task where order_id = ? order by id desc limit 1", Long.class, orderId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String currentUser() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId == null ? "admin" : String.valueOf(loginId);
    }
}
