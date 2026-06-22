package jimmy.logistics.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import jimmy.ai.service.AiMemoryService;
import jimmy.ai.service.AiProactiveAlertService;
import jimmy.common.trace.TraceContextSupport;
import jimmy.logistics.mapper.LogisticsDashboardMapper;
import jimmy.logistics.mapper.OperationLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 物流管理平台 XXL-Job 定时任务处理器。
 */
@Slf4j
@Component
public class LogisticsScheduledJobs {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LogisticsDashboardMapper dashboardMapper;
    private final OperationLogMapper operationLogMapper;
    private final AiProactiveAlertService proactiveAlertService;
    private final AiMemoryService aiMemoryService;
    private final TraceContextSupport traceContextSupport;

    @Value("${app.operation-log.retention-days:180}")
    private int operationLogRetentionDays;

    public LogisticsScheduledJobs(LogisticsDashboardMapper dashboardMapper,
                                  OperationLogMapper operationLogMapper,
                                  AiProactiveAlertService proactiveAlertService,
                                  AiMemoryService aiMemoryService,
                                  TraceContextSupport traceContextSupport) {
        this.dashboardMapper = dashboardMapper;
        this.operationLogMapper = operationLogMapper;
        this.proactiveAlertService = proactiveAlertService;
        this.aiMemoryService = aiMemoryService;
        this.traceContextSupport = traceContextSupport;
    }

    /**
     * 合同到期预警：扫描未来 30 天内到期的合同。
     */
    @XxlJob("contractExpireAlert")
    public void contractExpireAlert() {
        runJob("contractExpireAlert", () -> {
            log.info("定时任务合同到期预警开始");
            List<Map<String, Object>> expiringContracts = dashboardMapper.selectExpiringContracts(30);
            if (expiringContracts.isEmpty()) {
                log.info("定时任务合同到期预警完成，无到期合同");
                return;
            }
            log.warn("{} 份合同即将到期", expiringContracts.size());
            for (Map<String, Object> contract : expiringContracts) {
                log.warn("合同到期预警，employeeId={}, contractNo={}, endDate={}",
                        contract.get("employeeId"),
                        contract.get("contractNo"),
                        contract.get("endDate"));
            }
            log.info("定时任务合同到期预警完成，共 {} 份", expiringContracts.size());
        });
    }

    /**
     * 月度收入报表：统计上月订单、收入和异常数量。
     */
    @XxlJob("monthlyIncomeReport")
    public void monthlyIncomeReport() {
        runJob("monthlyIncomeReport", () -> {
            log.info("定时任务月度收入报表开始");
            BigDecimal monthIncome = dashboardMapper.sumPaidMonthIncome(null);
            if (monthIncome == null) {
                monthIncome = BigDecimal.ZERO;
            }
            Long lastMonthOrders = dashboardMapper.countLastMonthOrders();
            if (lastMonthOrders == null) {
                lastMonthOrders = 0L;
            }
            int exceptionCount = dashboardMapper.countLastMonthExceptions();

            log.info("月度收入报表，统计时间={}", LocalDate.now().format(DATE_FORMAT));
            log.info("月度收入报表，上月订单数={}", lastMonthOrders);
            log.info("月度收入报表，上月收入={}", monthIncome);
            log.info("月度收入报表，上月异常订单数={}", exceptionCount);
            log.info("定时任务月度收入报表完成");
        });
    }

    /**
     * 临时文件清理：当前只统计上传文件数量，后续可扩展真实清理逻辑。
     */
    @XxlJob("tempFileCleanup")
    public void tempFileCleanup() {
        runJob("tempFileCleanup", () -> {
            log.info("定时任务临时文件清理开始");
            Long fileCount = dashboardMapper.countUploadedFiles();
            log.info("上传文件总数={}", fileCount == null ? 0 : fileCount);
            log.info("定时任务临时文件清理完成，当前仅统计未执行清理");
        });
    }

