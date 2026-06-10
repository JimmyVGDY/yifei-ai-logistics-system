package jimmy.logistics.service;

import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.mapper.LogisticsFeeMapper;
import jimmy.logistics.model.SimpleResultVO;
import jimmy.logistics.model.OperationChangeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 物流费用服务 —— 订单费用生成与收款确认。
 * <p>
 * 计费模型（练习版）：基础运费 ¥120 + 重量×¥2.50 + 固定里程费 ¥80
 * 重复生成时更新原账单避免多条重复记录，收款时将实收同步为应收。
 */
@Slf4j
@Service
public class LogisticsFeeService {

    private final LogisticsFeeMapper logisticsFeeMapper;
    private final CompactSnowflakeIdGenerator idGenerator;

    public LogisticsFeeService(LogisticsFeeMapper logisticsFeeMapper,
                               CompactSnowflakeIdGenerator idGenerator) {
        this.logisticsFeeMapper = logisticsFeeMapper;
        this.idGenerator = idGenerator;
    }

    /**
     * 生成订单费用。
     * <p>
     * 计费公式：¥120 + cargoWeight×¥2.50 + ¥80。
     * 重复生成时更新原账单，避免同一订单出现多条费用记录。
     * 金额计算使用 BigDecimal，精度设为 2 位小数（ROUND_HALF_UP）。
     */
    public SimpleResultVO generateFee(String orderNo) {
        Map<String, Object> order = logisticsFeeMapper.findOrderFeeBaseByOrderNo(orderNo);
        // 防御性校验：订单不存在或缺少必要字段时提前报错，避免后续 NPE。
        if (order == null || order.get("id") == null || order.get("cargoWeight") == null) {
            throw new IllegalArgumentException("订单不存在或缺少货物信息");
        }
        Long orderId = order.get("id") instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(order.get("id")));
        BigDecimal cargoWeight = new BigDecimal(String.valueOf(order.get("cargoWeight")));
        // 当前是练习版计费模型：基础运费 + 重量费 + 固定里程费，后续可替换为线路/距离/合同价规则。
        BigDecimal baseFee = new BigDecimal("120.00");
        BigDecimal weightFee = cargoWeight.multiply(new BigDecimal("2.50")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal distanceFee = new BigDecimal("80.00");
        BigDecimal additionalFee = BigDecimal.ZERO.setScale(2);
        BigDecimal discountFee = BigDecimal.ZERO.setScale(2);
        BigDecimal payableFee = baseFee.add(weightFee).add(distanceFee).add(additionalFee).subtract(discountFee);

        if (logisticsFeeMapper.countByOrderId(orderId) > 0) {
            // 同一订单重复生成费用时更新原账单，避免费用结算表出现多条重复账单。
            logisticsFeeMapper.updateByOrderId(orderId, baseFee, weightFee, distanceFee, additionalFee, discountFee, payableFee);
        } else {
            logisticsFeeMapper.insertFee(idGenerator.nextId(), orderId, baseFee, weightFee, distanceFee, additionalFee, discountFee, payableFee);
        }
        log.info("订单费用已生成，orderNo={}, payableFee={}", orderNo, payableFee);
        OperationChangeContext.setChangeSummary("订单号=" + orderNo + ", 应收金额=¥" + payableFee);
        return new SimpleResultVO().add("orderNo", orderNo).add("payableFee", payableFee).add("paymentStatus", "UNPAID");
    }

    /** 标记费用已付款，实收金额同步为应收 */
    public SimpleResultVO markFeePaid(long feeId) {
        // 收款操作将实收金额同步为应收金额；后续接入部分收款时可在这里扩展收款流水。
        int updated = logisticsFeeMapper.markPaid(feeId);
        if (updated == 0) {
            throw new IllegalArgumentException("费用记录不存在");
        }
        log.info("费用记录已标记付款，feeId={}", feeId);
        OperationChangeContext.setChangeSummary("付款状态: 未付款 -> 已付款");
        return new SimpleResultVO().add("feeId", feeId).add("paymentStatus", "PAID");
    }
}
