package jimmy.messaging.config;

import org.springframework.beans.factory.annotation.Qualifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 演示消息配置 —— 声明演示 Exchange/Queue/Binding + JSON 消息转换器。
 */
@Configuration
@EnableConfigurationProperties(RabbitMqProperties.class)
public class RabbitMqConfig {

    @Bean
    public MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        // 统一使用 JSON 消息转换器，支持包含 LocalDateTime 的物流事件对象。
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public DirectExchange demoExchange(RabbitMqProperties properties) {
        // DirectExchange 用 routing key 精确路由，适合作为本地消息队列联调示例。
        return new DirectExchange(properties.getDemoExchange(), true, false);
    }

    @Bean
    public Queue demoQueue(RabbitMqProperties properties) {
        return new Queue(properties.getDemoQueue(), true);
    }

    @Bean
    public Binding demoBinding(@Qualifier("demoQueue") Queue demoQueue,
                               @Qualifier("demoExchange") DirectExchange demoExchange,
                               RabbitMqProperties properties) {
        // 将队列绑定到交换机后，生产者只需要发送到 exchange + routing key。
        return BindingBuilder.bind(demoQueue)
                .to(demoExchange)
                .with(properties.getDemoRoutingKey());
    }
}
