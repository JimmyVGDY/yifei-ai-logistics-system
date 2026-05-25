package jimmy.config;

import cn.dev33.satoken.stp.StpUtil;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

@Component
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put("traceId", traceId);
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId != null) {
            MDC.put("userId", String.valueOf(loginId));
            MDC.put("usernameMasked", String.valueOf(StpUtil.getSession().get("usernameMasked", "")));
            MDC.put("roleCode", String.valueOf(StpUtil.getSession().get("roleCode", "")));
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
