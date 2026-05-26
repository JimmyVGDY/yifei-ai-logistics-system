package jimmy.logistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LogisticsExceptionMapper {

    Long findOrderIdByOrderNo(@Param("orderNo") String orderNo);

    Long findLatestTaskIdByOrderId(@Param("orderId") Long orderId);

    String findExceptionStatus(@Param("exceptionId") Long exceptionId);

    int insertException(@Param("id") Long id,
                        @Param("orderId") Long orderId,
                        @Param("taskId") Long taskId,
                        @Param("exceptionType") String exceptionType,
                        @Param("exceptionDesc") String exceptionDesc,
                        @Param("reportUser") String reportUser);

    int updateOrderStatusException(@Param("orderId") Long orderId);

    int updateExceptionStatus(@Param("exceptionId") Long exceptionId,
                              @Param("exceptionStatus") String exceptionStatus,
                              @Param("handleUser") String handleUser);
}
