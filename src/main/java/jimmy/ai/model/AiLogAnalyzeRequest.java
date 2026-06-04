package jimmy.ai.model;

/**
 * AI 日志排障请求 —— 支持按链路标识、用户、接口和时间范围查询操作日志。
 */
public record AiLogAnalyzeRequest(
        String traceId,
        String operationId,
        String loginSessionId,
        String userId,
        String uri,
        String startTime,
        String endTime) {
}
