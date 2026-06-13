package jimmy.logistics.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import jimmy.logistics.config.LogisticsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 物流订单 RabbitMQ 配置 —— 声明 Exchange/Queue/Binding，消息持久化。
 */
@Configuration
@EnableConfigurationProperties(LogisticsProperties.class)
public class LogisticsRabbitMqConfig {

    @Bean
    public DirectExchange logisticsOrderExchange(LogisticsProperties properties) {
        // 物流订单独立使用一个交换机，避免与通用演示消息混在一起。
        return new DirectExchange(properties.getMq().getOrderExchange(), true, false);
    }

    @Bean
    public Queue logisticsOrderCreatedQueue(LogisticsProperties properties) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", properties.getMq().getOrderExchange() + ".dlx");
        args.put("x-dead-letter-routing-key", properties.getMq().getOrderCreatedRoutingKey() + ".dlq");
        return new Queue(properties.getMq().getOrderCreatedQueue(), true, false, false, args);
    }

    @Bean
    public DirectExchange logisticsOrderDeadLetterExchange(LogisticsProperties properties) {
        return new DirectExchange(properties.getMq().getOrderExchange() + ".dlx", true, false);
    }

    @Bean
    public Queue logisticsOrderDeadLetterQueue(LogisticsProperties properties) {
        return new Queue(properties.getMq().getOrderCreatedQueue() + ".dlq", true);
    }

    @Bean
    public Binding logisticsOrderDeadLetterBinding(@Qualifier("logisticsOrderDeadLetterQueue") Queue dlq,
                                                    @Qualifier("logisticsOrderDeadLetterExchange") DirectExchange dlx,
                                                    LogisticsProperties properties) {
        return BindingBuilder.bind(dlq)
                .to(dlx)
                .with(properties.getMq().getOrderCreatedRoutingKey() + ".dlq");
    }

    @Bean
    public Binding logisticsOrderCreatedBinding(@Qualifier("logisticsOrderCreatedQueue") Queue logisticsOrderCreatedQueue,
                                                @Qualifier("logisticsOrderExchange") DirectExchange logisticsOrderExchange,
                                                LogisticsProperties properties) {
        // 订单创建事件单独绑定队列，后续可扩展更多 routing key 处理不同物流事件。
        return BindingBuilder.bind(logisticsOrderCreatedQueue)
                .to(logisticsOrderExchange)
                .with(properties.getMq().getOrderCreatedRoutingKey());
    }
}
