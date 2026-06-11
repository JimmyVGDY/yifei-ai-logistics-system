package jimmy.ai.model;

import jakarta.validation.constraints.NotBlank;

/**
 * AI 反馈请求 —— 前端提交的点赞/点踩数据。
 *
 * @param messageId      AI 消息 ID（必填）
 * @param conversationId AI 会话 ID（必填）
 * @param rating         评价：UP 或 DOWN（必填）
 * @param comment        可选备注（最多 500 字）
 */
public record FeedbackRequest(
        @NotBlank String messageId,
        @NotBlank String conversationId,
        @NotBlank String rating,
        String comment
) {
}
