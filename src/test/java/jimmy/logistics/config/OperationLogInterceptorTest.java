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
import static org.mockito.Mockito.verifyNoInteractions;
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
                eq("运单管理-新增记录"),
                eq("/logistics/modules/orders"),
                eq("POST"),
                eq("SUCCESS"),
                costCaptor.capture(),
                eq(null)
        );
        assertThat(costCaptor.getValue()).isGreaterThanOrEqualTo(0L);
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void shouldWriteClientContextAndFilterSensitiveParamsWhenColumnsExist() throws Exception {
        OperationLogMapper mapper = mock(OperationLogMapper.class);
        ColumnExistenceChecker columnChecker = columnCheckerWithClientContextColumns();
        CompactSnowflakeIdGenerator idGenerator = mock(CompactSnowflakeIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(260602190000001L, 260602190000002L);

        OperationLogInterceptor interceptor = new OperationLogInterceptor(mapper, columnChecker, idGenerator);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/logistics/modules/orders/100");
        request.addHeader("X-Forwarded-For", "192.168.1.10, 10.0.0.1");
        request.addHeader("User-Agent", "JUnit Browser");
        request.setParameter("keyword", "测试订单");
        request.setParameter("password", "123456");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, handlerMethodWithoutOperationLog());
        interceptor.afterCompletion(request, response, handlerMethodWithoutOperationLog(), null);

        verify(mapper).insertOperationLogWithClientContext(
                anyLong(),
                eq("260602190000001"),
                eq(response.getHeader("X-Trace-Id")),
                eq(""),
                eq(""),
                eq("anonymous"),
                eq(""),
                eq("运单管理-编辑记录"),
                eq("/logistics/modules/orders/100"),
                eq("POST"),
                eq("SUCCESS"),
                org.mockito.ArgumentMatchers.anyLong(),
                eq(null),
                eq("192.168.1.10"),
                eq("JUnit Browser"),
                eq("keyword=测试订单"),
                eq("100"),
                eq(null)
        );
    }

    @Test
    void shouldRecordPermissionConfigCandidateListAsBusinessOperation() throws Exception {
        OperationLogMapper mapper = mock(OperationLogMapper.class);
        ColumnExistenceChecker columnChecker = columnCheckerWithOperationLogColumns();
        CompactSnowflakeIdGenerator idGenerator = mock(CompactSnowflakeIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(260602170000003L, 260602170000004L);

        OperationLogInterceptor interceptor = new OperationLogInterceptor(mapper, columnChecker, idGenerator);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/logistics/modules/users");
        request.setParameter("usage", "permissionConfig");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, handlerMethodWithoutOperationLog());
        interceptor.afterCompletion(request, response, handlerMethodWithoutOperationLog(), null);

        verify(mapper).insertOperationLog(
                anyLong(),
                eq("260602170000003"),
                eq(response.getHeader("X-Trace-Id")),
                eq(""),
                eq(""),
                eq("anonymous"),
                eq(""),
                eq("权限配置-加载用户候选列表"),
                eq("/logistics/modules/users"),
                eq("GET"),
                eq("SUCCESS"),
                org.mockito.ArgumentMatchers.anyLong(),
                eq(null)
        );
    }

    @Test
    void shouldNotRecordRelationOptionPreloadAsUserOperation() throws Exception {
        OperationLogMapper mapper = mock(OperationLogMapper.class);
        ColumnExistenceChecker columnChecker = columnCheckerWithOperationLogColumns();
        CompactSnowflakeIdGenerator idGenerator = mock(CompactSnowflakeIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(260602180000001L);

        OperationLogInterceptor interceptor = new OperationLogInterceptor(mapper, columnChecker, idGenerator);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/logistics/modules/orders");
        request.setParameter("usage", "relationOptions");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, handlerMethodWithoutOperationLog());
        interceptor.afterCompletion(request, response, handlerMethodWithoutOperationLog(), null);

        verifyNoInteractions(mapper);
    }

    @Test
    void shouldRecordGenericModuleQueryAndMutationAsReadableBusinessOperation() throws Exception {
        OperationLogMapper mapper = mock(OperationLogMapper.class);
        ColumnExistenceChecker columnChecker = columnCheckerWithOperationLogColumns();
        CompactSnowflakeIdGenerator idGenerator = mock(CompactSnowflakeIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(260602170000005L, 260602170000006L, 260602170000007L, 260602170000008L);

        OperationLogInterceptor interceptor = new OperationLogInterceptor(mapper, columnChecker, idGenerator);
        MockHttpServletRequest queryRequest = new MockHttpServletRequest("GET", "/logistics/modules/customers");
        MockHttpServletResponse queryResponse = new MockHttpServletResponse();
        interceptor.preHandle(queryRequest, queryResponse, handlerMethodWithoutOperationLog());
        interceptor.afterCompletion(queryRequest, queryResponse, handlerMethodWithoutOperationLog(), null);

        MockHttpServletRequest deleteRequest = new MockHttpServletRequest("POST", "/logistics/modules/orders/100/delete");
        MockHttpServletResponse deleteResponse = new MockHttpServletResponse();
        interceptor.preHandle(deleteRequest, deleteResponse, handlerMethodWithoutOperationLog());
        interceptor.afterCompletion(deleteRequest, deleteResponse, handlerMethodWithoutOperationLog(), null);

        verify(mapper).insertOperationLog(
                anyLong(),
                eq("260602170000005"),
                eq(queryResponse.getHeader("X-Trace-Id")),
                eq(""),
                eq(""),
                eq("anonymous"),
                eq(""),
                eq("客户管理-查询列表"),
                eq("/logistics/modules/customers"),
                eq("GET"),
                eq("SUCCESS"),
                org.mockito.ArgumentMatchers.anyLong(),
                eq(null)
        );
        verify(mapper).insertOperationLog(
                anyLong(),
                eq("260602170000007"),
                eq(deleteResponse.getHeader("X-Trace-Id")),
                eq(""),
                eq(""),
                eq("anonymous"),
                eq(""),
                eq("运单管理-删除记录"),
                eq("/logistics/modules/orders/100/delete"),
                eq("POST"),
                eq("SUCCESS"),
                org.mockito.ArgumentMatchers.anyLong(),
                eq(null)
        );
    }

    @Test
    void shouldRecordNonLogisticsUserClicksWithReadableBusinessOperation() throws Exception {
        OperationLogMapper mapper = mock(OperationLogMapper.class);
        ColumnExistenceChecker columnChecker = columnCheckerWithOperationLogColumns();
        CompactSnowflakeIdGenerator idGenerator = mock(CompactSnowflakeIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(260602170000009L, 260602170000010L, 260602170000011L, 260602170000012L);

        OperationLogInterceptor interceptor = new OperationLogInterceptor(mapper, columnChecker, idGenerator);
        MockHttpServletRequest profileRequest = new MockHttpServletRequest("PUT", "/auth/profile");
        MockHttpServletResponse profileResponse = new MockHttpServletResponse();
        interceptor.preHandle(profileRequest, profileResponse, handlerMethodWithoutOperationLog());
        interceptor.afterCompletion(profileRequest, profileResponse, handlerMethodWithoutOperationLog(), null);

        MockHttpServletRequest infraRequest = new MockHttpServletRequest("GET", "/infra/status");
        MockHttpServletResponse infraResponse = new MockHttpServletResponse();
        interceptor.preHandle(infraRequest, infraResponse, handlerMethodWithoutOperationLog());
        interceptor.afterCompletion(infraRequest, infraResponse, handlerMethodWithoutOperationLog(), null);

        verify(mapper).insertOperationLog(
                anyLong(),
                eq("260602170000009"),
                eq(profileResponse.getHeader("X-Trace-Id")),
                eq(""),
                eq(""),
                eq("anonymous"),
                eq(""),
                eq("个人设置-修改资料"),
                eq("/auth/profile"),
                eq("PUT"),
                eq("SUCCESS"),
                org.mockito.ArgumentMatchers.anyLong(),
                eq(null)
        );
        verify(mapper).insertOperationLog(
                anyLong(),
                eq("260602170000011"),
                eq(infraResponse.getHeader("X-Trace-Id")),
                eq(""),
                eq(""),
                eq("anonymous"),
                eq(""),
                eq("资源中心-查看中间件状态"),
                eq("/infra/status"),
                eq("GET"),
                eq("SUCCESS"),
                org.mockito.ArgumentMatchers.anyLong(),
                eq(null)
        );
    }

    @Test
    void shouldRecordSystemPermissionClickByAnnotation() throws Exception {
        OperationLogMapper mapper = mock(OperationLogMapper.class);
        ColumnExistenceChecker columnChecker = columnCheckerWithOperationLogColumns();
        CompactSnowflakeIdGenerator idGenerator = mock(CompactSnowflakeIdGenerator.class);
        when(idGenerator.nextId()).thenReturn(260602170000013L, 260602170000014L);

        OperationLogInterceptor interceptor = new OperationLogInterceptor(mapper, columnChecker, idGenerator);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/permissions/tree");
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(request, response, handlerMethod());
        interceptor.afterCompletion(request, response, handlerMethod(), null);

        verify(mapper).insertOperationLog(
                anyLong(),
                eq("260602170000013"),
                eq(response.getHeader("X-Trace-Id")),
                eq(""),
                eq(""),
                eq("anonymous"),
                eq(""),
                eq("测试操作"),
                eq("/system/permissions/tree"),
                eq("GET"),
                eq("SUCCESS"),
                org.mockito.ArgumentMatchers.anyLong(),
                eq(null)
        );
    }

    private HandlerMethod handlerMethod() throws Exception {
        Method method = DummyController.class.getDeclaredMethod("createOrder");
        return new HandlerMethod(new DummyController(), method);
    }

    private HandlerMethod handlerMethodWithoutOperationLog() throws Exception {
        Method method = DummyController.class.getDeclaredMethod("queryUsers");
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
            statement.execute("create table if not exists sys_operation_log (id bigint, operation_id varchar(64), error_message varchar(255))");
        }
        return new ColumnExistenceChecker(dataSource);
    }

    private ColumnExistenceChecker columnCheckerWithClientContextColumns() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:operation_log_context_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("create table if not exists sys_operation_log (id bigint, operation_id varchar(64), error_message varchar(255), client_ip varchar(45), user_agent varchar(255), request_params varchar(1000), target_id varchar(64), change_summary varchar(1000))");
        }
        return new ColumnExistenceChecker(dataSource);
    }

    private static class DummyController {
        @OperationLog("测试操作")
        public void createOrder() {
        }

        public void queryUsers() {
        }
    }
}
