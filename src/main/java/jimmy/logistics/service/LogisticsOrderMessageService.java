package jimmy.logistics.service;

import jimmy.logistics.config.LogisticsProperties;
import jimmy.common.trace.TraceContextSupport;
import jimmy.logistics.entity.LogisticsOrder;
import jimmy.logistics.model.LogisticsOrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 物流订单消息服务：订单创建后向 RabbitMQ 发布事件。
 */
@Slf4j
@Service
public class LogisticsOrderMessageService {

    private final RabbitTemplate rabbitTemplate;
    private final LogisticsProperties logisticsProperties;
    private final TraceContextSupport traceContextSupport;

    public LogisticsOrderMessageService(RabbitTemplate rabbitTemplate,
                                        LogisticsProperties logisticsProperties,
                                        TraceContextSupport traceContextSupport) {
        this.rabbitTemplate = rabbitTemplate;
        this.logisticsProperties = logisticsProperties;
        this.traceContextSupport = traceContextSupport;
    }

    /**
     * 发布 ORDER_CREATED 事件，并把当前请求链路、操作和登录会话标识复制到消息体与消息头。
     */
    public void publishOrderCreated(LogisticsOrder logisticsOrder) {
        LogisticsOrderEvent event = new LogisticsOrderEvent(
                "ORDER_CREATED",
                logisticsOrder.getOrderNo(),
                logisticsOrder.getStatus(),
                LocalDateTime.now()
        );
        traceContextSupport.captureEventContext(event);
        rabbitTemplate.convertAndSend(
                logisticsProperties.getMq().getOrderExchange(),
                logisticsProperties.getMq().getOrderCreatedRoutingKey(),
                event,
                message -> {
                    // 消息持久化到磁盘，RabbitMQ 重启后尽量不丢失。
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    message.getMessageProperties().setHeader("traceId", event.getTraceId());
                    message.getMessageProperties().setHeader("operationId", event.getOperationId());
                    message.getMessageProperties().setHeader("loginSessionId", event.getLoginSessionId());
                    return message;
                }
        );
        log.info("订单创建事件已发送，orderNo={}, traceId={}, operationId={}, loginSessionId={}, exchange={}, routingKey={}",
                logisticsOrder.getOrderNo(),
                event.getTraceId(),
                event.getOperationId(),
                event.getLoginSessionId(),
                logisticsProperties.getMq().getOrderExchange(),
                logisticsProperties.getMq().getOrderCreatedRoutingKey());
    }
}
