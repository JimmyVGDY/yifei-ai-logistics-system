package jimmy.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LogisticsProperties.class)
public class LogisticsRabbitMqConfig {

    @Bean
    public DirectExchange logisticsOrderExchange(LogisticsProperties properties) {
        return new DirectExchange(properties.getMq().getOrderExchange(), true, false);
    }

    @Bean
    public Queue logisticsOrderCreatedQueue(LogisticsProperties properties) {
        return new Queue(properties.getMq().getOrderCreatedQueue(), true);
    }

    @Bean
    public Binding logisticsOrderCreatedBinding(Queue logisticsOrderCreatedQueue,
                                                DirectExchange logisticsOrderExchange,
                                                LogisticsProperties properties) {
        return BindingBuilder.bind(logisticsOrderCreatedQueue)
                .to(logisticsOrderExchange)
                .with(properties.getMq().getOrderCreatedRoutingKey());
    }
}
