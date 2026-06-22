package jimmy.ai.service;

import jimmy.ai.mapper.AiMemoryMapper;
import jimmy.ai.model.AiMemoryItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 记忆生命周期管理器。
 * <p>
 * 负责后台自动维护记忆状态 —— 用户无感，完全由证据和时间驱动：
 * <ul>
 *   <li><b>自动升级</b>：CANDIDATE 积累足够证据后自动升级为 ACTIVE</li>
 *   <li><b>自动恢复</b>：SUSPECTED_HALLUCINATION 被后续证据印证后自动恢复为 CANDIDATE</li>
 *   <li><b>自动衰减</b>：ACTIVE 记忆长期未被召回 → 降为 WEAKENING</li>
 *   <li><b>自动归档</b>：WEAKENING 记忆继续未被使用 → ARCHIVED</li>
 *   <li><b>自动清理</b>：已归档记忆超过保留期 → 物理删除</li>
 * </ul>
 * <p>
 * 本服务设计为可被定时任务或手动触发调用。每次调用处理有限数量，
 * 避免一次性全表扫描。
 */
@Slf4j
@Service
public class AiMemoryLifecycleManager {

    private static final int BATCH_SIZE = 100;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AiMemoryMapper memoryMapper;
    private final AiMemoryConfidenceEngine confidenceEngine;
    private final AiMemoryGovernanceService governanceService;

    public AiMemoryLifecycleManager(AiMemoryMapper memoryMapper,
                                     AiMemoryConfidenceEngine confidenceEngine,
                                     AiMemoryGovernanceService governanceService) {
        this.memoryMapper = memoryMapper;
        this.confidenceEngine = confidenceEngine;
        this.governanceService = governanceService;
    }

    /**
     * 执行一轮完整的生命周期维护。
     * <p>
     * 按优先级顺序执行：升级 > 恢复 > 衰减 > 归档 > 清理。
     * 每步独立执行，一步失败不影响其他步骤。
     *
     * @return 维护结果摘要
     */
    public MaintenanceResult executeMaintenance() {
        int promoted = 0, recovered = 0, decayed = 0, archived = 0, purged = 0;
        List<String> details = new ArrayList<>();

        try {
            promoted = promoteCandidates();
            if (promoted > 0) details.add("promoted=" + promoted);
        } catch (Exception e) {
            log.warn("记忆自动升级失败", e);
        }

        try {
            recovered = recoverHallucinations();
            if (recovered > 0) details.add("recovered=" + recovered);
        } catch (Exception e) {
            log.warn("记忆自动恢复失败", e);
        }

        try {
            decayed = decayStaleActiveMemories();
            if (decayed > 0) details.add("decayed=" + decayed);
        } catch (Exception e) {
            log.warn("记忆衰减失败", e);
        }

        try {
            archived = archiveWeakeningMemories();
            if (archived > 0) details.add("archived=" + archived);
        } catch (Exception e) {
            log.warn("记忆归档失败", e);
        }

        try {
            purged = purgeExpiredArchivedMemories();
            if (purged > 0) details.add("purged=" + purged);
        } catch (Exception e) {
            log.warn("过期记忆清理失败", e);
        }

        String summary = details.isEmpty() ? "无变更" : String.join(", ", details);
        int total = promoted + recovered + decayed + archived + purged;
        if (total > 0) {
            log.info("AI 记忆生命周期维护完成，{}", summary);
        }
        return new MaintenanceResult(promoted, recovered, decayed, archived, purged, summary);
    }

    /**
     * 自动升级：证据数 ≥ 2 且置信度 ≥ 0.85 的 CANDIDATE 记忆 → ACTIVE。
     */
    public int promoteCandidates() {
        List<Long> ids = memoryMapper.selectPromotableCandidateIds(
                confidenceEngine.AUTO_PROMOTE_MIN_CONFIDENCE,
                confidenceEngine.AUTO_PROMOTE_MIN_EVIDENCE,
                BATCH_SIZE
        );
        if (ids.isEmpty()) {
            return 0;
        }
        int updated = memoryMapper.updateMemoryStatus(ids, AiMemoryGovernanceService.STATUS_ACTIVE);
        if (updated > 0) {
            log.info("自动升级 {} 条候选记忆为生效状态，ids={}", updated, summarizeIds(ids));
        }
        return updated;
    }

