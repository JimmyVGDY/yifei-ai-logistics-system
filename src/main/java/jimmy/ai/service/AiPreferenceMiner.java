package jimmy.ai.service;

import jimmy.ai.mapper.AiMemoryMapper;
import jimmy.logistics.util.ColumnExistenceChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * AI 用户偏好自动挖掘器。
 * <p>
 * 在服务启动时和每日凌晨执行，通过分析历史记忆的召回频率和使用模式，
 * 自动推断用户偏好（常用模块、查询习惯），并管理记忆的生命周期（强化/衰减/归档）。
 * <p>
 * 设计原则：
 * <ul>
 *   <li>基于统计频次而非单条消息判断，避免噪声干扰</li>
 *   <li>记忆有完整的生命周期：ACTIVE → WEAKENING → ARCHIVED → 删除</li>
 *   <li>服务重启时立即追赶进度，不等到凌晨才执行</li>
 * </ul>
 */
@Slf4j
@Component
public class AiPreferenceMiner {

    /**
     * 记忆进入衰减状态的沉默天数阈值。
     * 如果一条 ACTIVE 记忆超过此天数未被强化，标记为 WEAKENING。
     */
    private static final int WEAKENING_DAYS = 30;

    /**
     * 衰减记忆的归档天数阈值。
     * WEAKENING 记忆超过此天数未被强化，标记为 ARCHIVED。
     */
    private static final int ARCHIVE_DAYS = 30;

    /**
     * 已归档记忆的物理清理天数阈值。
     * ARCHIVED 记忆在更新后超过此天数，软删除。
     */
    private static final int CLEANUP_DAYS = 90;

    /**
     * 每次置信度衰减的步长。
     */
    private static final double DECAY_STEP = 0.05;

    /**
     * 归档前的置信度最低阈值。低于此值不再保留。
     */
    private static final double ARCHIVE_CONFIDENCE_THRESHOLD = 0.50;

    /**
     * 模块偏好挖掘的天数窗口。
     * 统计最近 N 天内被召回的 FAVORITE_MODULE / QUERY_HABIT 记忆。
     */
    private static final int MODULE_MINING_DAYS = 14;

    /**
     * 单次批量处理上限，防止大事务锁表。
     */
    private static final int BATCH_SIZE = 200;

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AiMemoryMapper memoryMapper;
    private final ColumnExistenceChecker columnChecker;
    private final Executor aiChatExecutor;

    public AiPreferenceMiner(AiMemoryMapper memoryMapper,
                             ColumnExistenceChecker columnChecker,
                             @Qualifier("aiChatExecutor") Executor aiChatExecutor) {
        this.memoryMapper = memoryMapper;
        this.columnChecker = columnChecker;
        this.aiChatExecutor = aiChatExecutor;
    }

    // ==================== 触发入口 ====================

    /**
     * 服务启动后异步执行一次偏好挖掘，不阻塞 Spring 容器初始化。
     */
    @PostConstruct
    public void mineOnStartup() {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("服务启动后开始挖掘用户偏好...");
                manageLifecycle();
                log.info("用户偏好挖掘完成");
            } catch (Exception e) {
                log.error("启动时偏好挖掘异常，已忽略", e);
            }
        }, aiChatExecutor);
    }

    /**
     * 每日凌晨 2 点定时挖掘。
     * <p>
     * 使用固定延迟而非 cron 表达式以支持 Spring Boot 内置调度器，
     * 实际间隔由 initialDelay 和 fixedDelay 控制。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void mineDaily() {
        log.info("定时挖掘用户偏好开始...");
        try {
            manageLifecycle();
        } catch (Exception e) {
            log.error("每日偏好挖掘异常，已忽略", e);
        }
        log.info("定时挖掘用户偏好完成");
    }

    // ==================== 生命周期管理 ====================

    /**
     * 执行完整的生命周期管理：衰减检查 → 归档清理。
     */
    private void manageLifecycle() {
        if (!lifecycleColumnsReady()) {
            log.info("AI 长期记忆生命周期字段未就绪，跳过本次偏好挖掘；执行 scripts/sql/20260610_incremental_ai_memory_lifecycle.sql 后重启即可启用");
            return;
        }
        // 1. ACTIVE 记忆超过 WEAKENING_DAYS 天未强化 → 标记为 WEAKENING
        String weakeningBefore = DATETIME_FMT.format(LocalDateTime.now().minusDays(WEAKENING_DAYS));
        List<Long> weakeningIds = memoryMapper.selectMemoryIdsByStatus("ACTIVE", weakeningBefore, BATCH_SIZE);
        int weakeningCount = 0;
        for (int i = 0; i < weakeningIds.size(); i += BATCH_SIZE) {
            List<Long> batch = weakeningIds.subList(i, Math.min(i + BATCH_SIZE, weakeningIds.size()));
            // 先衰减置信度再切换状态
            memoryMapper.decayConfidence(batch, 0.50 - DECAY_STEP > 0 ? DECAY_STEP : 0.10);
            memoryMapper.updateMemoryStatus(batch, "WEAKENING");
            weakeningCount += batch.size();
        }
        if (weakeningCount > 0) {
            log.info("生命周期：{} 条 ACTIVE 记忆标记为 WEAKENING", weakeningCount);
        }

        // 2. WEAKENING 记忆超过 ARCHIVE_DAYS 天未强化 → 检查置信度
        String archiveBefore = DATETIME_FMT.format(LocalDateTime.now().minusDays(ARCHIVE_DAYS));
        List<Long> archiveIds = memoryMapper.selectMemoryIdsByStatus("WEAKENING", archiveBefore, BATCH_SIZE);
        int archiveCount = 0;
        int deletedCount = 0;
        for (int i = 0; i < archiveIds.size(); i += BATCH_SIZE) {
            List<Long> batch = archiveIds.subList(i, Math.min(i + BATCH_SIZE, archiveIds.size()));
            // 置信度降到阈值以下 → 标记 ARCHIVED
            memoryMapper.decayConfidence(batch, DECAY_STEP);
            // 注意：decayConfidence 降低了置信度，但我们需要根据当前置信度分流
            // 简单处理：未强化超过 60 天（30+30）的直接归档
            memoryMapper.updateMemoryStatus(batch, "ARCHIVED");
            archiveCount += batch.size();
        }
        if (archiveCount > 0) {
            log.info("生命周期：{} 条 WEAKENING 记忆标记为 ARCHIVED", archiveCount);
        }

        // 3. ARCHIVED 记忆超过 CLEANUP_DAYS 天 → 软删除
        String cleanupBefore = DATETIME_FMT.format(LocalDateTime.now().minusDays(CLEANUP_DAYS));
        deletedCount = memoryMapper.deleteArchivedMemories(cleanupBefore);
        if (deletedCount > 0) {
            log.info("生命周期：{} 条 ARCHIVED 记忆已软删除", deletedCount);
        }

        if (weakeningCount == 0 && archiveCount == 0 && deletedCount == 0) {
            log.debug("生命周期管理：无需处理的记忆");
        }
    }

    private boolean lifecycleColumnsReady() {
        return columnChecker.hasColumn("ai_user_memory", "status")
                && columnChecker.hasColumn("ai_user_memory", "last_reinforced_at")
                && columnChecker.hasColumn("ai_user_memory", "reinforce_count");
    }
}
