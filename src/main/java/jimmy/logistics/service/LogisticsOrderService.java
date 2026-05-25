package jimmy.logistics.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import jimmy.config.LogisticsProperties;
import jimmy.logistics.entity.LogisticsOrder;
import jimmy.logistics.mapper.LogisticsOrderMapper;
import jimmy.logistics.model.CreateLogisticsOrderRequest;
import jimmy.logistics.model.LogisticsOrderEvent;
import jimmy.logistics.repository.LogisticsOrderSearchDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class LogisticsOrderService {

    private static final String STATUS_CREATED = "WAIT_DISPATCH";

    private final LogisticsOrderMapper logisticsOrderMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ElasticsearchOperations elasticsearchOperations;
    private final LogisticsProperties logisticsProperties;

    public LogisticsOrderService(LogisticsOrderMapper logisticsOrderMapper,
                                 RedisTemplate<String, Object> redisTemplate,
                                 RabbitTemplate rabbitTemplate,
                                 ElasticsearchOperations elasticsearchOperations,
                                 LogisticsProperties logisticsProperties) {
        this.logisticsOrderMapper = logisticsOrderMapper;
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.elasticsearchOperations = elasticsearchOperations;
        this.logisticsProperties = logisticsProperties;
    }

    @SentinelResource(value = "logisticsOrderCreate", fallback = "createFallback")
    public LogisticsOrder create(CreateLogisticsOrderRequest request) {
        log.info("开始创建物流订单，customerName={}, cargoName={}", request == null ? null : request.getCustomerName(), request == null ? null : request.getCargoName());
        validate(request);

        // 运单号在服务端生成，避免前端传入重复单号导致业务数据冲突。
        LocalDateTime now = LocalDateTime.now();
        LogisticsOrder logisticsOrder = new LogisticsOrder();
        logisticsOrder.setOrderNo(generateOrderNo(now));
        logisticsOrder.setCustomerName(request.getCustomerName());
        logisticsOrder.setSenderAddress(request.getSenderAddress());
        logisticsOrder.setReceiverAddress(request.getReceiverAddress());
        logisticsOrder.setCargoName(request.getCargoName());
        logisticsOrder.setCargoWeight(request.getCargoWeight());
        logisticsOrder.setStatus(STATUS_CREATED);
        logisticsOrder.setCreatedAt(now);
        logisticsOrder.setUpdatedAt(now);

        // 下单主流程：先落库保证核心数据可靠，再同步缓存、搜索索引和异步事件。
        logisticsOrderMapper.insert(logisticsOrder);
        cacheOrder(logisticsOrder);
        saveSearchDocument(logisticsOrder);
        publishOrderCreated(logisticsOrder);
        log.info("物流订单创建完成，orderNo={}, customerName={}, status={}", logisticsOrder.getOrderNo(), logisticsOrder.getCustomerName(), logisticsOrder.getStatus());
        return logisticsOrder;
    }

    @SentinelResource(value = "logisticsOrderQuery", fallback = "findByOrderNoFallback")
    public LogisticsOrder findByOrderNo(String orderNo) {
        log.info("查询物流订单详情，orderNo={}", orderNo);
        // 查询优先走 Redis，未命中再查数据库，并把数据库结果回填缓存。
        String cacheKey = orderCacheKey(orderNo);
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof LogisticsOrder) {
            log.info("物流订单命中缓存，orderNo={}", orderNo);
            return (LogisticsOrder) cached;
        }

        LogisticsOrder logisticsOrder = logisticsOrderMapper.findByOrderNo(orderNo);
        if (logisticsOrder != null) {
            cacheOrder(logisticsOrder);
            log.info("物流订单查询数据库成功并回填缓存，orderNo={}", orderNo);
        } else {
            log.warn("物流订单不存在，orderNo={}", orderNo);
        }
        return logisticsOrder;
    }

    public List<LogisticsOrder> findRecent(int limit) {
        // 限制单次查询数量，避免前端误传过大 limit 压垮数据库。
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<LogisticsOrder> orders = logisticsOrderMapper.findRecent(safeLimit);
        log.info("查询近期物流订单，limit={}, safeLimit={}, resultSize={}", limit, safeLimit, orders.size());
        return orders;
    }

    public LogisticsOrder createFallback(CreateLogisticsOrderRequest request, Throwable throwable) {
        log.warn("创建物流订单触发 Sentinel 兜底，customerName={}, reason={}", request == null ? null : request.getCustomerName(), throwable == null ? null : throwable.getMessage());
        // Sentinel 触发限流或熔断时返回可识别状态，调用方可以据此提示稍后重试。
        LogisticsOrder logisticsOrder = new LogisticsOrder();
        logisticsOrder.setOrderNo("SENTINEL-FALLBACK");
        logisticsOrder.setStatus("BLOCKED");
        logisticsOrder.setCustomerName(request == null ? null : request.getCustomerName());
        return logisticsOrder;
    }

    public LogisticsOrder findByOrderNoFallback(String orderNo, Throwable throwable) {
        log.warn("查询物流订单触发 Sentinel 兜底，orderNo={}, reason={}", orderNo, throwable == null ? null : throwable.getMessage());
        LogisticsOrder logisticsOrder = new LogisticsOrder();
        logisticsOrder.setOrderNo(orderNo);
        logisticsOrder.setStatus("QUERY_FALLBACK");
        return logisticsOrder;
    }

    private void validate(CreateLogisticsOrderRequest request) {
        if (request == null || !StringUtils.hasText(request.getCustomerName())) {
            throw new IllegalArgumentException("customerName is required");
        }
        if (!StringUtils.hasText(request.getSenderAddress())) {
            throw new IllegalArgumentException("senderAddress is required");
        }
        if (!StringUtils.hasText(request.getReceiverAddress())) {
            throw new IllegalArgumentException("receiverAddress is required");
        }
        if (!StringUtils.hasText(request.getCargoName())) {
            throw new IllegalArgumentException("cargoName is required");
        }
        if (request.getCargoWeight() == null) {
            throw new IllegalArgumentException("cargoWeight is required");
        }
    }

    private void cacheOrder(LogisticsOrder logisticsOrder) {
        // 订单缓存设置过期时间，既提升查询速度，也避免长期保存脏数据。
        redisTemplate.opsForValue().set(
                orderCacheKey(logisticsOrder.getOrderNo()),
                logisticsOrder,
                logisticsProperties.getOrderCacheTtlSeconds(),
                TimeUnit.SECONDS
        );
        log.info("物流订单写入 Redis 缓存，orderNo={}, ttlSeconds={}", logisticsOrder.getOrderNo(), logisticsProperties.getOrderCacheTtlSeconds());
    }

    private void saveSearchDocument(LogisticsOrder logisticsOrder) {
        // 将订单关键字段写入 Elasticsearch，后续可扩展按客户、地址、货物名称检索。
        LogisticsOrderSearchDocument document = new LogisticsOrderSearchDocument();
        document.setOrderNo(logisticsOrder.getOrderNo());
        document.setStatus(logisticsOrder.getStatus());
        document.setCustomerName(logisticsOrder.getCustomerName());
        document.setReceiverAddress(logisticsOrder.getReceiverAddress());
        document.setCargoName(logisticsOrder.getCargoName());
        document.setCargoWeight(logisticsOrder.getCargoWeight());
        try {
            elasticsearchOperations.save(document);
            log.info("物流订单写入 Elasticsearch 索引，orderNo={}", logisticsOrder.getOrderNo());
        } catch (RuntimeException ignored) {
            // Elasticsearch 用于检索增强，但离线时不能影响运单创建主流程。
            log.warn("物流订单写入 Elasticsearch 失败，已忽略，orderNo={}, reason={}", logisticsOrder.getOrderNo(), ignored.getMessage());
        }
    }

    private void publishOrderCreated(LogisticsOrder logisticsOrder) {
        // 创建订单事件用于解耦后续流程，例如通知、轨迹初始化、费用计算等。
        LogisticsOrderEvent event = new LogisticsOrderEvent(
                "ORDER_CREATED",
                logisticsOrder.getOrderNo(),
                logisticsOrder.getStatus(),
                LocalDateTime.now()
        );
        rabbitTemplate.convertAndSend(
                logisticsProperties.getMq().getOrderExchange(),
                logisticsProperties.getMq().getOrderCreatedRoutingKey(),
                event
        );
        log.info("物流订单创建事件已发送，orderNo={}, exchange={}, routingKey={}",
                logisticsOrder.getOrderNo(),
                logisticsProperties.getMq().getOrderExchange(),
                logisticsProperties.getMq().getOrderCreatedRoutingKey());
    }

    private String orderCacheKey(String orderNo) {
        return logisticsProperties.getCachePrefix() + ":order:" + orderNo;
    }

    private String generateOrderNo(LocalDateTime now) {
        // 单号由时间戳和随机片段组成，便于按创建时间排查，同时降低并发重复概率。
        String datePart = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "LO" + datePart + randomPart;
    }
}
