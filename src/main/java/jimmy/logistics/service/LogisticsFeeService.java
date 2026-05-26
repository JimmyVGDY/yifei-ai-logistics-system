package jimmy.logistics.service;

import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.mapper.LogisticsFeeMapper;
import jimmy.logistics.model.SimpleResultVO;
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
        Long orderId = ((Number) order.get("id")).longValue();
        BigDecimal cargoWeight = new BigDecimal(String.valueOf(order.get("cargoWeight")));
        BigDecimal baseFee = new BigDecimal("120.00");
        BigDecimal weightFee = cargoWeight.multiply(new BigDecimal("2.50")).setScale(2, BigDecimal.ROUND_HALF_UP);
        BigDecimal distanceFee = new BigDecimal("80.00");
        BigDecimal additionalFee = BigDecimal.ZERO.setScale(2);
        BigDecimal discountFee = BigDecimal.ZERO.setScale(2);
        BigDecimal payableFee = baseFee.add(weightFee).add(distanceFee).add(additionalFee).subtract(discountFee);

        if (logisticsFeeMapper.countByOrderId(orderId) > 0) {
            logisticsFeeMapper.updateByOrderId(orderId, baseFee, weightFee, distanceFee, additionalFee, discountFee, payableFee);
        } else {
            logisticsFeeMapper.insertFee(idGenerator.nextId(), orderId, baseFee, weightFee, distanceFee, additionalFee, discountFee, payableFee);
        }
        log.info("订单费用已生成，orderNo={}, payableFee={}", orderNo, payableFee);
        return new SimpleResultVO().add("orderNo", orderNo).add("payableFee", payableFee).add("paymentStatus", "UNPAID");
    }

    public SimpleResultVO markFeePaid(long feeId) {
        int updated = logisticsFeeMapper.markPaid(feeId);
        if (updated == 0) {
            throw new IllegalArgumentException("费用记录不存在");
        }
        log.info("费用记录已标记付款，feeId={}", feeId);
        return new SimpleResultVO().add("feeId", feeId).add("paymentStatus", "PAID");
    }
}
