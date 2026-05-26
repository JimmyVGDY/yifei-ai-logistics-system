package jimmy.logistics.service;

import jimmy.logistics.entity.LogisticsOrder;
import jimmy.logistics.mapper.LogisticsOrderMapper;
import jimmy.logistics.model.LogisticsOrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LogisticsOrderEventListener {

    private final LogisticsOrderMapper logisticsOrderMapper;
    private final LogisticsTrackInitializeService trackInitializeService;

    public LogisticsOrderEventListener(LogisticsOrderMapper logisticsOrderMapper,
                                       LogisticsTrackInitializeService trackInitializeService) {
        this.logisticsOrderMapper = logisticsOrderMapper;
        this.trackInitializeService = trackInitializeService;
    }

    @RabbitListener(queues = "${app.logistics.mq.order-created-queue:logistics.order.created.queue}")
    public void handleOrderCreated(LogisticsOrderEvent event) {
        if (event == null || !"ORDER_CREATED".equals(event.getEventType())) {
            return;
        }
        LogisticsOrder order = logisticsOrderMapper.findByOrderNo(event.getOrderNo());
        if (order == null) {
            log.warn("订单创建事件未找到订单，orderNo={}", event.getOrderNo());
            return;
        }
        trackInitializeService.initializeCreatedTrack(order);
        log.info("订单创建事件已消费并初始化轨迹，orderNo={}, status={}", event.getOrderNo(), event.getStatus());
    }
}
