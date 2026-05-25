package jimmy.config;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.Charset;

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
