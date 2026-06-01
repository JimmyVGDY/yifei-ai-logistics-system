package jimmy.logistics.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface LogisticsDashboardMapper {

    Long countTodayOrders(@org.apache.ibatis.annotations.Param("customerId") Long customerId);

    Long countCompletedOrders(@org.apache.ibatis.annotations.Param("customerId") Long customerId);

    Long countWaitDispatchOrders(@org.apache.ibatis.annotations.Param("customerId") Long customerId);

    Long countInTransitOrders(@org.apache.ibatis.annotations.Param("customerId") Long customerId);

    Long countOpenExceptionOrders(@org.apache.ibatis.annotations.Param("customerId") Long customerId);

    BigDecimal sumPaidMonthIncome(@org.apache.ibatis.annotations.Param("customerId") Long customerId);

    List<Map<String, Object>> selectStatusDistribution(@org.apache.ibatis.annotations.Param("customerId") Long customerId);

    List<Map<String, Object>> selectRecentOpenExceptions(@org.apache.ibatis.annotations.Param("customerId") Long customerId);

    /** 查询 30 天内到期的合同 */
    List<Map<String, Object>> selectExpiringContracts(@org.apache.ibatis.annotations.Param("days") int days);

    /** 统计上月订单数 */
    Long countLastMonthOrders();

    /** 统计上月异常订单数 */
    int countLastMonthExceptions();

    /** 统计上传文件总数 */
    Long countUploadedFiles();
}
