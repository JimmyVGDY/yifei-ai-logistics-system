package jimmy.logistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface LogisticsModuleQueryMapper {

    Long countModule(@Param("module") String module,
                     @Param("deletedExists") boolean deletedExists,
                     @Param("userCodeExists") boolean userCodeExists,
                     @Param("operationLogExtendedExists") boolean operationLogExtendedExists,
                     @Param("operationLogErrorMessageExists") boolean operationLogErrorMessageExists,
                     @Param("operationLogClientContextExists") boolean operationLogClientContextExists,
                     @Param("keyword") String keyword,
                     @Param("keywordColumns") List<String> keywordColumns,
                     @Param("timeColumn") String timeColumn,
                     @Param("startTime") String startTime,
                     @Param("endTime") String endTime,
                     @Param("customerId") Long customerId);

    List<Map<String, Object>> selectModulePage(@Param("module") String module,
                                               @Param("deletedExists") boolean deletedExists,
                                               @Param("userCodeExists") boolean userCodeExists,
                                               @Param("operationLogExtendedExists") boolean operationLogExtendedExists,
                                               @Param("operationLogErrorMessageExists") boolean operationLogErrorMessageExists,
                                               @Param("operationLogClientContextExists") boolean operationLogClientContextExists,
                                               @Param("keyword") String keyword,
                                               @Param("keywordColumns") List<String> keywordColumns,
                                               @Param("timeColumn") String timeColumn,
                                               @Param("startTime") String startTime,
                                               @Param("endTime") String endTime,
                                               @Param("orderColumn") String orderColumn,
                                               @Param("pageSize") int pageSize,
                                               @Param("offset") int offset,
                                               @Param("customerId") Long customerId);
}
