package jimmy.ai.mapper;

import jimmy.ai.model.AiMemoryItemVO;
import jimmy.ai.model.AiMemoryProfileVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 长期记忆 Mapper。
 */
@Mapper
public interface AiMemoryMapper {

    AiMemoryProfileVO selectProfile(@Param("userId") String userId, @Param("userCode") String userCode);

    int insertDefaultProfile(@Param("id") Long id,
                             @Param("userId") String userId,
                             @Param("userCode") String userCode,
                             @Param("answerStyle") String answerStyle);

    int updateProfileSettings(@Param("userId") String userId,
                              @Param("userCode") String userCode,
                              @Param("memoryEnabled") Boolean memoryEnabled,
                              @Param("answerStyle") String answerStyle);

    int updateProfileHabits(@Param("userId") String userId,
                            @Param("userCode") String userCode,
                            @Param("favoriteModules") String favoriteModules,
                            @Param("queryHabits") String queryHabits);

    long countMemories(@Param("userId") String userId,
                       @Param("userCode") String userCode,
                       @Param("keyword") String keyword,
                       @Param("memoryType") String memoryType);

    List<AiMemoryItemVO> selectMemories(@Param("userId") String userId,
                                        @Param("userCode") String userCode,
                                        @Param("keyword") String keyword,
                                        @Param("memoryType") String memoryType,
                                        @Param("offset") long offset,
                                        @Param("pageSize") int pageSize);

    List<AiMemoryItemVO> selectRecallCandidates(@Param("userId") String userId,
                                                @Param("userCode") String userCode,
                                                @Param("keyword") String keyword,
                                                @Param("limit") int limit);

    AiMemoryItemVO selectMemoryById(@Param("id") Long id,
                                    @Param("userId") String userId,
                                    @Param("userCode") String userCode);

    int countDuplicateMemory(@Param("userId") String userId,
                             @Param("userCode") String userCode,
                             @Param("memoryType") String memoryType,
                             @Param("memorySummary") String memorySummary);

    int insertMemory(@Param("id") Long id,
                     @Param("userId") String userId,
                     @Param("userCode") String userCode,
                     @Param("memoryType") String memoryType,
                     @Param("memoryTitle") String memoryTitle,
                     @Param("memorySummary") String memorySummary,
                     @Param("confidence") Double confidence,
                     @Param("qdrantPointId") String qdrantPointId,
                     @Param("sourceConversationId") String sourceConversationId,
                     @Param("sourceTraceId") String sourceTraceId);

    int markMemoryRecalled(@Param("id") Long id);

    /**
     * 记录一次偏好强化：reinforce_count +1，更新 last_reinforced_at。
     * 用于记忆召回命中时或偏好挖掘确认时。
     */
    int markMemoryReinforced(@Param("id") Long id);

    /**
     * 批量更新记忆状态，用于生命周期流转。
     */
    int updateMemoryStatus(@Param("ids") List<Long> ids, @Param("status") String status);

    /**
     * 对指定状态的记忆降低置信度，每次降低固定步长。
     * 用于衰减逻辑。
     */
    int decayConfidence(@Param("ids") List<Long> ids, @Param("step") double step);

    /**
     * 查询需要生命周期管理的记忆。
     * @param status         过滤状态
     * @param beforeTime     已超过该时间未强化/未召回
     * @param limit          单次处理上限
     */
    List<Long> selectMemoryIdsByStatus(@Param("status") String status,
                                       @Param("beforeTime") String beforeTime,
                                       @Param("limit") int limit);

    /**
     * 删除过期的已归档记忆（软删除）。
     */
    int deleteArchivedMemories(@Param("beforeTime") String beforeTime);

    /**
     * 统计指定时间段内用户最常调用的业务模块，用于自动推荐 FAVORITE_MODULE。
     */
    List<String> selectFrequentModules(@Param("userId") String userId,
                                       @Param("userCode") String userCode,
                                       @Param("sinceTime") String sinceTime,
                                       @Param("limit") int limit);

    int deleteMemory(@Param("id") Long id,
                     @Param("userId") String userId,
                     @Param("userCode") String userCode);

    int clearMemories(@Param("userId") String userId, @Param("userCode") String userCode);

    int insertMemoryEvent(@Param("id") Long id,
                          @Param("memoryId") Long memoryId,
                          @Param("eventType") String eventType,
                          @Param("eventSource") String eventSource,
                          @Param("userId") String userId,
                          @Param("userCode") String userCode,
                          @Param("traceId") String traceId,
                          @Param("operationId") String operationId,
                          @Param("loginSessionId") String loginSessionId,
                          @Param("aiConversationId") String aiConversationId,
                          @Param("eventSummary") String eventSummary);
}
