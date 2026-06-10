package jimmy.logistics.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import jimmy.common.trace.TraceContextSupport;
import jimmy.logistics.mapper.LogisticsDashboardMapper;
import lombok.extern.slf4j.Slf4j;
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
    private final TraceContextSupport traceContextSupport;

    public LogisticsScheduledJobs(LogisticsDashboardMapper dashboardMapper,
                                  TraceContextSupport traceContextSupport) {
        this.dashboardMapper = dashboardMapper;
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
