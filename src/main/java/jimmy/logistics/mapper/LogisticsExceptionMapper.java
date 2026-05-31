package jimmy.logistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 物流异常数据访问 —— 异常的上报、状态查询和状态流转。
 */
@Mapper
public interface LogisticsExceptionMapper {

    /** 按订单号反查内部 ID */
    Long findOrderIdByOrderNo(@Param("orderNo") String orderNo);

    /** 查某个订单下最新的运输任务 ID */
    Long findLatestTaskIdByOrderId(@Param("orderId") Long orderId);

    /** 查异常当前状态，用于状态流转校验 */
    String findExceptionStatus(@Param("exceptionId") Long exceptionId);

    /** 插入新异常记录 */
    int insertException(@Param("id") Long id,
                        @Param("orderId") Long orderId,
                        @Param("taskId") Long taskId,
                        @Param("exceptionType") String exceptionType,
                        @Param("exceptionDesc") String exceptionDesc,
                        @Param("reportUser") String reportUser);

    /** 更新关联订单状态为异常 */
    int updateOrderStatusException(@Param("orderId") Long orderId);

    /** 更新异常处理状态和处理人 */
    int updateExceptionStatus(@Param("exceptionId") Long exceptionId,
                              @Param("exceptionStatus") String exceptionStatus,
                              @Param("handleUser") String handleUser);
}
