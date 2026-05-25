package jimmy.logistics.service;

import jimmy.config.LogisticsProperties;
import jimmy.logistics.entity.LogisticsOrder;
import jimmy.service.BloomFilterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class LogisticsOrderCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final LogisticsProperties logisticsProperties;
    private final BloomFilterService bloomFilterService;

    public LogisticsOrderCacheService(RedisTemplate<String, Object> redisTemplate,
                                      LogisticsProperties logisticsProperties,
                                      BloomFilterService bloomFilterService) {
        this.redisTemplate = redisTemplate;
        this.logisticsProperties = logisticsProperties;
        this.bloomFilterService = bloomFilterService;
    }

    public void rememberOrderNo(String orderNo) {
        bloomFilterService.put(orderNo);
    }

    public boolean mightContainOrderNo(String orderNo) {
        return bloomFilterService.mightContain(orderNo);
    }

    public LogisticsOrder getOrder(String orderNo) {
        Object cached = redisTemplate.opsForValue().get(orderCacheKey(orderNo));
        return cached instanceof LogisticsOrder ? (LogisticsOrder) cached : null;
    }

    public void cacheOrder(LogisticsOrder logisticsOrder) {
        redisTemplate.opsForValue().set(
                orderCacheKey(logisticsOrder.getOrderNo()),
                logisticsOrder,
                logisticsProperties.getOrderCacheTtlSeconds(),
                TimeUnit.SECONDS
        );
        log.info("物流订单写入 Redis 缓存，orderNo={}, ttlSeconds={}",
                logisticsOrder.getOrderNo(), logisticsProperties.getOrderCacheTtlSeconds());
    }

    private String orderCacheKey(String orderNo) {
        return logisticsProperties.getCachePrefix() + ":order:" + orderNo;
    }
}
