package jimmy.logistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LogisticsTrackMapper {

    int countByOrderIdAndOperationDesc(@Param("orderId") Long orderId,
                                       @Param("operationDesc") String operationDesc);

    int insertTrack(@Param("id") Long id,
                    @Param("orderId") Long orderId,
                    @Param("waybillId") Long waybillId,
                    @Param("currentStatus") String currentStatus,
                    @Param("currentLocation") String currentLocation,
                    @Param("operatorName") String operatorName,
                    @Param("operationDesc") String operationDesc);
}
