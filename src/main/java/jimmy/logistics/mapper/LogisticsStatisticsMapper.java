package jimmy.logistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface LogisticsStatisticsMapper {

    List<Map<String, Object>> selectOrderTrend(@Param("daysOffset") int daysOffset);

    List<Map<String, Object>> selectIncomeTrend(@Param("monthsOffset") int monthsOffset);
}
