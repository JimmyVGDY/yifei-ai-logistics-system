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
}
