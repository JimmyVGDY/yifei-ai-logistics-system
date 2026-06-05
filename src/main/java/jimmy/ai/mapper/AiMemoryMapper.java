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
