package jimmy.logistics.service;

import jimmy.logistics.config.OperationChangeContext;
import jimmy.util.FieldEncryptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeAuditServiceTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldKeepTraceIdentifiersAndMaskSensitiveValuesInChangeSummary() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        ChangeAuditService service = new ChangeAuditService(new FieldEncryptor(false, ""));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", 260602222327001L);
        values.put("order_no", "LO202606020001");
        values.put("user_code", "U202606020001");
        values.put("customer_name", "上海鲜生零售有限公司");
        values.put("mobile", "13812341234");
        values.put("delivery_address", "上海市浦东新区世纪大道100号");

        service.recordCreate(values);

        String summary = OperationChangeContext.changeSummary(request);
        assertThat(summary)
                .contains("id=260602222327001")
                .contains("order_no=LO202606020001")
                .contains("user_code=U202606020001")
                .doesNotContainPattern("id=26\\*+7001")
                .doesNotContainPattern("order_no=LO\\*+0001")
                .doesNotContainPattern("user_code=U2\\*+0001")
                .doesNotContain("13812341234")
                .doesNotContain("上海鲜生零售有限公司")
                .doesNotContain("上海市浦东新区世纪大道100号");
    }
}
