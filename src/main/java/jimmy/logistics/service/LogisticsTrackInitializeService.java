package jimmy.logistics.service;

import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.entity.LogisticsOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LogisticsTrackInitializeService {

    private final JdbcTemplate jdbcTemplate;
    private final CompactSnowflakeIdGenerator idGenerator;

    public LogisticsTrackInitializeService(JdbcTemplate jdbcTemplate,
                                           CompactSnowflakeIdGenerator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
    }

    public void initializeCreatedTrack(LogisticsOrder order) {
        Integer exists = jdbcTemplate.queryForObject(
                "select count(1) from logistics_track where order_id = ? and operation_desc = ?",
                Integer.class,
                order.getId(),
                "订单创建后自动初始化轨迹"
        );
        if (exists != null && exists > 0) {
            return;
        }
        jdbcTemplate.update(
                "insert into logistics_track (id, order_id, waybill_id, current_status, current_location, operator_name, operation_desc, operation_time) " +
                        "values (?, ?, 0, ?, ?, ?, ?, current_timestamp)",
                idGenerator.nextId(),
                order.getId(),
                order.getStatus(),
                "订单中心",
                "系统",
                "订单创建后自动初始化轨迹"
        );
        log.info("订单创建轨迹已初始化，orderNo={}", order.getOrderNo());
    }
}
