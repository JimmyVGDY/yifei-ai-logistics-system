package jimmy.logistics.service;

import jimmy.logistics.config.LogisticsProperties;
import jimmy.logistics.entity.LogisticsOrder;
import jimmy.system.service.BloomFilterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 物流订单缓存服务 —— 布隆过滤器预判 + Redis 缓存（Cache-Aside 模式）。
 * <p>
 * 布隆过滤器加速不存在判断，Redis 缓存订单详情（TTL 可配置），减少数据库查询。
 */
@Slf4j
@Service
public class LogisticsOrderCacheService {

    private static final String IDEMPOTENCY_PENDING = "__PENDING__";
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

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
        return cached instanceof LogisticsOrder logisticsOrder ? logisticsOrder : null;
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

    public String idempotencyOrderNo(String key) {
        Object value = redisTemplate.opsForValue().get(idempotencyKey(key));
        if (value == null || IDEMPOTENCY_PENDING.equals(String.valueOf(value))) {
            return null;
        }
        return String.valueOf(value);
    }

    public boolean reserveIdempotencyKey(String key) {
        Boolean created = redisTemplate.opsForValue().setIfAbsent(
                idempotencyKey(key), IDEMPOTENCY_PENDING, IDEMPOTENCY_TTL_HOURS, TimeUnit.HOURS);
        return Boolean.TRUE.equals(created);
    }

    public boolean isIdempotencyPending(String key) {
        Object value = redisTemplate.opsForValue().get(idempotencyKey(key));
        return IDEMPOTENCY_PENDING.equals(String.valueOf(value));
    }

    public void completeIdempotencyKey(String key, String orderNo) {
        redisTemplate.opsForValue().set(idempotencyKey(key), orderNo, IDEMPOTENCY_TTL_HOURS, TimeUnit.HOURS);
    }

    public void clearIdempotencyKey(String key) {
        redisTemplate.delete(idempotencyKey(key));
    }

    /**
     * 生成订单缓存的 Redis Key，格式为 {@code 前缀:order:订单号}，支持按前缀批量清理。
     */
    private String orderCacheKey(String orderNo) {
        return logisticsProperties.getCachePrefix() + ":order:" + orderNo;
    }

    private String idempotencyKey(String key) {
        return logisticsProperties.getCachePrefix() + ":order:idempotency:" + key;
    }
}
