package jimmy.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RabbitMqProperties.class)
public class RabbitMqConfig {

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
    public Binding demoBinding(Queue demoQueue,
                               DirectExchange demoExchange,
                               RabbitMqProperties properties) {
        // 将队列绑定到交换机后，生产者只需要发送到 exchange + routing key。
        return BindingBuilder.bind(demoQueue)
                .to(demoExchange)
                .with(properties.getDemoRoutingKey());
    }
}
