package jimmy.logistics.service;

import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.config.LogisticsProperties;
import jimmy.common.trace.TraceContextSupport;
import jimmy.logistics.entity.LogisticsOrder;
import jimmy.logistics.model.LogisticsOrderEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LogisticsOrderMessageServiceTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldPublishOrderEventWithTraceContext() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        TraceContextSupport traceContextSupport = new TraceContextSupport(mock(CompactSnowflakeIdGenerator.class));
        LogisticsOrderMessageService service = new LogisticsOrderMessageService(
                rabbitTemplate,
                new LogisticsProperties(),
                traceContextSupport
        );
        MDC.put(TraceContextSupport.TRACE_ID, "trace-order");
        MDC.put(TraceContextSupport.OPERATION_ID, "op-order");
        MDC.put(TraceContextSupport.LOGIN_SESSION_ID, "login-session-order");
        MDC.put(TraceContextSupport.USER_ID, "260602001");
        MDC.put(TraceContextSupport.USER_CODE, "U-001");
        MDC.put(TraceContextSupport.USERNAME_MASKED, "a***n");
        MDC.put(TraceContextSupport.ROLE_CODE, "ADMIN");
        LogisticsOrder order = new LogisticsOrder();
        order.setOrderNo("LO-TRACE-001");
        order.setStatus("CREATED");

        service.publishOrderCreated(order);

        ArgumentCaptor<LogisticsOrderEvent> eventCaptor = ArgumentCaptor.forClass(LogisticsOrderEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq("logistics.order.exchange"),
                eq("logistics.order.created"),
                eventCaptor.capture(),
                org.mockito.ArgumentMatchers.any(MessagePostProcessor.class)
        );
        LogisticsOrderEvent event = eventCaptor.getValue();
        assertThat(event.getTraceId()).isEqualTo("trace-order");
        assertThat(event.getOperationId()).isEqualTo("op-order");
        assertThat(event.getLoginSessionId()).isEqualTo("login-session-order");
        assertThat(event.getUserId()).isEqualTo("260602001");
        assertThat(event.getUserCode()).isEqualTo("U-001");
        assertThat(event.getUsernameMasked()).isEqualTo("a***n");
        assertThat(event.getRoleCode()).isEqualTo("ADMIN");
    }
}
