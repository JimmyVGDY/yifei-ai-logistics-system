package jimmy.ai.model;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * AI 助手问答请求 —— 第一版只允许只读问答和排障分析。
 */
public record AiChatRequest(
        @NotBlank(message = "问题内容不能为空")
        String message,
        String conversationId,
        String pageContext,
        String cursorId,
        /**
         * 前端临时会话历史。
         * <p>
         * 用于 SSE 中断、回答尚未落库、页面本地已有上下文但 MySQL 暂时查不到历史时的短期上下文兜底。
         * 后端只接受 user / assistant 两类角色，并会在入库前做去重和长度控制。
         */
        List<ClientHistoryMessage> clientHistory) {

    public record ClientHistoryMessage(String role, String content) {
    }
}
