package jimmy.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * AI 运行时配置读取器 —— 优先读取 Nacos 中的 spring-ai.yml，失败时回退本地配置。
 */
@Slf4j
@Component
public class AiRuntimePropertiesProvider {

    private static final String DATA_ID = "spring-ai.yml";
    private static final String API_KEY = "spring.ai.openai.api-key";
    private static final String BASE_URL = "spring.ai.openai.base-url";
    private static final String MODEL = "spring.ai.openai.chat.options.model";
    private static final long CACHE_MILLIS = 60_000L;

    private final Environment environment;
    private final RestClient restClient;
    private final String nacosServerAddr;
    private final String nacosGroup;
    private final String nacosNamespace;

    private volatile AiRuntimeProperties cached;
    private volatile long cacheExpireAt;

    public AiRuntimePropertiesProvider(Environment environment,
                                       RestClient.Builder restClientBuilder,
                                       @Value("${spring.cloud.nacos.server-addr:127.0.0.1:8848}") String nacosServerAddr,
                                       @Value("${spring.cloud.nacos.config.group:DEFAULT_GROUP}") String nacosGroup,
                                       @Value("${spring.cloud.nacos.config.namespace:}") String nacosNamespace) {
        this.environment = environment;
        this.restClient = restClientBuilder.build();
        this.nacosServerAddr = nacosServerAddr;
        this.nacosGroup = nacosGroup;
        this.nacosNamespace = nacosNamespace;
    }

    /**
     * 获取当前 AI 运行时配置。
     * <p>
     * 使用双重检查锁实现线程安全的懒加载缓存：
     * <ol>
     *   <li>优先从 Nacos 读取 spring-ai.yml</li>
     *   <li>Nacos 不可用时回退本地 application.yml</li>
     *   <li>结果缓存 60 秒，到期后重新拉取</li>
     * </ol>
     */
    public AiRuntimeProperties current() {
        long now = System.currentTimeMillis();
        // 快速路径：缓存未过期，直接返回（无需加锁，volatile 保证可见性）
        AiRuntimeProperties snapshot = cached;
        if (snapshot != null && now < cacheExpireAt) {
            return snapshot;
        }
        // 慢速路径：双重检查锁，避免并发时重复拉取 Nacos
        synchronized (this) {
            snapshot = cached;
            if (snapshot != null && now < cacheExpireAt) {
                return snapshot;
            }
            AiRuntimeProperties resolved = loadFromNacos().orElseGet(this::loadFromEnvironment);
            cached = resolved;
            cacheExpireAt = System.currentTimeMillis() + CACHE_MILLIS;
            return resolved;
        }
    }

    private Optional<AiRuntimeProperties> loadFromNacos() {
        try {
            String content = restClient.get()
                    .uri(nacosConfigUri())
                    .retrieve()
                    .body(String.class);
            if (!StringUtils.hasText(content)) {
                return Optional.empty();
            }
            List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                    .load(DATA_ID, new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
            for (PropertySource<?> source : sources) {
                AiRuntimeProperties properties = fromPropertySource(source, "nacos:" + DATA_ID);
                if (properties.configured()) {
                    return Optional.of(properties);
                }
            }
        } catch (IOException exception) {
            log.warn("解析 Nacos AI 配置失败，已回退本地配置，reason={}", exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn("读取 Nacos AI 配置失败，已回退本地配置，reason={}", exception.getMessage());
        }
        return Optional.empty();
    }

    private URI nacosConfigUri() {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(normalizedNacosServerAddr())
                .path("/nacos/v1/cs/configs")
                .queryParam("dataId", DATA_ID)
                .queryParam("group", nacosGroup);
        if (StringUtils.hasText(nacosNamespace)) {
            builder.queryParam("tenant", nacosNamespace);
        }
        return builder.build(true).toUri();
    }

    private String normalizedNacosServerAddr() {
        if (nacosServerAddr.startsWith("http://") || nacosServerAddr.startsWith("https://")) {
            return nacosServerAddr;
        }
        return "http://" + nacosServerAddr;
    }

    private AiRuntimeProperties fromPropertySource(PropertySource<?> source, String sourceName) {
        return new AiRuntimeProperties(
                value(source.getProperty(API_KEY)),
                value(source.getProperty(BASE_URL)),
                value(source.getProperty(MODEL)),
                sourceName
        );
    }

    private AiRuntimeProperties loadFromEnvironment() {
        return new AiRuntimeProperties(
                environment.getProperty(API_KEY, ""),
                environment.getProperty(BASE_URL, ""),
                environment.getProperty(MODEL, ""),
                "environment"
        );
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
