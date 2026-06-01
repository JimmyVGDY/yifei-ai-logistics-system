package jimmy.logistics.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import jimmy.logistics.mapper.LogisticsDashboardMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 物流管理平台 — XXL-Job 定时任务处理器。
 * <p>
 * 每个任务方法通过 {@code @XxlJob} 注解注册为 XXL-Job Handler，
 * 在调度中心配置 Cron 表达式后即可自动执行。
 * </p>
 */
@Slf4j
@Component
public class LogisticsScheduledJobs {

    /** 线程安全的日期格式化器，替代 SimpleDateFormat */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LogisticsDashboardMapper dashboardMapper;

    public LogisticsScheduledJobs(LogisticsDashboardMapper dashboardMapper) {
        this.dashboardMapper = dashboardMapper;
    }

    /**
     * 合同到期预警 —— 每天上午 8 点执行。
     * <p>
     * 扫描未来 30 天内到期的合同，输出告警日志。
     * 后续可扩展为发送邮件/企业微信通知。
     * </p>
     */
    @XxlJob("contractExpireAlert")
    public void contractExpireAlert() {
        log.info("【定时任务】合同到期预警开始");
        try {
            // 查询 30 天内到期的合同
            List<Map<String, Object>> expiringContracts = dashboardMapper.selectExpiringContracts(30);
            if (expiringContracts.isEmpty()) {
                log.info("【定时任务】合同到期预警完成，无到期合同");
                return;
            }
            
            log.warn("【定时任务】{} 份合同即将到期：", expiringContracts.size());
            for (Map<String, Object> contract : expiringContracts) {
                log.warn("  - 员工ID={}, 合同编号={}, 到期日期={}",
                        contract.get("employeeId"),
                        contract.get("contractNo"),
                        contract.get("endDate"));
            }
            log.info("【定时任务】合同到期预警完成，共 {} 份", expiringContracts.size());
        } catch (Exception e) {
            log.error("【定时任务】合同到期预警执行失败", e);
        }
    }

    /**
     * 月度收入汇总 —— 每月 1 号上午 8 点执行。
     * <p>
     * 统计上月已付款订单的总收入，输出汇总日志。
     * 后续可扩展为生成 PDF 报表并自动发送给财务部门。
     * </p>
     */
    @XxlJob("monthlyIncomeReport")
    public void monthlyIncomeReport() {
        log.info("【定时任务】月度收入报表开始");
        try {
            BigDecimal monthIncome = dashboardMapper.sumPaidMonthIncome(null);
            if (monthIncome == null) {
                monthIncome = BigDecimal.ZERO;
            }
            // 上月订单统计
            Long lastMonthOrders = dashboardMapper.countLastMonthOrders();
            if (lastMonthOrders == null) {
                lastMonthOrders = 0L;
            }
            // 上月异常订单数
            int exceptionCount = dashboardMapper.countLastMonthExceptions();

            log.info("═══════════════════════════════════");
            log.info("  月度收入报表");
            log.info("  统计时间: {}", LocalDate.now().format(DATE_FORMAT));
            log.info("  上月订单数: {}", lastMonthOrders);
            log.info("  上月收入: ¥{}", monthIncome);
            log.info("  异常订单数: {}", exceptionCount);
            log.info("═══════════════════════════════════");
            log.info("【定时任务】月度收入报表完成");
        } catch (Exception e) {
            log.error("【定时任务】月度收入报表执行失败", e);
        }
    }

    /**
     * 临时文件清理 —— 每天凌晨 4 点执行。
     * <p>
     * 记录当前上传文件目录大小，后续可扩展自动清理 30 天前的文件。
     * </p>
     */
    @XxlJob("tempFileCleanup")
    public void tempFileCleanup() {
        log.info("【定时任务】临时文件清理开始");
        try {
            // 当前仅记录统计信息，实际清理逻辑后续实现
            Long fileCount = dashboardMapper.countUploadedFiles();
            log.info("【定时任务】上传文件总数: {}", fileCount == null ? 0 : fileCount);
            log.info("【定时任务】临时文件清理完成（仅统计，未执行清理）");
        } catch (Exception e) {
            log.error("【定时任务】临时文件清理执行失败", e);
        }
    }

    /**
     * 缓存预热 —— 每天早上 6 点执行。
     * <p>
     * 预先查询近期热点订单，确保上班高峰缓存命中。
     * 当前仅输出预热标记，实际预热逻辑在 LogisticsOrderCacheService 中。
     * </p>
     */
    @XxlJob("cachePreheat")
    public void cachePreheat() {
        log.info("【定时任务】缓存预热开始");
        try {
            // 查询近期订单数量作为预热参考
            Long todayOrders = dashboardMapper.countTodayOrders(null);
            log.info("【定时任务】缓存预热完成，今日订单数: {}", todayOrders == null ? 0 : todayOrders);
        } catch (Exception e) {
            log.error("【定时任务】缓存预热执行失败", e);
        }
    }
}
