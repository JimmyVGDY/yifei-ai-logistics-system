package jimmy.ai.model;

/**
 * AI 回答引用来源 —— 告诉用户回答来自文档、日志或业务查询。
 */
public record AiCitation(String sourceType, String title, String reference, String snippet) {
}
