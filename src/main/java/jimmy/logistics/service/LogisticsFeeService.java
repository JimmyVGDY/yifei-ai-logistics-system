package jimmy.logistics.service;

import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.model.SimpleResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Service
public class LogisticsFeeService {

    private final JdbcTemplate jdbcTemplate;
    private final CompactSnowflakeIdGenerator idGenerator;

    public LogisticsFeeService(JdbcTemplate jdbcTemplate,
                               CompactSnowflakeIdGenerator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
    }

    public SimpleResultVO generateFee(String orderNo) {
        Map<String, Object> order = jdbcTemplate.queryForMap(
                "select id, coalesce(cargo_weight, 0) cargo_weight from logistics_order where order_no = ?",
                orderNo
        );
        Long orderId = ((Number) order.get("id")).longValue();
        BigDecimal cargoWeight = new BigDecimal(String.valueOf(order.get("cargo_weight")));
        BigDecimal baseFee = new BigDecimal("120.00");
        BigDecimal weightFee = cargoWeight.multiply(new BigDecimal("2.50")).setScale(2, BigDecimal.ROUND_HALF_UP);
        BigDecimal distanceFee = new BigDecimal("80.00");
        BigDecimal additionalFee = BigDecimal.ZERO.setScale(2);
        BigDecimal discountFee = BigDecimal.ZERO.setScale(2);
        BigDecimal payableFee = baseFee.add(weightFee).add(distanceFee).add(additionalFee).subtract(discountFee);

        Integer exists = jdbcTemplate.queryForObject("select count(1) from logistics_fee where order_id = ?", Integer.class, orderId);
        if (exists != null && exists > 0) {
            jdbcTemplate.update(
                    "update logistics_fee set base_fee = ?, weight_fee = ?, distance_fee = ?, additional_fee = ?, discount_fee = ?, payable_fee = ?, update_time = current_timestamp where order_id = ?",
                    baseFee, weightFee, distanceFee, additionalFee, discountFee, payableFee, orderId
            );
        } else {
            jdbcTemplate.update(
                    "insert into logistics_fee (id, order_id, base_fee, weight_fee, distance_fee, additional_fee, discount_fee, payable_fee, actual_fee, payment_status, create_time, update_time) " +
                            "values (?, ?, ?, ?, ?, ?, ?, ?, 0, 'UNPAID', current_timestamp, current_timestamp)",
                    idGenerator.nextId(), orderId, baseFee, weightFee, distanceFee, additionalFee, discountFee, payableFee
            );
        }
        log.info("订单费用已生成，orderNo={}, payableFee={}", orderNo, payableFee);
        return new SimpleResultVO().add("orderNo", orderNo).add("payableFee", payableFee).add("paymentStatus", "UNPAID");
    }

    public SimpleResultVO markFeePaid(long feeId) {
        int updated = jdbcTemplate.update(
                "update logistics_fee set actual_fee = payable_fee, payment_status = 'PAID', update_time = current_timestamp where id = ?",
                feeId
        );
        if (updated == 0) {
            throw new IllegalArgumentException("费用记录不存在");
        }
        log.info("费用记录已标记付款，feeId={}", feeId);
        return new SimpleResultVO().add("feeId", feeId).add("paymentStatus", "PAID");
    }
}
