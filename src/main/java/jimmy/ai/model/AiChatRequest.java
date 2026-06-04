package jimmy.ai.model;

import jakarta.validation.constraints.NotBlank;

/**
 * AI 助手问答请求 —— 第一版只允许只读问答和排障分析。
 */
public record AiChatRequest(
        @NotBlank(message = "问题内容不能为空")
        String message,
        String conversationId,
        String pageContext) {
}
