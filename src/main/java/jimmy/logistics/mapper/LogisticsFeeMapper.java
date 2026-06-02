package jimmy.logistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 费用结算 Mapper —— 费用生成、更新、标记已收款。
 * <p>
 * 金额字段使用 BigDecimal 确保精度。
 */
@Mapper
public interface LogisticsFeeMapper {

    Map<String, Object> findOrderFeeBaseByOrderNo(@Param("orderNo") String orderNo);

    int countByOrderId(@Param("orderId") Long orderId);

    int updateByOrderId(@Param("orderId") Long orderId,
                        @Param("baseFee") BigDecimal baseFee,
                        @Param("weightFee") BigDecimal weightFee,
                        @Param("distanceFee") BigDecimal distanceFee,
                        @Param("additionalFee") BigDecimal additionalFee,
                        @Param("discountFee") BigDecimal discountFee,
                        @Param("payableFee") BigDecimal payableFee);

    int insertFee(@Param("id") Long id,
                  @Param("orderId") Long orderId,
                  @Param("baseFee") BigDecimal baseFee,
                  @Param("weightFee") BigDecimal weightFee,
                  @Param("distanceFee") BigDecimal distanceFee,
                  @Param("additionalFee") BigDecimal additionalFee,
                  @Param("discountFee") BigDecimal discountFee,
                  @Param("payableFee") BigDecimal payableFee);

    int markPaid(@Param("feeId") Long feeId);
}
