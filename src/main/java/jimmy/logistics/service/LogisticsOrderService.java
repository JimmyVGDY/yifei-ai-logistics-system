package jimmy.logistics.service;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.entity.LogisticsOrder;
import jimmy.logistics.model.OperationChangeContext;
import jimmy.logistics.mapper.LogisticsOrderMapper;
import jimmy.logistics.model.CreateLogisticsOrderRequest;
import jimmy.common.util.LogMaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 物流订单服务 —— 订单创建、查询、缓存/索引同步、MQ 事件发布的核心编排层。
 * <p>
 * 创建流程：参数校验 → 生成订单号 → 落库 → 布隆过滤器标记 → 缓存回填 → ES 索引 → RabbitMQ 事件
 * <p>
 * 查询流程：布隆过滤器预判 → Redis 缓存 → 数据库回源 → 缓存回填（Cache-Aside 模式）
 * <p>
 * 限流保护：{@code @SentinelResource} 对创建和查询分别配置 fallback 兜底。
 */
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

    /**
     * 创建物流订单（带 Sentinel 限流保护）。
     * <p>
     * 订单号由后端统一生成（格式 LO+14 位时间戳+8 位随机码），
     * 落库后同步更新布隆过滤器、Redis 缓存、ES 搜索索引，并发布 MQ 事件。
     * 变更摘要写入 OperationChangeContext 供审计拦截器采集。
     */
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
        logisticsOrder.setCustomerId(currentCustomerScopeOrNull());
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

    /**
     * 按订单号查询（带 Sentinel 限流保护）。
     * <p>
     * 布隆过滤器预判 → Redis 缓存 → 数据库回源 → 缓存回填。
     * 布隆过滤器未命中时仍会查询数据库以避免重启后旧数据的误判。
     */
    @SentinelResource(value = "logisticsOrderQuery", fallback = "findByOrderNoFallback")
    public LogisticsOrder findByOrderNo(String orderNo) {
        Long customerId = currentCustomerScopeOrNull();
        if (customerId != null) {
            LogisticsOrder scopedOrder = logisticsOrderMapper.findByOrderNoAndCustomerId(orderNo, customerId);
            if (scopedOrder == null) {
                log.warn("客户账号查询订单越权或订单不存在，orderNo={}, customerId={}", orderNo, customerId);
            }
            return scopedOrder;
        }
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

    /**
     * 查询最近 N 条订单（上限 100，防止前端传过大 limit 压库）
     */
    public List<LogisticsOrder> findRecent(int limit) {
        // 限制单次查询数量，避免前端误传过大 limit 压垮数据库。
        int safeLimit = Math.max(1, Math.min(limit, 100));
        Long customerId = currentCustomerScopeOrNull();
        List<LogisticsOrder> orders = customerId == null
                ? logisticsOrderMapper.findRecent(safeLimit)
                : logisticsOrderMapper.findRecentByCustomerId(customerId, safeLimit);
        log.info("查询近期物流订单，limit={}, safeLimit={}, resultSize={}", limit, safeLimit, orders.size());
        return orders;
    }

    /**
     * Sentinel 创建订单的 fallback —— 返回 BLOCKED 状态标记，调用方可据此提示稍后重试
     */
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

    /**
     * 生成订单号：前缀 LO + 14 位时间戳 + 8 位 Snowflake 唯一后缀。
     * <p>
     * 后缀取自 {@link CompactSnowflakeIdGenerator#nextId()} 的后 8 位十进制数字，
     * 同一秒内 Snowflake 序列号 (0~999) 保证唯一，单秒并发上限 1000。
     * 相比 UUID 随机 8 位十六进制字符 (32 bit 随机空间)，
     * Snowflake 的秒级时间戳 + 序列号在高并发下碰撞概率更低。
     * <p>
     * 格式示例：LO2026061114302500000123 (24 位)
     */
    private String generateOrderNo(LocalDateTime now) {
        String datePart = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        // 使用 Snowflake ID 后 8 位替代 UUID 随机数，唯一性由 Snowflake 序列号保证
        long snowflakeId = idGenerator.nextId();
        String uniquePart = String.format("%08d", snowflakeId % 100_000_000);
        return "LO" + datePart + uniquePart;
    }

    private Long currentCustomerScopeOrNull() {
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId == null) {
                return null;
            }
            Object roleCode = StpUtil.getSessionByLoginId(loginId).get("roleCode");
            if (!"CUSTOMER".equals(String.valueOf(roleCode))) {
                return null;
            }
            Object customerId = StpUtil.getSessionByLoginId(loginId).get("customerId");
            if (customerId instanceof Number number) {
                return number.longValue();
            }
            if (customerId != null && StringUtils.hasText(String.valueOf(customerId))) {
                return Long.valueOf(String.valueOf(customerId));
            }
            throw new IllegalStateException("客户账号未绑定客户档案，禁止查询业务数据");
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("客户数据权限校验失败", ex);
        }
    }
}
