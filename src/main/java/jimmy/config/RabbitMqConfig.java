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
        return BindingBuilder.bind(demoQueue)
                .to(demoExchange)
                .with(properties.getDemoRoutingKey());
    }
}
