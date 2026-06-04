package jimmy.ai.model;

import java.util.List;
import java.util.Map;

/**
 * AI 日志排障响应 —— 以时间线和风险点形式展示排查结论。
 */
public record AiLogAnalysisResponse(
        String summary,
        List<AiLogTimelineItem> timeline,
        List<String> riskPoints,
        List<String> suggestions,
        List<Map<String, Object>> relatedLogs) {
}
