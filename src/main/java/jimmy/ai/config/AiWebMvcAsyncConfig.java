package jimmy.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * AI 流式响应的 MVC 异步超时配置。
 * <p>
 * Spring MVC 对 {@code StreamingResponseBody} 有整体异步超时限制；模型调用和工具查询偶尔会超过默认 30 秒，
 * 如果不拉长超时，响应会被容器提前关闭，前端就只能看到“SSE 连接意外关闭”。
 */
@Configuration
public class AiWebMvcAsyncConfig implements WebMvcConfigurer {

    private final long sseTimeoutMs;

    public AiWebMvcAsyncConfig(@Value("${app.ai.sse.timeout-ms:180000}") long sseTimeoutMs) {
        this.sseTimeoutMs = sseTimeoutMs;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(sseTimeoutMs);
    }
}
