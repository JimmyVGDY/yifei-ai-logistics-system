package jimmy.logistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 操作日志 Mapper —— 兼容三种表结构版本（完整字段/含客户端上下文字段/旧版精简字段）的日志写入。
 * <p>
 * 运行时通过 ColumnExistenceChecker 判断字段是否存在，自动选择对应 SQL 版本。
 */
@Mapper
public interface OperationLogMapper {

    int insertOperationLog(@Param("id") Long id,
                           @Param("operationId") String operationId,
                           @Param("traceId") String traceId,
                           @Param("userId") String userId,
                           @Param("userCode") String userCode,
                           @Param("username") String username,
                           @Param("roleCode") String roleCode,
                           @Param("operation") String operation,
                           @Param("requestUri") String requestUri,
                           @Param("requestMethod") String requestMethod,
                           @Param("operationStatus") String operationStatus,
                           @Param("costMs") Long costMs,
                           @Param("errorMessage") String errorMessage);

    int insertOperationLogWithSession(@Param("id") Long id,
                                      @Param("operationId") String operationId,
                                      @Param("traceId") String traceId,
                                      @Param("loginSessionId") String loginSessionId,
                                      @Param("userId") String userId,
                                      @Param("userCode") String userCode,
                                      @Param("username") String username,
                                      @Param("roleCode") String roleCode,
                                      @Param("operation") String operation,
                                      @Param("requestUri") String requestUri,
                                      @Param("requestMethod") String requestMethod,
                                      @Param("operationStatus") String operationStatus,
                                      @Param("costMs") Long costMs,
                                      @Param("errorMessage") String errorMessage);

    int insertOperationLogWithClientContext(@Param("id") Long id,
                                            @Param("operationId") String operationId,
                                            @Param("traceId") String traceId,
                                            @Param("userId") String userId,
                                            @Param("userCode") String userCode,
                                            @Param("username") String username,
                                            @Param("roleCode") String roleCode,
                                            @Param("operation") String operation,
                                            @Param("requestUri") String requestUri,
                                            @Param("requestMethod") String requestMethod,
                                            @Param("operationStatus") String operationStatus,
                                            @Param("costMs") Long costMs,
                                            @Param("errorMessage") String errorMessage,
                                            @Param("clientIp") String clientIp,
                                            @Param("userAgent") String userAgent,
                                            @Param("requestParams") String requestParams,
                                            @Param("targetId") String targetId,
                                            @Param("changeSummary") String changeSummary);

    int insertOperationLogWithClientContextAndSession(@Param("id") Long id,
                                                      @Param("operationId") String operationId,
                                                      @Param("traceId") String traceId,
                                                      @Param("loginSessionId") String loginSessionId,
                                                      @Param("userId") String userId,
                                                      @Param("userCode") String userCode,
                                                      @Param("username") String username,
                                                      @Param("roleCode") String roleCode,
                                                      @Param("operation") String operation,
                                                      @Param("requestUri") String requestUri,
                                                      @Param("requestMethod") String requestMethod,
                                                      @Param("operationStatus") String operationStatus,
                                                      @Param("costMs") Long costMs,
                                                      @Param("errorMessage") String errorMessage,
                                                      @Param("clientIp") String clientIp,
                                                      @Param("userAgent") String userAgent,
                                                      @Param("requestParams") String requestParams,
                                                      @Param("targetId") String targetId,
                                                      @Param("changeSummary") String changeSummary);

    int insertOperationLogWithoutErrorMessage(@Param("id") Long id,
                                              @Param("operationId") String operationId,
                                              @Param("traceId") String traceId,
                                              @Param("userId") String userId,
                                              @Param("userCode") String userCode,
                                              @Param("username") String username,
                                              @Param("roleCode") String roleCode,
                                              @Param("operation") String operation,
                                              @Param("requestUri") String requestUri,
                                              @Param("requestMethod") String requestMethod,
                                              @Param("operationStatus") String operationStatus,
                                              @Param("costMs") Long costMs);

    int insertOperationLogWithoutErrorMessageWithSession(@Param("id") Long id,
                                                         @Param("operationId") String operationId,
                                                         @Param("traceId") String traceId,
                                                         @Param("loginSessionId") String loginSessionId,
                                                         @Param("userId") String userId,
                                                         @Param("userCode") String userCode,
                                                         @Param("username") String username,
                                                         @Param("roleCode") String roleCode,
                                                         @Param("operation") String operation,
                                                         @Param("requestUri") String requestUri,
                                                         @Param("requestMethod") String requestMethod,
                                                         @Param("operationStatus") String operationStatus,
                                                         @Param("costMs") Long costMs);

    int insertLegacyOperationLog(@Param("id") Long id,
                                 @Param("username") String username,
                                 @Param("operation") String operation,
                                 @Param("requestUri") String requestUri,
                                 @Param("requestMethod") String requestMethod,
                                 @Param("operationStatus") String operationStatus);
}
