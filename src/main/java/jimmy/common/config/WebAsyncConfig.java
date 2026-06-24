package jimmy.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 异步请求配置。
 * <p>
 * 主要用于 SSE / StreamingResponseBody 长连接接口：
 * 1. 避免 Spring MVC 默认异步超时过短；
 * 2. 保证 AI 流式问答在模型慢、工具调用慢时不会被提前中断；
 * 3. 超时时间统一读取 application.yml 中的 app.ai.sse.timeout-ms。
 */
@Configuration
public class WebAsyncConfig implements WebMvcConfigurer {

    @Value("${app.ai.sse.timeout-ms:180000}")
    private long sseTimeoutMs;

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(sseTimeoutMs);
    }
}
