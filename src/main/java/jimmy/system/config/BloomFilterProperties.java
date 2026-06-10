package jimmy.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 布隆过滤器配置属性 —— 绑定 {@code app.bloom-filter} 前缀，期望插入量和误判率。
 */
@ConfigurationProperties(prefix = "app.bloom-filter")
public class BloomFilterProperties {

    private long expectedInsertions = 100000L;
    private double falsePositiveProbability = 0.01D;

    public long getExpectedInsertions() {
        return expectedInsertions;
    }

    public void setExpectedInsertions(long expectedInsertions) {
        this.expectedInsertions = expectedInsertions;
    }

    public double getFalsePositiveProbability() {
        return falsePositiveProbability;
    }

    public void setFalsePositiveProbability(double falsePositiveProbability) {
        this.falsePositiveProbability = falsePositiveProbability;
    }
}