    /**
     * 缓存预热：当前只输出预热统计，实际缓存逻辑后续接入。
     */
    @XxlJob("cachePreheat")
    public void cachePreheat() {
        runJob("cachePreheat", () -> {
            log.info("定时任务缓存预热开始");
            Long todayOrders = dashboardMapper.countTodayOrders(null);
            log.info("定时任务缓存预热完成，今日订单数={}", todayOrders == null ? 0 : todayOrders);
        });
    }

    /**
     * 操作日志归档：将超过保留期的操作日志分批迁移至归档表 sys_operation_log_archive。
     * <p>
     * 保留天数通过 {@code app.operation-log.retention-days} 配置（默认 180 天）。
     * 每批迁移 5000 条，避免长事务锁表阻塞正常写入。
     * 建议每月执行一次（XXL-Job Cron: {@code 0 0 2 1 * ?}）。
     */
    @XxlJob("operationLogArchive")
    public void operationLogArchive() {
        runJob("operationLogArchive", () -> {
            log.info("操作日志归档开始，保留天数={}", operationLogRetentionDays);
            int archived = operationLogMapper.archiveOperationLogs(operationLogRetentionDays);
            log.info("操作日志归档完成，本次归档 {} 条", archived);
        });
    }

    /**
     * AI 每日运营简报：利用 AI 模型生成自然语言运营摘要和异常检测。
     * <p>
     * 建议每日凌晨执行（XXL-Job Cron: {@code 0 0 6 * * ?}）。
     * AI 模型不可用时自动降级为纯数据统计报告。
     */
    @XxlJob("dailyBriefing")
    public void dailyBriefing() {
        runJob("dailyBriefing", () -> {
            log.info("AI 每日运营简报生成开始");
            var briefing = proactiveAlertService.generateDailyBriefing();
            log.info("AI 每日运营简报生成完成，date={}, anomalies={}, suggestions={}",
                    briefing.date(), briefing.anomalies().size(), briefing.suggestions().size());
            for (String anomaly : briefing.anomalies()) {
                log.warn("AI 运营异常告警：{}", anomaly);
            }
        });
    }

    /**
     * AI 异常检测：定期扫描业务数据，发现异常模式时输出告警日志。
     */
    @XxlJob("anomalyDetection")
    public void anomalyDetection() {
        runJob("anomalyDetection", () -> {
            log.info("AI 异常检测开始");
            var anomalies = proactiveAlertService.detectAnomalies();
            if (anomalies.isEmpty()) {
                log.info("AI 异常检测完成，未发现异常");
            } else {
                for (String anomaly : anomalies) {
                    log.warn("AI 异常检测告警：{}", anomaly);
                }
            }
        });
    }

    /**
     * AI 长期记忆生命周期维护：自动升级候选记忆、恢复疑似幻觉、衰减过期记忆、清理归档记忆。
     * <p>
     * 建议每天凌晨执行一次（XXL-Job Cron: {@code 0 7 3 * * ?}）。
     * 整个过程对用户完全无感，仅在日志中记录变更摘要。
     */
    @XxlJob("memoryLifecycleMaintenance")
    public void memoryLifecycleMaintenance() {
        runJob("memoryLifecycleMaintenance", () -> {
            log.info("AI 长期记忆生命周期维护开始");
            var result = aiMemoryService.runMaintenance();
            log.info("AI 长期记忆生命周期维护完成，summary={}", result.summary());
        });
    }

    private void runJob(String jobName, Runnable task) {
        String jobRunId = traceContextSupport.bindJobContext(jobName);
        try {
            log.info("定时任务上下文已创建，jobName={}, jobRunId={}", jobName, jobRunId);
            task.run();
        } catch (Exception e) {
            log.error("定时任务执行失败，jobName={}, jobRunId={}", jobName, jobRunId, e);
        } finally {
            traceContextSupport.clearTraceContext();
        }
    }
}
