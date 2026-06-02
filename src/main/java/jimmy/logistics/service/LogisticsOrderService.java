package jimmy.logistics.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.entity.LogisticsOrder;
import jimmy.logistics.config.OperationChangeContext;
import jimmy.logistics.mapper.LogisticsOrderMapper;
import jimmy.logistics.model.CreateLogisticsOrderRequest;
import jimmy.util.LogMaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class LogisticsOrderService {

    private static final String STATUS_CREATED = "WAIT_DISPATCH";

    private final LogisticsOrderMapper logisticsOrderMapper;
    private final CompactSnowflakeIdGenerator idGenerator;
    private final LogisticsOrderCacheService orderCacheService;
    private final LogisticsOrderSearchService orderSearchService;
    private final LogisticsOrderMessageService orderMessageService;

    public LogisticsOrderService(LogisticsOrderMapper logisticsOrderMapper,
                                 CompactSnowflakeIdGenerator idGenerator,
                                 LogisticsOrderCacheService orderCacheService,
                                 LogisticsOrderSearchService orderSearchService,
                                 LogisticsOrderMessageService orderMessageService) {
        this.logisticsOrderMapper = logisticsOrderMapper;
        this.idGenerator = idGenerator;
        this.orderCacheService = orderCacheService;
        this.orderSearchService = orderSearchService;
        this.orderMessageService = orderMessageService;
    }

    @SentinelResource(value = "logisticsOrderCreate", fallback = "createFallback")
    public LogisticsOrder create(CreateLogisticsOrderRequest request) {
        log.info("开始创建物流订单，customerName={}, cargoName={}",
                request == null ? null : LogMaskUtils.maskName(request.getCustomerName()),
                request == null ? null : LogMaskUtils.maskText(request.getCargoName()));
        validate(request);

        // 运单号在服务端生成，避免前端传入重复单号导致业务数据冲突。
        LocalDateTime now = LocalDateTime.now();
        LogisticsOrder logisticsOrder = new LogisticsOrder();
        logisticsOrder.setId(idGenerator.nextId());
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
        orderCacheService.rememberOrderNo(logisticsOrder.getOrderNo());
        orderCacheService.cacheOrder(logisticsOrder);
        orderSearchService.saveSearchDocument(logisticsOrder);
        orderMessageService.publishOrderCreated(logisticsOrder);
        log.info("物流订单创建完成，orderNo={}, customerName={}, status={}",
                logisticsOrder.getOrderNo(),
                LogMaskUtils.maskName(logisticsOrder.getCustomerName()),
                logisticsOrder.getStatus());
        OperationChangeContext.setChangeSummary("订单号=" + logisticsOrder.getOrderNo()
                + ", 客户=" + LogMaskUtils.maskName(logisticsOrder.getCustomerName())
                + ", 货物=" + logisticsOrder.getCargoName());
        return logisticsOrder;
    }

    @SentinelResource(value = "logisticsOrderQuery", fallback = "findByOrderNoFallback")
    public LogisticsOrder findByOrderNo(String orderNo) {
        log.info("查询物流订单详情，orderNo={}", orderNo);
        if (!orderCacheService.mightContainOrderNo(orderNo)) {
            log.info("物流订单未命中布隆过滤器，继续查询数据库避免重启后旧数据误判，orderNo={}", orderNo);
        }
        // 查询优先走 Redis，未命中再查数据库，并把数据库结果回填缓存。
        LogisticsOrder cached = orderCacheService.getOrder(orderNo);
        if (cached != null) {
            log.info("物流订单命中缓存，orderNo={}", orderNo);
            return cached;
        }

        LogisticsOrder logisticsOrder = logisticsOrderMapper.findByOrderNo(orderNo);
        if (logisticsOrder != null) {
            orderCacheService.rememberOrderNo(logisticsOrder.getOrderNo());
            orderCacheService.cacheOrder(logisticsOrder);
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
        log.warn("创建物流订单触发 Sentinel 兜底，customerName={}, reason={}",
                request == null ? null : LogMaskUtils.maskName(request.getCustomerName()),
                throwable == null ? null : throwable.getMessage());
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
    }

    private String generateOrderNo(LocalDateTime now) {
        // 单号由时间戳和随机片段组成，便于按创建时间排查，同时降低并发重复概率。
        String datePart = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "LO" + datePart + randomPart;
    }
}
