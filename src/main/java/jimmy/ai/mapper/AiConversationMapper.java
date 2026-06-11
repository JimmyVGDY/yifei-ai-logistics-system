package jimmy.ai.mapper;

import jimmy.ai.model.AiConversationVO;
import jimmy.ai.model.AiMessageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 会话持久化 Mapper。
 * <p>
 * 会话和消息落 MySQL 作为审计主数据，Redis 只缓存最近上下文，避免服务重启后历史丢失。
 */
@Mapper
public interface AiConversationMapper {

    int upsertConversation(@Param("id") Long id,
                           @Param("conversationId") String conversationId,
                           @Param("userId") String userId,
                           @Param("userCode") String userCode,
                           @Param("title") String title,
                           @Param("contextSnapshot") String contextSnapshot);

    int updateConversationSummary(@Param("conversationId") String conversationId,
                                  @Param("userId") String userId,
                                  @Param("userCode") String userCode,
                                  @Param("contextSnapshot") String contextSnapshot);

    long countConversations(@Param("userId") String userId,
                            @Param("userCode") String userCode,
                            @Param("status") String status,
                            @Param("keyword") String keyword);

    List<AiConversationVO> selectConversations(@Param("userId") String userId,
                                               @Param("userCode") String userCode,
                                               @Param("status") String status,
                                               @Param("keyword") String keyword,
                                               @Param("offset") long offset,
                                               @Param("pageSize") int pageSize);

    AiConversationVO selectConversation(@Param("userId") String userId,
                                        @Param("userCode") String userCode,
                                        @Param("conversationId") String conversationId);

    List<AiMessageVO> selectMessages(@Param("conversationId") String conversationId,
                                     @Param("userId") String userId,
                                     @Param("userCode") String userCode,
                                     @Param("limit") int limit);

    int insertMessage(@Param("id") Long id,
                      @Param("messageId") String messageId,
                      @Param("conversationId") String conversationId,
                      @Param("userId") String userId,
                      @Param("userCode") String userCode,
                      @Param("role") String role,
                      @Param("content") String content,
                      @Param("status") String status,
                      @Param("traceId") String traceId,
                      @Param("operationId") String operationId,
                      @Param("loginSessionId") String loginSessionId,
                      @Param("toolSummary") String toolSummary,
                      @Param("citationSummary") String citationSummary);

    int archiveConversation(@Param("conversationId") String conversationId,
                            @Param("userId") String userId,
                            @Param("userCode") String userCode);

    int restoreConversation(@Param("conversationId") String conversationId,
                            @Param("userId") String userId,
                            @Param("userCode") String userCode);

    int deleteConversation(@Param("conversationId") String conversationId,
                           @Param("userId") String userId,
                           @Param("userCode") String userCode);

    int clearConversations(@Param("userId") String userId,
                           @Param("userCode") String userCode,
                           @Param("status") String status);
}
