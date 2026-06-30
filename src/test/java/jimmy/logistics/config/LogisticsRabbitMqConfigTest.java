package jimmy.logistics.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class LogisticsRabbitMqConfigTest {

    @Test
    void orderCreatedQueueShouldDeclareDeadLetterArguments() {
        LogisticsRabbitMqConfig config = new LogisticsRabbitMqConfig();
        LogisticsProperties properties = new LogisticsProperties();

        Queue queue = config.logisticsOrderCreatedQueue(properties);

        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", "logistics.order.exchange.dlx")
                .containsEntry("x-dead-letter-routing-key", "logistics.order.created.dlq");
    }
}
