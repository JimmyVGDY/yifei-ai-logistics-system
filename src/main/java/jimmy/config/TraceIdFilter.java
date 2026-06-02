package jimmy.config;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
/**
 * TraceId 过滤器 —— 为每个 HTTP 请求注入链路追踪 ID。
 * <p>
 * 优先从请求头 X-Trace-Id 读取（兼容上游传入），否则生成 UUID。
 * 同时将登录用户信息写入 MDC，供日志框架自动包含在所有日志输出中。
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private final TraceContextSupport traceContextSupport;

    public TraceIdFilter(TraceContextSupport traceContextSupport) {
        this.traceContextSupport = traceContextSupport;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = traceContextSupport.newTraceId();
        }
        response.setHeader("X-Trace-Id", traceId);
        MDC.put(TraceContextSupport.TRACE_ID, traceId);
        traceContextSupport.bindCurrentLoginSession();
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
