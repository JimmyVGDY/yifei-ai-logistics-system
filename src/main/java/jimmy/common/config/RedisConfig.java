package jimmy.common.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置：序列化策略与安全校验。
 * <p>
 * Key 使用 StringRedisSerializer 便于 CLI 调试；
 * Value 使用 GenericJackson2JsonRedisSerializer + 白名单多态类型校验，
 * 防止 Jackson 反序列化漏洞（RCE 攻击面）。
 * </p>
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory,
                                                       ObjectMapper objectMapper) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        // Key 使用字符串序列化，方便在 redis-cli 中直接阅读和排查。
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        GenericJackson2JsonRedisSerializer jsonRedisSerializer = jsonRedisSerializer(objectMapper);
        // Value 使用 JSON 序列化，便于缓存 Java 对象并兼顾跨语言可读性。
        redisTemplate.setValueSerializer(jsonRedisSerializer);
        redisTemplate.setHashValueSerializer(jsonRedisSerializer);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    /**
     * 构建带安全类型校验的 JSON Redis 序列化器。
     * <p>
     * 仅允许反序列化为项目自身模型类和 JDK 安全类型，阻止攻击者
     * 通过构造恶意 JSON 执行任意代码（CVE 反序列化攻击）。
     * </p>
     */
    private GenericJackson2JsonRedisSerializer jsonRedisSerializer(ObjectMapper objectMapper) {
        ObjectMapper redisObjectMapper = objectMapper.copy();
        redisObjectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        redisObjectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // RedisTemplate 以 Object 读写缓存，需要保留类型信息，但限制可反序列化的类型范围。
        redisObjectMapper.activateDefaultTyping(
                buildSafeTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        GenericJackson2JsonRedisSerializer.registerNullValueSerializer(redisObjectMapper, null);
        return new GenericJackson2JsonRedisSerializer(redisObjectMapper);
    }

    /**
     * 构建多态类型安全校验器。
     * <p>
     * 白名单策略：只允许项目内 {@code jimmy.*} 包下的类、JDK 集合类
     * 和 Jackson 内部的类型标记类被反序列化，其余类型一律拒绝。
     * 新增业务模型时无需修改此配置，只要放在 {@code jimmy} 包下即可。
     * </p>
     */
    private static PolymorphicTypeValidator buildSafeTypeValidator() {
        return BasicPolymorphicTypeValidator.builder()
                // 允许项目自身的所有模型类
                .allowIfBaseType("jimmy.")
                // 允许 JDK 安全集合类型（ArrayList、HashMap、LinkedHashMap 等）
                .allowIfBaseType("java.util.")
                // 允许 Jackson 内部的类型标识
                .allowIfBaseType("com.fasterxml.jackson.")
                .build();
    }
}
