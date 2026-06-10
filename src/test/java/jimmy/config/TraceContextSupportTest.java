package jimmy.common.trace;

import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.model.LogisticsOrderEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceContextSupportTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldCaptureAndRestoreEventContext() {
        CompactSnowflakeIdGenerator idGenerator = mock(CompactSnowflakeIdGenerator.class);
        TraceContextSupport support = new TraceContextSupport(idGenerator);
        MDC.put(TraceContextSupport.TRACE_ID, "trace-001");
        MDC.put(TraceContextSupport.OPERATION_ID, "op-001");
        MDC.put(TraceContextSupport.LOGIN_SESSION_ID, "session-001");
        MDC.put(TraceContextSupport.USER_ID, "260602001");
        MDC.put(TraceContextSupport.USER_CODE, "U-001");
        MDC.put(TraceContextSupport.USERNAME_MASKED, "a***n");
        MDC.put(TraceContextSupport.ROLE_CODE, "ADMIN");

        LogisticsOrderEvent event = new LogisticsOrderEvent("ORDER_CREATED", "LO-001", "CREATED", LocalDateTime.now());
        support.captureEventContext(event);
        MDC.clear();
        support.restoreEventContext(event);

        assertThat(MDC.get(TraceContextSupport.TRACE_ID)).isEqualTo("trace-001");
        assertThat(MDC.get(TraceContextSupport.OPERATION_ID)).isEqualTo("op-001");
        assertThat(MDC.get(TraceContextSupport.LOGIN_SESSION_ID)).isEqualTo("session-001");
        assertThat(MDC.get(TraceContextSupport.USER_ID)).isEqualTo("260602001");
        assertThat(MDC.get(TraceContextSupport.USER_CODE)).isEqualTo("U-001");
        assertThat(MDC.get(TraceContextSupport.USERNAME_MASKED)).isEqualTo("a***n");
        assertThat(MDC.get(TraceContextSupport.ROLE_CODE)).isEqualTo("ADMIN");
    }

    @Test
    void shouldCreateSystemJobContext() {
        CompactSnowflakeIdGenerator idGenerator = mock(CompactSnowflakeIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(260602190000001L);
        TraceContextSupport support = new TraceContextSupport(idGenerator);

        String jobRunId = support.bindJobContext("cachePreheat");

        assertThat(jobRunId).isEqualTo("JOB-cachePreheat-260602190000001");
        assertThat(MDC.get(TraceContextSupport.TRACE_ID)).isNotBlank();
        assertThat(MDC.get(TraceContextSupport.OPERATION_ID)).isEqualTo(jobRunId);
        assertThat(MDC.get(TraceContextSupport.JOB_RUN_ID)).isEqualTo(jobRunId);
        assertThat(MDC.get(TraceContextSupport.USER_ID)).isEqualTo("system");
        assertThat(MDC.get(TraceContextSupport.ROLE_CODE)).isEqualTo("SYSTEM");
    }
}
