package jimmy.logistics.service;

import jimmy.common.trace.TraceContextSupport;
import jimmy.logistics.entity.LogisticsOrder;
import jimmy.logistics.mapper.LogisticsOrderMapper;
import jimmy.logistics.model.LogisticsOrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 物流订单事件监听器：消费 ORDER_CREATED 消息，并初始化物流轨迹。
 */
@Slf4j
@Component
public class LogisticsOrderEventListener {

    private final LogisticsOrderMapper logisticsOrderMapper;
    private final LogisticsTrackInitializeService trackInitializeService;
    private final TraceContextSupport traceContextSupport;

    public LogisticsOrderEventListener(LogisticsOrderMapper logisticsOrderMapper,
                                       LogisticsTrackInitializeService trackInitializeService,
                                       TraceContextSupport traceContextSupport) {
        this.logisticsOrderMapper = logisticsOrderMapper;
        this.trackInitializeService = trackInitializeService;
        this.traceContextSupport = traceContextSupport;
    }

    /**
     * 消费订单创建事件：恢复消息中的链路上下文后，再查询订单详情并初始化首条轨迹。
     */
    @RabbitListener(queues = "${app.logistics.mq.order-created-queue:logistics.order.created.queue}")
    public void handleOrderCreated(LogisticsOrderEvent event) {
        if (event == null || !"ORDER_CREATED".equals(event.getEventType())) {
            return;
        }
        boolean generatedTraceId = event.getTraceId() == null || event.getTraceId().trim().isEmpty();
        traceContextSupport.restoreEventContext(event);
        try {
            if (generatedTraceId) {
                log.info("订单创建事件缺少 traceId，消费端已补生成，orderNo={}", event.getOrderNo());
            }
            LogisticsOrder order = logisticsOrderMapper.findByOrderNo(event.getOrderNo());
            if (order == null) {
                log.warn("订单创建事件未找到订单，orderNo={}", event.getOrderNo());
                return;
            }
            trackInitializeService.initializeCreatedTrack(order);
            log.info("订单创建事件已消费并初始化轨迹，orderNo={}, status={}", event.getOrderNo(), event.getStatus());
        } finally {
            traceContextSupport.clearTraceContext();
        }
    }
}
