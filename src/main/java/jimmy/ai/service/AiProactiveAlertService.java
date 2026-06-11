package jimmy.ai.service;

import jimmy.ai.model.DailyBriefingVO;
import jimmy.logistics.mapper.LogisticsDashboardMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI 主动预警服务 —— 定时生成每日运营简报，检测异常模式。
 * <p>
 * 通过 XXL-Job 定时触发（建议每日凌晨执行），
 * 结合数据库统计数据和 AI 模型生成自然语言摘要。
 * AI 不可用时降级为纯统计数据报告。
 */
@Slf4j
@Service
public class AiProactiveAlertService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LogisticsDashboardMapper dashboardMapper;
    private final AiModelGateway modelGateway;
    private final AiSensitiveDataMasker masker;

    public AiProactiveAlertService(LogisticsDashboardMapper dashboardMapper,
                                    AiModelGateway modelGateway,
                                    AiSensitiveDataMasker masker) {
        this.dashboardMapper = dashboardMapper;
        this.modelGateway = modelGateway;
        this.masker = masker;
    }

    /**
     * 生成每日运营简报。
     * <p>
     * 优先使用 AI 模型生成自然语言摘要，不可用时降级为纯数据报告。
     *
     * @return 简报内容
     */
    public DailyBriefingVO generateDailyBriefing() {
        String today = LocalDate.now().format(DATE_FORMAT);
        log.info("AI 每日运营简报生成开始，date={}", today);

        // 1. 收集统计数据
        Long todayOrders = safeLong(dashboardMapper.countTodayOrders(null));
        BigDecimal monthIncome = safeBigDecimal(dashboardMapper.sumPaidMonthIncome(null));
        int exceptionCount = dashboardMapper.countLastMonthExceptions();
        Long fileCount = dashboardMapper.countUploadedFiles();

        // 2. 构建数据摘要
        List<DailyBriefingVO.MetricItem> metrics = new ArrayList<>();
        metrics.add(new DailyBriefingVO.MetricItem("今日订单", String.valueOf(todayOrders), ""));
        metrics.add(new DailyBriefingVO.MetricItem("本月收入", monthIncome.toString(), ""));
        metrics.add(new DailyBriefingVO.MetricItem("上月异常", String.valueOf(exceptionCount), exceptionCount > 10 ? "↑偏高" : "正常"));
        metrics.add(new DailyBriefingVO.MetricItem("上传文件", fileCount != null ? String.valueOf(fileCount) : "0", ""));

        // 3. AI 生成摘要（不可用时降级）
        String summary = generateAiSummary(todayOrders, monthIncome, exceptionCount);
        List<String> anomalies = detectAnomalies(todayOrders, exceptionCount);
        List<String> suggestions = generateSuggestions(exceptionCount);

        log.info("AI 每日运营简报生成完成，date={}, anomalies={}", today, anomalies.size());
        return new DailyBriefingVO(today, summary, metrics, anomalies, suggestions,
                java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    /**
     * 检测异常模式：基于简单规则判断是否触发告警。
     */
    public List<String> detectAnomalies() {
        List<String> anomalies = new ArrayList<>();
        try {
            Long todayOrders = safeLong(dashboardMapper.countTodayOrders(null));
            int exceptionCount = dashboardMapper.countLastMonthExceptions();
            return detectAnomalies(todayOrders, exceptionCount);
        } catch (RuntimeException exception) {
            log.debug("AI 异常检测失败，reason={}", exception.getMessage());
        }
        return anomalies;
    }

    /**
     * 使用 AI 模型生成自然语言摘要。
     */
    private String generateAiSummary(Long todayOrders, BigDecimal monthIncome, int exceptionCount) {
        if (!modelGateway.configured()) {
            return fallbackSummary(todayOrders, monthIncome, exceptionCount);
        }
        try {
            String prompt = "请根据以下数据生成一段 50 字以内的物流平台每日运营简报："
                    + "今日订单数:" + todayOrders
                    + "，本月收入:" + monthIncome
                    + "，上月异常订单数:" + exceptionCount;
            Optional<String> result = modelGateway.chat(
                    "你是物流平台的运营分析助手，请用简洁专业的中文回复。",
                    prompt, "daily_briefing");
            return result.orElseGet(() -> fallbackSummary(todayOrders, monthIncome, exceptionCount));
        } catch (RuntimeException exception) {
            log.debug("AI 简报生成失败，降级纯数据，reason={}", exception.getMessage());
            return fallbackSummary(todayOrders, monthIncome, exceptionCount);
        }
    }

    private String fallbackSummary(Long todayOrders, BigDecimal monthIncome, int exceptionCount) {
        return "今日平台新增订单 " + todayOrders + " 单，本月累计收入 " + monthIncome + " 元。"
                + "上月异常订单 " + exceptionCount + " 单"
                + (exceptionCount > 10 ? "，异常率偏高，建议关注。" : "，运营状态正常。");
    }

    private List<String> detectAnomalies(Long todayOrders, int exceptionCount) {
        List<String> anomalies = new ArrayList<>();
        if (todayOrders == 0) {
            anomalies.add("今日暂无新订单，请检查系统是否正常运行");
        }
        if (exceptionCount > 10) {
            anomalies.add("上月异常订单数偏高（" + exceptionCount + " 单），建议排查异常原因");
        }
        return anomalies;
    }

    private List<String> generateSuggestions(int exceptionCount) {
        List<String> suggestions = new ArrayList<>();
        if (exceptionCount > 10) {
            suggestions.add("建议查看操作日志中上月异常高频关键词，定位异常根因");
            suggestions.add("建议检查上月运力调度是否存在瓶颈");
        }
        return suggestions;
    }

    private long safeLong(Long value) { return value == null ? 0 : value; }
    private BigDecimal safeBigDecimal(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
