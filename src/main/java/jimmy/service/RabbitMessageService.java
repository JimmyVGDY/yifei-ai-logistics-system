package jimmy.service;

import jimmy.config.RabbitMqProperties;
import jimmy.util.LogMaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RabbitMessageService {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMqProperties properties;

    public RabbitMessageService(RabbitTemplate rabbitTemplate, RabbitMqProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void sendDemoMessage(String message) {
        log.info("发送 RabbitMQ 演示消息，exchange={}, routingKey={}, message={}",
                properties.getDemoExchange(), properties.getDemoRoutingKey(), LogMaskUtils.maskText(message));
        rabbitTemplate.convertAndSend(
                properties.getDemoExchange(),
                properties.getDemoRoutingKey(),
                message
        );
        log.info("RabbitMQ 演示消息发送完成，exchange={}, routingKey={}", properties.getDemoExchange(), properties.getDemoRoutingKey());
    }
}
