package jimmy.logistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OperationLogMapper {

    int insertOperationLog(@Param("id") Long id,
                           @Param("username") String username,
                           @Param("operation") String operation,
                           @Param("requestUri") String requestUri,
                           @Param("requestMethod") String requestMethod,
                           @Param("operationStatus") String operationStatus);
}
