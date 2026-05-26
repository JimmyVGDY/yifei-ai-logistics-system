package jimmy.logistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Map;

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
