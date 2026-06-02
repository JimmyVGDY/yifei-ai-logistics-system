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

/**
 * TraceId 过滤器 —— 为每个 HTTP 请求注入链路追踪 ID。
 * <p>
 * 优先从请求头 X-Trace-Id 读取（兼容上游传入），否则生成 UUID。
 * 同时将登录用户信息写入 MDC，供日志框架自动包含在所有日志输出中。
 */
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
        response.setHeader("X-Trace-Id", traceId);
        MDC.put("traceId", traceId);
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId != null) {
            MDC.put("userId", String.valueOf(loginId));
            MDC.put("userCode", String.valueOf(StpUtil.getSession().get("userCode", "")));
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
