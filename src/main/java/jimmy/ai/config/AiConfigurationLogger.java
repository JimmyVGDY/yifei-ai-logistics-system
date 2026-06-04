package jimmy.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * AI 配置启动摘要 —— 只打印非敏感配置，方便判断 Nacos 配置是否已被应用读取。
 */
@Slf4j
@Component
public class AiConfigurationLogger {

    private static final String API_KEY = "spring.ai.openai.api-key";
    private static final String BASE_URL = "spring.ai.openai.base-url";
    private static final String MODEL = "spring.ai.openai.chat.options.model";

    private final Environment environment;

    public AiConfigurationLogger(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logAiConfigurationSummary() {
        String apiKey = environment.getProperty(API_KEY, "");
        String baseUrl = environment.getProperty(BASE_URL, "");
        String model = environment.getProperty(MODEL, "");
        log.info("AI 配置摘要：configured={}, baseUrl={}, model={}, apiKeySource={}, baseUrlSource={}, modelSource={}",
                configured(apiKey),
                safeDisplay(baseUrl),
                safeDisplay(model),
                propertySourceName(API_KEY),
                propertySourceName(BASE_URL),
                propertySourceName(MODEL));
    }

    private boolean configured(String apiKey) {
        return StringUtils.hasText(apiKey) && !"missing".equalsIgnoreCase(apiKey);
    }

    private String safeDisplay(String value) {
        return StringUtils.hasText(value) ? value : "未配置";
    }

    private String propertySourceName(String key) {
        if (!(environment instanceof ConfigurableEnvironment configurableEnvironment)) {
            return "unknown";
        }
        for (PropertySource<?> propertySource : configurableEnvironment.getPropertySources()) {
            if (propertySource.containsProperty(key)) {
                return propertySource.getName();
            }
        }
        return "not-found";
    }
}
