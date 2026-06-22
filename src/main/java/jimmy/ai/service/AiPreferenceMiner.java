package jimmy.ai.service;

import jimmy.ai.mapper.AiMemoryMapper;
import jimmy.ai.model.AiMemoryItemVO;
import jimmy.ai.model.AiMemoryOwnerVO;
import jimmy.logistics.util.ColumnExistenceChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * AI 用户偏好自动挖掘器。
 * <p>
 * 在服务启动时和每日凌晨执行，主要做两件事：
 * <ol>
 *   <li><b>生命周期维护</b>：委托 {@link AiMemoryLifecycleManager} 处理记忆的
 *       自动升级、衰减、归档和清理</li>
 *   <li><b>画像编译</b>：基于当前生效记忆重新编译用户画像快照</li>
 * </ol>
 */
@Slf4j
@Component
public class AiPreferenceMiner {

    private static final int BATCH_SIZE = 200;

    private final AiMemoryMapper memoryMapper;
    private final ColumnExistenceChecker columnChecker;
    private final Executor aiChatExecutor;
    private final AiUserProfileCompiler profileCompiler;
    private final AiMemoryLifecycleManager lifecycleManager;

    public AiPreferenceMiner(AiMemoryMapper memoryMapper,
                             ColumnExistenceChecker columnChecker,
                             @Qualifier("aiChatExecutor") Executor aiChatExecutor,
                             AiUserProfileCompiler profileCompiler,
                             AiMemoryLifecycleManager lifecycleManager) {
        this.memoryMapper = memoryMapper;
        this.columnChecker = columnChecker;
        this.aiChatExecutor = aiChatExecutor;
        this.profileCompiler = profileCompiler;
        this.lifecycleManager = lifecycleManager;
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
                runMining();
                log.info("用户偏好挖掘完成");
            } catch (Exception e) {
                log.error("启动时偏好挖掘异常，已忽略", e);
            }
        }, aiChatExecutor);
    }

    /**
     * 每日凌晨 2 点定时挖掘。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void mineDaily() {
        log.info("定时挖掘用户偏好开始...");
        try {
            runMining();
        } catch (Exception e) {
            log.error("每日偏好挖掘异常，已忽略", e);
        }
        log.info("定时挖掘用户偏好完成");
    }

    // ==================== 挖掘逻辑 ====================

    private void runMining() {
        // 1. 生命周期维护 —— 委托统一状态机
        if (lifecycleColumnsReady()) {
            var result = lifecycleManager.executeMaintenance();
            if (result.hasChanges()) {
                log.info("记忆生命周期维护: {}", result.summary());
            }
        } else {
            log.info("AI 长期记忆生命周期字段未就绪，跳过生命周期维护");
        }

        // 2. 画像编译 —— 基于生效记忆重新编译用户画像
        compileProfiles();
    }

    // ==================== 画像编译 ====================

    private void compileProfiles() {
        if (!governanceColumnsReady()) {
            return;
        }
        List<AiMemoryOwnerVO> owners = memoryMapper.selectDistinctActiveMemoryOwners(BATCH_SIZE);
        for (AiMemoryOwnerVO owner : owners) {
            String userId = owner.userId();
            String userCode = owner.userCode();
            List<AiMemoryItemVO> memories = memoryMapper.selectActiveMemoriesForProfile(userId, userCode, 200);
            AiUserProfileCompiler.ProfileSnapshot snapshot = profileCompiler.compile(memories, "先给结论，再列关键依据");
            memoryMapper.updateProfileCompiled(userId, userCode, snapshot.answerStyle(), snapshot.favoriteModules(),
                    snapshot.queryHabits(), snapshot.answerStyleJson(), snapshot.queryStrategyJson(),
                    snapshot.moduleAffinityJson(), snapshot.profileConfidence());
        }
    }

    // ==================== 字段就绪检查 ====================

    private boolean lifecycleColumnsReady() {
        return columnChecker.hasColumn("ai_user_memory", "status")
                && columnChecker.hasColumn("ai_user_memory", "last_reinforced_at")
                && columnChecker.hasColumn("ai_user_memory", "reinforce_count");
    }

    private boolean governanceColumnsReady() {
        return lifecycleColumnsReady()
                && columnChecker.hasColumn("ai_user_memory", "memory_scope")
                && columnChecker.hasColumn("ai_user_memory", "conflict_group")
                && columnChecker.hasColumn("ai_user_profile", "profile_version");
    }
}