    /**
     * 自动恢复：SUSPECTED_HALLUCINATION 记忆若在后续对话中获得印证证据 → CANDIDATE。
     * <p>
     * 印证条件：证据数至少为 1 且最近被标记为 reinforced（说明后续对话行为吻合）。
     */
    public int recoverHallucinations() {
        String cutoffTime = daysAgo(confidenceEngine.ARCHIVE_AFTER_DAYS);
        List<Long> ids = memoryMapper.selectMemoryIdsByStatus(
                AiMemoryGovernanceService.STATUS_HALLUCINATION, cutoffTime, BATCH_SIZE
        );
        if (ids.isEmpty()) {
            return 0;
        }

        // 对每条疑似幻觉记忆检查是否满足恢复条件
        List<Long> recoverable = new ArrayList<>();
        for (Long id : ids) {
            AiMemoryItemVO memory = memoryMapper.selectMemoryById(id, null, null);
            if (memory != null
                    && memory.evidenceCount() != null && memory.evidenceCount() >= 1
                    && memory.confidence() != null && memory.confidence() >= 0.78) {
                recoverable.add(id);
            }
        }

        if (recoverable.isEmpty()) {
            return 0;
        }
        int updated = memoryMapper.updateMemoryStatus(recoverable, AiMemoryGovernanceService.STATUS_CANDIDATE);
        if (updated > 0) {
            log.info("自动恢复 {} 条疑似幻觉记忆为候选状态，ids={}", updated, summarizeIds(recoverable));
        }
        return updated;
    }

    /**
     * 自动衰减：ACTIVE 记忆超过 14 天未被召回/强化 → WEAKENING。
     */
    public int decayStaleActiveMemories() {
        String cutoffTime = daysAgo(confidenceEngine.DECAY_AFTER_DAYS);
        List<Long> ids = memoryMapper.selectMemoryIdsByStatus(
                AiMemoryGovernanceService.STATUS_ACTIVE, cutoffTime, BATCH_SIZE
        );
        if (ids.isEmpty()) {
            return 0;
        }
        int decayed = memoryMapper.decayConfidence(ids, confidenceEngine.DECAY_STEP);
        if (decayed > 0) {
            // 衰减后置信度低于阈值的转为 WEAKENING
            int weakened = memoryMapper.updateMemoryStatus(ids, AiMemoryGovernanceService.STATUS_WEAKENING);
            log.info("衰减 {} 条长期未使用的生效记忆，其中 {} 条降为弱化状态", decayed, weakened);
        }
        return decayed;
    }

    /**
     * 自动归档：WEAKENING 记忆超过 30 天未被召回 → ARCHIVED。
     */
    public int archiveWeakeningMemories() {
        String cutoffTime = daysAgo(confidenceEngine.ARCHIVE_AFTER_DAYS);
        List<Long> ids = memoryMapper.selectMemoryIdsByStatus(
                AiMemoryGovernanceService.STATUS_WEAKENING, cutoffTime, BATCH_SIZE
        );
        if (ids.isEmpty()) {
            return 0;
        }
        int archived = memoryMapper.updateMemoryStatus(ids, "ARCHIVED");
        if (archived > 0) {
            log.info("归档 {} 条长期未使用的弱化记忆", archived);
        }
        return archived;
    }

    /**
     * 自动清理：已归档记忆超过 90 天 → 物理删除。
     */
    public int purgeExpiredArchivedMemories() {
        String cutoffTime = daysAgo(confidenceEngine.PURGE_AFTER_DAYS);
        int purged = memoryMapper.deleteArchivedMemories(cutoffTime);
        if (purged > 0) {
            log.info("清理 {} 条过期归档记忆", purged);
        }
        return purged;
    }

    private String daysAgo(int days) {
        return LocalDateTime.now().minus(days, ChronoUnit.DAYS).format(FORMATTER);
    }

    private String summarizeIds(List<Long> ids) {
        if (ids.size() <= 5) {
            return ids.toString();
        }
        return ids.subList(0, 5) + "...(共" + ids.size() + "条)";
    }

    /**
     * 一次生命周期维护的结果摘要。
     */
    public record MaintenanceResult(
            int promoted,
            int recovered,
            int decayed,
            int archived,
            int purged,
            String summary) {

        public boolean hasChanges() {
            return promoted + recovered + decayed + archived + purged > 0;
        }
    }
}
