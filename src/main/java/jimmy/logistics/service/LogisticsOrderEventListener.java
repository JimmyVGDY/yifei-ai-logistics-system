package jimmy.logistics.service;

import jimmy.logistics.entity.LogisticsOrder;
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
    private final LogisticsTrackInitializeService trackInitializeService;

    public LogisticsOrderEventListener(JdbcTemplate jdbcTemplate,
                                       LogisticsTrackInitializeService trackInitializeService) {
        this.jdbcTemplate = jdbcTemplate;
        this.trackInitializeService = trackInitializeService;
    }

    @RabbitListener(queues = "${app.logistics.mq.order-created-queue:logistics.order.created.queue}")
    public void handleOrderCreated(LogisticsOrderEvent event) {
        if (event == null || !"ORDER_CREATED".equals(event.getEventType())) {
            return;
        }
        List<LogisticsOrder> orders = jdbcTemplate.query(
                "select id, order_no, customer_name, sender_address, receiver_address, cargo_name, cargo_weight, status, created_at, updated_at from logistics_order where order_no = ?",
                (rs, rowNum) -> {
                    LogisticsOrder order = new LogisticsOrder();
                    order.setId(rs.getLong("id"));
                    order.setOrderNo(rs.getString("order_no"));
                    order.setStatus(rs.getString("status"));
                    return order;
                },
                event.getOrderNo()
        );
        if (orders.isEmpty()) {
            log.warn("订单创建事件未找到订单，orderNo={}", event.getOrderNo());
            return;
        }
        trackInitializeService.initializeCreatedTrack(orders.get(0));
        log.info("订单创建事件已消费并初始化轨迹，orderNo={}, status={}", event.getOrderNo(), event.getStatus());
    }
}
