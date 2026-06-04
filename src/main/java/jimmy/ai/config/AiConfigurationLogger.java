package jimmy.ai.config;

import jimmy.ai.service.AiRuntimeProperties;
import jimmy.ai.service.AiRuntimePropertiesProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * AI 配置启动摘要 —— 只打印非敏感配置，方便判断 Nacos 配置是否已被应用读取。
 */
@Slf4j
@Component
public class AiConfigurationLogger {

    private final AiRuntimePropertiesProvider runtimePropertiesProvider;

    public AiConfigurationLogger(AiRuntimePropertiesProvider runtimePropertiesProvider) {
        this.runtimePropertiesProvider = runtimePropertiesProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logAiConfigurationSummary() {
        AiRuntimeProperties properties = runtimePropertiesProvider.current();
        log.info("AI 配置摘要：configured={}, baseUrl={}, model={}, source={}",
                properties.configured(),
                safeDisplay(properties.baseUrl()),
                safeDisplay(properties.model()),
                properties.source());
    }

    private String safeDisplay(String value) {
        return StringUtils.hasText(value) ? value : "未配置";
    }
}
