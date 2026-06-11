package jimmy.ai.mapper;

import jimmy.ai.entity.AiMessageFeedback;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 消息反馈 Mapper —— 记录用户对 AI 回答的点赞/点踩。
 */
@Mapper
public interface AiMessageFeedbackMapper {

    /** 插入一条反馈记录 */
    int insert(AiMessageFeedback feedback);
}
