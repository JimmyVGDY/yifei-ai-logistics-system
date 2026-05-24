package jimmy.logistics.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import jimmy.config.LogisticsProperties;
import jimmy.logistics.entity.LogisticsOrder;
import jimmy.logistics.mapper.LogisticsOrderMapper;
import jimmy.logistics.model.CreateLogisticsOrderRequest;
import jimmy.logistics.model.LogisticsOrderEvent;
import jimmy.logistics.repository.LogisticsOrderSearchDocument;
import jimmy.logistics.repository.LogisticsOrderSearchRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class LogisticsOrderService {

    private static final String STATUS_CREATED = "CREATED";

    private final LogisticsOrderMapper logisticsOrderMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final LogisticsOrderSearchRepository searchRepository;
    private final LogisticsProperties logisticsProperties;

    public LogisticsOrderService(LogisticsOrderMapper logisticsOrderMapper,
                                 RedisTemplate<String, Object> redisTemplate,
                                 RabbitTemplate rabbitTemplate,
                                 LogisticsOrderSearchRepository searchRepository,
                                 LogisticsProperties logisticsProperties) {
        this.logisticsOrderMapper = logisticsOrderMapper;
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.searchRepository = searchRepository;
        this.logisticsProperties = logisticsProperties;
    }

    @SentinelResource(value = "logisticsOrderCreate", fallback = "createFallback")
    public LogisticsOrder create(CreateLogisticsOrderRequest request) {
        validate(request);

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

        logisticsOrderMapper.insert(logisticsOrder);
        cacheOrder(logisticsOrder);
        saveSearchDocument(logisticsOrder);
        publishOrderCreated(logisticsOrder);
        return logisticsOrder;
    }

    @SentinelResource(value = "logisticsOrderQuery", fallback = "findByOrderNoFallback")
    public LogisticsOrder findByOrderNo(String orderNo) {
        String cacheKey = orderCacheKey(orderNo);
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof LogisticsOrder) {
            return (LogisticsOrder) cached;
        }

        LogisticsOrder logisticsOrder = logisticsOrderMapper.findByOrderNo(orderNo);
        if (logisticsOrder != null) {
            cacheOrder(logisticsOrder);
        }
        return logisticsOrder;
    }

    public List<LogisticsOrder> findRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return logisticsOrderMapper.findRecent(safeLimit);
    }

    public LogisticsOrder createFallback(CreateLogisticsOrderRequest request, Throwable throwable) {
        LogisticsOrder logisticsOrder = new LogisticsOrder();
        logisticsOrder.setOrderNo("SENTINEL-FALLBACK");
        logisticsOrder.setStatus("BLOCKED");
        logisticsOrder.setCustomerName(request == null ? null : request.getCustomerName());
        return logisticsOrder;
    }

    public LogisticsOrder findByOrderNoFallback(String orderNo, Throwable throwable) {
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
        redisTemplate.opsForValue().set(
                orderCacheKey(logisticsOrder.getOrderNo()),
                logisticsOrder,
                logisticsProperties.getOrderCacheTtlSeconds(),
                TimeUnit.SECONDS
        );
    }

    private void saveSearchDocument(LogisticsOrder logisticsOrder) {
        LogisticsOrderSearchDocument document = new LogisticsOrderSearchDocument();
        document.setOrderNo(logisticsOrder.getOrderNo());
        document.setStatus(logisticsOrder.getStatus());
        document.setCustomerName(logisticsOrder.getCustomerName());
        document.setReceiverAddress(logisticsOrder.getReceiverAddress());
        document.setCargoName(logisticsOrder.getCargoName());
        document.setCargoWeight(logisticsOrder.getCargoWeight());
        searchRepository.save(document);
    }

    private void publishOrderCreated(LogisticsOrder logisticsOrder) {
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
    }

    private String orderCacheKey(String orderNo) {
        return logisticsProperties.getCachePrefix() + ":order:" + orderNo;
    }

    private String generateOrderNo(LocalDateTime now) {
        String datePart = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "LO" + datePart + randomPart;
    }
}
