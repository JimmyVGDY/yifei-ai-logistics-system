package jimmy.logistics.service;

import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.model.LogisticsOrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class LogisticsOrderEventListener {

    private final JdbcTemplate jdbcTemplate;
    private final CompactSnowflakeIdGenerator idGenerator;

    public LogisticsOrderEventListener(JdbcTemplate jdbcTemplate, CompactSnowflakeIdGenerator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
    }

    @RabbitListener(queues = "${app.logistics.mq.order-created-queue:logistics.order.created.queue}")
    public void handleOrderCreated(LogisticsOrderEvent event) {
        if (event == null || !"ORDER_CREATED".equals(event.getEventType())) {
            return;
        }
        List<Long> orderIds = jdbcTemplate.queryForList(
                "select id from logistics_order where order_no = ?",
                Long.class,
                event.getOrderNo()
        );
        if (orderIds.isEmpty()) {
            log.warn("订单创建事件未找到订单，orderNo={}", event.getOrderNo());
            return;
        }
        Long orderId = orderIds.get(0);
        Long existed = jdbcTemplate.queryForObject(
                "select count(1) from logistics_track where order_id = ? and operation_desc = ?",
                Long.class,
                orderId,
                "订单创建后自动初始化轨迹"
        );
        if (existed != null && existed > 0) {
            return;
        }
        jdbcTemplate.update(
                "insert into logistics_track (id, order_id, waybill_id, current_status, current_location, operator_name, operation_desc, operation_time) " +
                        "values (?, ?, 0, ?, ?, ?, ?, current_timestamp)",
                idGenerator.nextId(),
                orderId,
                event.getStatus(),
                "订单中心",
                "系统",
                "订单创建后自动初始化轨迹"
        );
        log.info("订单创建事件已消费并初始化轨迹，orderNo={}, status={}", event.getOrderNo(), event.getStatus());
    }
}
