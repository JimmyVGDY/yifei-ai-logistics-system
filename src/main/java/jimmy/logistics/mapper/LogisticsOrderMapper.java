package jimmy.logistics.mapper;

import jimmy.logistics.entity.LogisticsOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 物流订单数据访问 —— 订单的创建、查询和状态更新。
 */
@Mapper
public interface LogisticsOrderMapper {

    /** 查询最近 N 条订单（按创建时间倒序） */
    List<LogisticsOrder> findRecent(@Param("limit") int limit);

    List<LogisticsOrder> findRecentByCustomerId(@Param("customerId") Long customerId,
                                                @Param("limit") int limit);

    /** 按订单号精确查询 */
    LogisticsOrder findByOrderNo(@Param("orderNo") String orderNo);

    LogisticsOrder findByOrderNoAndCustomerId(@Param("orderNo") String orderNo,
                                              @Param("customerId") Long customerId);

    /** 插入新订单 */
    int insert(LogisticsOrder logisticsOrder);

    /** 更新订单状态 */
    int updateStatus(@Param("orderNo") String orderNo, @Param("status") String status);
}
