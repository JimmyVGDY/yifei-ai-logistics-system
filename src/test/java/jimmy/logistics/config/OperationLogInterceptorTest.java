package jimmy.logistics.config;

import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.annotation.OperationLog;
import jimmy.logistics.mapper.OperationLogMapper;
import jimmy.logistics.util.ColumnExistenceChecker;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationLogInterceptorTest {

    @Test
    void shouldExposeTraceAndOperationIdAndWriteStructuredOperationLog() throws Exception {
        OperationLogMapper mapper = mock(OperationLogMapper.class);
        ColumnExistenceChecker columnChecker = columnCheckerWithOperationLogColumns();
        CompactSnowflakeIdGenerator idGenerator = mock(CompactSnowflakeIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(260602170000001L, 260602170000002L);

        OperationLogInterceptor interceptor = new OperationLogInterceptor(mapper, columnChecker, idGenerator);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/logistics/modules/orders");
        request.addHeader("X-Trace-Id", "trace-from-test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, handlerMethod())).isTrue();
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("trace-from-test");
        assertThat(response.getHeader("X-Operation-Id")).isEqualTo("260602170000001");
        assertThat(MDC.get("traceId")).isEqualTo("trace-from-test");
        assertThat(MDC.get("operationId")).isEqualTo("260602170000001");
        assertThat(MDC.get("requestUri")).isEqualTo("/logistics/modules/orders");
        assertThat(MDC.get("requestMethod")).isEqualTo("POST");

        interceptor.afterCompletion(request, response, handlerMethod(), null);

        ArgumentCaptor<Long> costCaptor = ArgumentCaptor.forClass(Long.class);
        verify(mapper).insertOperationLog(
                anyLong(),
                eq("260602170000001"),
                eq("trace-from-test"),
                eq(""),
                eq(""),
                eq("anonymous"),
                eq(""),
                eq("测试操作"),
                eq("/logistics/modules/orders"),
                eq("POST"),
                eq("SUCCESS"),
                costCaptor.capture(),
                eq(null)
        );
        assertThat(costCaptor.getValue()).isGreaterThanOrEqualTo(0L);
        assertThat(MDC.get("traceId")).isNull();
    }

    private HandlerMethod handlerMethod() throws Exception {
        Method method = DummyController.class.getDeclaredMethod("createOrder");
        return new HandlerMethod(new DummyController(), method);
    }

    private ColumnExistenceChecker columnCheckerWithOperationLogColumns() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:operation_log_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("create table sys_operation_log (id bigint, operation_id varchar(64), error_message varchar(255))");
        }
        return new ColumnExistenceChecker(dataSource);
    }

    private static class DummyController {
        @OperationLog("测试操作")
        public void createOrder() {
        }
    }
}
