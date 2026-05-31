package jimmy.service;

import com.google.common.hash.BloomFilter;
import org.springframework.stereotype.Service;

/**
 * 布隆过滤器服务 —— 用于快速判断订单号是否可能存在。
 * <p>
 * 基于 Guava 内存布隆过滤器实现，适合中小规模数据。
 * 重启后数据丢失，需结合数据库回源策略使用。
 * 生产环境建议替换为 Redis Bitmap 持久化实现。
 * </p>
 */
@Service
public class BloomFilterService {

    private final BloomFilter<CharSequence> stringBloomFilter;

    public BloomFilterService(BloomFilter<CharSequence> stringBloomFilter) {
        this.stringBloomFilter = stringBloomFilter;
    }

    /**
     * 将值加入布隆过滤器。
     *
     * @param value 要记录的值（如订单号）
     * @return true 表示成功加入
     */
    public boolean put(String value) {
        return stringBloomFilter.put(value);
    }

    /**
     * 判断值是否可能存在。
     *
     * @param value 要查询的值
     * @return true 表示可能存在（需回源确认），false 表示一定不存在
     */
    public boolean mightContain(String value) {
        return stringBloomFilter.mightContain(value);
    }
}
