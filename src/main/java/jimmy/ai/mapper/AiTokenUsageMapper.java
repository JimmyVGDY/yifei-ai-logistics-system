package jimmy.ai.mapper;

import jimmy.ai.entity.AiTokenUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI Token 用量 Mapper —— 记录和查询每次模型调用的 Token 消耗。
 */
@Mapper
public interface AiTokenUsageMapper {

    /**
     * 插入一条 Token 用量记录。
     */
    int insert(AiTokenUsage usage);

    /**
     * 按时间范围查询用量记录，支持按模型、用途过滤。
     */
    List<AiTokenUsage> selectByTimeRange(@Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime,
                                          @Param("modelName") String modelName,
                                          @Param("purpose") String purpose,
                                          @Param("offset") long offset,
                                          @Param("limit") int limit);

    /**
     * 统计时间范围内的 Token 消耗汇总。
     *
     * @return [{model_name, purpose, call_count, total_prompt_tokens, total_completion_tokens, total_estimated_cost}]
     */
    List<java.util.Map<String, Object>> sumByModelAndPurpose(@Param("startTime") LocalDateTime startTime,
                                                               @Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定天数内的总费用。
     */
    java.math.BigDecimal sumCostByDays(@Param("days") int days);
}
