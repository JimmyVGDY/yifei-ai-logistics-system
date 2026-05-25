package jimmy.logistics.service;

import jimmy.config.LogisticsProperties;
import jimmy.logistics.entity.LogisticsOrder;
import jimmy.logistics.model.LogisticsOrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class LogisticsOrderMessageService {

    private final RabbitTemplate rabbitTemplate;
    private final LogisticsProperties logisticsProperties;

    public LogisticsOrderMessageService(RabbitTemplate rabbitTemplate,
                                        LogisticsProperties logisticsProperties) {
        this.rabbitTemplate = rabbitTemplate;
        this.logisticsProperties = logisticsProperties;
    }

    public void publishOrderCreated(LogisticsOrder logisticsOrder) {
        LogisticsOrderEvent event = new LogisticsOrderEvent(
                "ORDER_CREATED",
                logisticsOrder.getOrderNo(),
                logisticsOrder.getStatus(),
                LocalDateTime.now()
        );
        rabbitTemplate.convertAndSend(
                logisticsProperties.getMq().getOrderExchange(),
                logisticsProperties.getMq().getOrderCreatedRoutingKey(),
                event
        );
        log.info("物流订单创建事件已发送，orderNo={}, exchange={}, routingKey={}",
                logisticsOrder.getOrderNo(),
                logisticsProperties.getMq().getOrderExchange(),
                logisticsProperties.getMq().getOrderCreatedRoutingKey());
    }
}
