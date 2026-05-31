package jimmy.logistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 物流轨迹数据访问 —— 轨迹记录、去重查询和初始化写入。
 */
@Mapper
public interface LogisticsTrackMapper {

    /** 按订单 ID 和操作描述去重，防止重复初始化轨迹 */
    int countByOrderIdAndOperationDesc(@Param("orderId") Long orderId,
                                       @Param("operationDesc") String operationDesc);

    /** 插入新的轨迹记录 */
    int insertTrack(@Param("id") Long id,
                    @Param("orderId") Long orderId,
                    @Param("waybillId") Long waybillId,
                    @Param("currentStatus") String currentStatus,
                    @Param("currentLocation") String currentLocation,
                    @Param("operatorName") String operatorName,
                    @Param("operationDesc") String operationDesc);
}
