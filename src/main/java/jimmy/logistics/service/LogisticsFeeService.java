package jimmy.logistics.service;

import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.mapper.LogisticsFeeMapper;
import jimmy.logistics.model.SimpleResultVO;
import jimmy.logistics.config.OperationChangeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

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

    public SimpleResultVO generateFee(String orderNo) {
        Map<String, Object> order = logisticsFeeMapper.findOrderFeeBaseByOrderNo(orderNo);
        // 防御性校验：订单不存在或缺少必要字段时提前报错，避免后续 NPE。
        if (order == null || order.get("id") == null || order.get("cargoWeight") == null) {
            throw new IllegalArgumentException("订单不存在或缺少货物信息");
        }
        Long orderId = ((Number) order.get("id")).longValue();
        BigDecimal cargoWeight = new BigDecimal(String.valueOf(order.get("cargoWeight")));
        // 当前是练习版计费模型：基础运费 + 重量费 + 固定里程费，后续可替换为线路/距离/合同价规则。
        BigDecimal baseFee = new BigDecimal("120.00");
        BigDecimal weightFee = cargoWeight.multiply(new BigDecimal("2.50")).setScale(2, BigDecimal.ROUND_HALF_UP);
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
