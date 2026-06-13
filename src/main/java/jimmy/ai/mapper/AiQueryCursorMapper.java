package jimmy.ai.mapper;

import jimmy.ai.model.AiQueryCursor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * AI 查询游标 Mapper。
 */
@Mapper
public interface AiQueryCursorMapper {

    int insertCursor(AiQueryCursor cursor);

    AiQueryCursor selectLatestActive(@Param("conversationId") String conversationId,
                                     @Param("userId") String userId,
                                     @Param("userCode") String userCode);

    int markDeleted(@Param("cursorId") String cursorId,
                    @Param("userId") String userId,
                    @Param("userCode") String userCode);
}
