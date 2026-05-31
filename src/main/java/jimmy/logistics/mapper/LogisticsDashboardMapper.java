package jimmy.logistics.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface LogisticsDashboardMapper {

    Long countTodayOrders();

    Long countCompletedOrders();

    Long countWaitDispatchOrders();

    Long countInTransitOrders();

    Long countOpenExceptionOrders();

    BigDecimal sumPaidMonthIncome();

    List<Map<String, Object>> selectStatusDistribution();

    List<Map<String, Object>> selectRecentOpenExceptions();

    /** 查询 30 天内到期的合同 */
    List<Map<String, Object>> selectExpiringContracts(@org.apache.ibatis.annotations.Param("days") int days);

    /** 统计上月订单数 */
    Long countLastMonthOrders();

    /** 统计上月异常订单数 */
    int countLastMonthExceptions();

    /** 统计上传文件总数 */
    Long countUploadedFiles();
}
