package jimmy.logistics.mapper;

import jimmy.logistics.entity.LogisticsOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LogisticsOrderMapper {

    List<LogisticsOrder> findRecent(@Param("limit") int limit);

    LogisticsOrder findByOrderNo(@Param("orderNo") String orderNo);

    int insert(LogisticsOrder logisticsOrder);

    int updateStatus(@Param("orderNo") String orderNo, @Param("status") String status);
}
