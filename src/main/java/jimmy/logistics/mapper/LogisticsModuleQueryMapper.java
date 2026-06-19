package jimmy.logistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 模块列表查询 Mapper —— 支持动态模块/表名/列名拼接的通用分页查询。
 * <p>
 * 通过判断字段是否存在（如 deleted/user_code/client_ip 等）动态选择 SQL 版本，
 * 兼容增量迁移期间不同表结构。
 * <p>
 * 数据行级隔离通过 {@code scopeColumn} / {@code scopeValue} 参数注入，
 * 由 {@link jimmy.logistics.config.DataScopeResolver} 统一解析。
 */
@Mapper
public interface LogisticsModuleQueryMapper {

    Long countModule(@Param("module") String module,
                     @Param("deletedExists") boolean deletedExists,
                     @Param("userCodeExists") boolean userCodeExists,
                     @Param("operationLogExtendedExists") boolean operationLogExtendedExists,
                     @Param("operationLogErrorMessageExists") boolean operationLogErrorMessageExists,
                     @Param("operationLogClientContextExists") boolean operationLogClientContextExists,
                     @Param("operationLogChangeSummaryExists") boolean operationLogChangeSummaryExists,
                     @Param("operationLogLoginSessionExists") boolean operationLogLoginSessionExists,
                     @Param("operationLogAiAuditExists") boolean operationLogAiAuditExists,
                     @Param("keyword") String keyword,
                     @Param("keywordColumns") List<String> keywordColumns,
                     @Param("timeColumn") String timeColumn,
                     @Param("startTime") String startTime,
                     @Param("endTime") String endTime,
                     @Param("scopeColumn") String scopeColumn,
                     @Param("scopeValue") Object scopeValue,
                     @Param("customerId") Long customerId);

    List<Map<String, Object>> selectModulePage(@Param("module") String module,
                                               @Param("deletedExists") boolean deletedExists,
                                               @Param("userCodeExists") boolean userCodeExists,
                                               @Param("operationLogExtendedExists") boolean operationLogExtendedExists,
                                               @Param("operationLogErrorMessageExists") boolean operationLogErrorMessageExists,
                                               @Param("operationLogClientContextExists") boolean operationLogClientContextExists,
                                               @Param("operationLogChangeSummaryExists") boolean operationLogChangeSummaryExists,
                                               @Param("operationLogLoginSessionExists") boolean operationLogLoginSessionExists,
                                               @Param("operationLogAiAuditExists") boolean operationLogAiAuditExists,
                                               @Param("keyword") String keyword,
                                               @Param("keywordColumns") List<String> keywordColumns,
                                               @Param("timeColumn") String timeColumn,
                                               @Param("startTime") String startTime,
                                               @Param("endTime") String endTime,
                                               @Param("orderColumn") String orderColumn,
                                               @Param("pageSize") int pageSize,
                                               @Param("offset") int offset,
                                               @Param("scopeColumn") String scopeColumn,
                                               @Param("scopeValue") Object scopeValue,
                                               @Param("customerId") Long customerId);
}
