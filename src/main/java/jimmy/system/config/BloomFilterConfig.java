package jimmy.system.config;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.Charset;

/**
 * 布隆过滤器配置 —— Guava 内存实现，用于订单号快速判存（防缓存穿透）。
 * <p>
 * 期望插入量和误判率通过 {@link BloomFilterProperties} 配置。
 */
@Configuration
@EnableConfigurationProperties(BloomFilterProperties.class)
public class BloomFilterConfig {

    @Bean
    public BloomFilter<CharSequence> stringBloomFilter(BloomFilterProperties properties) {
        // 布隆过滤器用于快速判断字符串是否可能存在，适合后续做缓存穿透防护。
        return BloomFilter.create(
                Funnels.stringFunnel(Charset.forName("UTF-8")),
                properties.getExpectedInsertions(),
                properties.getFalsePositiveProbability()
        );
    }
}
