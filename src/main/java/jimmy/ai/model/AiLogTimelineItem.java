package jimmy.ai.model;

/**
 * 日志排障时间线节点。
 */
public record AiLogTimelineItem(
        String time,
        String operation,
        String uri,
        String method,
        String status,
        String costMs,
        String errorMessage) {
}
