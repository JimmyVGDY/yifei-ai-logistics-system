package jimmy.ai.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * 每日运营简报 VO —— AI 自动生成的昨日数据汇总。
 *
 * @param date           简报日期
 * @param summary        自然语言摘要
 * @param keyMetrics     关键指标 [{label, value, trend}]
 * @param anomalies      异常提示列表
 * @param suggestions    优化建议列表
 * @param generatedAt    生成时间
 */
public record DailyBriefingVO(
        String date,
        String summary,
        List<MetricItem> keyMetrics,
        List<String> anomalies,
        List<String> suggestions,
        String generatedAt
) {
    /** 单个关键指标项 */
    public record MetricItem(String label, String value, String trend) {
    }
}
