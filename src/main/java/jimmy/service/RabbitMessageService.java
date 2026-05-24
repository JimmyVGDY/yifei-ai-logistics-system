package jimmy.service;

import jimmy.config.RabbitMqProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class RabbitMessageService {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMqProperties properties;

    public RabbitMessageService(RabbitTemplate rabbitTemplate, RabbitMqProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void sendDemoMessage(String message) {
        rabbitTemplate.convertAndSend(
                properties.getDemoExchange(),
                properties.getDemoRoutingKey(),
                message
        );
    }
}
