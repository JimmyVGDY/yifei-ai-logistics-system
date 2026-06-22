package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.mapper.AiMemoryMapper;
import jimmy.ai.model.AiCitation;
import jimmy.ai.model.AiMemoryCandidate;
import jimmy.ai.model.AiMemoryGovernanceDecision;
import jimmy.ai.model.AiMemoryItemVO;
import jimmy.ai.model.AiMemoryProfileVO;
import jimmy.ai.model.AiMemoryRecallResult;
import jimmy.ai.model.AiMemorySettingsRequest;
import jimmy.ai.model.AiToolCall;
import jimmy.ai.util.SseChatContext;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.common.trace.TraceContextSupport;
import jimmy.logistics.util.ColumnExistenceChecker;
import jimmy.common.model.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * AI 长期记忆服务。
 * <p>
 * MySQL 保存可审计、可删除的真值；Qdrant 只做语义召回增强。
 */
@Slf4j
@Service
public class AiMemoryService {

    private static final String DEFAULT_ANSWER_STYLE = "先给结论，再列关键依据";
    private static final int MAX_SUMMARY_LENGTH = 500;
    private static final int MAX_RECALL_COUNT = 5;

    private final AiMemoryMapper memoryMapper;
    private final CompactSnowflakeIdGenerator idGenerator;
    private final TraceContextSupport traceContextSupport;
    private final AiSensitiveDataMasker masker;
    private final AiQdrantMemoryClient qdrantClient;
    private final ColumnExistenceChecker columnChecker;
    private final AiAuditLogService auditLogService;
    private final AiMemoryExtractor memoryExtractor;
    private final AiMemoryGovernanceService memoryGovernanceService;
    private final AiMemoryLifecycleManager lifecycleManager;
    private final AiUserProfileCompiler profileCompiler;

    public AiMemoryService(AiMemoryMapper memoryMapper,
                           CompactSnowflakeIdGenerator idGenerator,
                           TraceContextSupport traceContextSupport,
                           AiSensitiveDataMasker masker,
                           AiQdrantMemoryClient qdrantClient,
                           ColumnExistenceChecker columnChecker,
                           AiAuditLogService auditLogService,
                           AiMemoryExtractor memoryExtractor,
                           AiMemoryGovernanceService memoryGovernanceService,
                           AiMemoryLifecycleManager lifecycleManager,
                           AiUserProfileCompiler profileCompiler) {
        this.memoryMapper = memoryMapper;
        this.idGenerator = idGenerator;
        this.traceContextSupport = traceContextSupport;
        this.masker = masker;
        this.qdrantClient = qdrantClient;
        this.columnChecker = columnChecker;
        this.auditLogService = auditLogService;
        this.memoryExtractor = memoryExtractor;
        this.memoryGovernanceService = memoryGovernanceService;
        this.lifecycleManager = lifecycleManager;
        this.profileCompiler = profileCompiler;
    }

    public AiMemoryProfileVO profile() {
        UserScope scope = currentScope();
        if (!tablesExist()) {
            return defaultProfile(scope);
        }
        ensureProfile(scope);
        AiMemoryProfileVO profile = memoryMapper.selectProfile(scope.userId(), scope.userCode());
        return profile == null ? defaultProfile(scope) : profile;
    }

    public PageResult<AiMemoryItemVO> items(int page, int pageSize, String keyword, String memoryType) {
        return items(page, pageSize, keyword, memoryType, null);
    }

    public PageResult<AiMemoryItemVO> items(int page, int pageSize, String keyword, String memoryType, String status) {
        UserScope scope = currentScope();
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        if (!tablesExist()) {
            return new PageResult<>(List.of(), safePage, safePageSize, 0L);
        }
        ensureProfile(scope);
        long total = memoryMapper.countMemories(scope.userId(), scope.userCode(), normalize(keyword), normalize(memoryType), normalize(status));
        List<AiMemoryItemVO> records = memoryMapper.selectMemories(scope.userId(), scope.userCode(),
                normalize(keyword), normalize(memoryType), normalize(status), (long) (safePage - 1) * safePageSize, safePageSize);
        return new PageResult<>(maskRecords(records), safePage, safePageSize, total);
    }

    public AiMemoryProfileVO updateSettings(AiMemorySettingsRequest request) {
        UserScope scope = currentScope();
        if (!tablesExist()) {
            return defaultProfile(scope);
        }
        ensureProfile(scope);
        Boolean memoryEnabled = request == null ? Boolean.TRUE : request.memoryEnabled();
        String answerStyle = request == null ? DEFAULT_ANSWER_STYLE : normalize(request.answerStyle());
        memoryMapper.updateProfileSettings(scope.userId(), scope.userCode(), memoryEnabled, answerStyle);
        recordEvent(null, "SETTINGS_UPDATE", "USER", "用户更新 AI 长期记忆设置");
        return profile();
    }

    public void deleteMemory(Long id) {
        if (!tablesExist()) {
            return;
        }
        UserScope scope = currentScope();
        AiMemoryItemVO memory = memoryMapper.selectMemoryById(id, scope.userId(), scope.userCode());
        if (memory == null) {
            return;
        }
        memoryMapper.deleteMemory(id, scope.userId(), scope.userCode());
        qdrantClient.delete(memory.qdrantPointId());
        compileProfile(scope);
        recordEvent(id, "DELETE", "USER", "用户删除单条 AI 长期记忆：" + memory.memoryTitle());
    }

    public void approveMemory(Long id) {
        if (!tablesExist()) {
            return;
        }
        UserScope scope = currentScope();
        AiMemoryItemVO memory = memoryMapper.selectMemoryById(id, scope.userId(), scope.userCode());
        if (memory == null) {
            return;
        }
        memoryMapper.changeMemoryStatus(id, scope.userId(), scope.userCode(), AiMemoryGovernanceService.STATUS_ACTIVE);
        if (StringUtils.hasText(memory.conflictGroup())) {
            memoryMapper.supersedeConflictMemories(scope.userId(), scope.userCode(), memory.conflictGroup(), id);
        }
        compileProfile(scope);
        recordEvent(id, "APPROVE", "USER", "用户批准 AI 长期记忆：" + memory.memoryTitle());
    }

    public void rejectMemory(Long id) {
        if (!tablesExist()) {
            return;
        }
        UserScope scope = currentScope();
        AiMemoryItemVO memory = memoryMapper.selectMemoryById(id, scope.userId(), scope.userCode());
        if (memory == null) {
            return;
        }
        memoryMapper.changeMemoryStatus(id, scope.userId(), scope.userCode(), "REJECTED");
        compileProfile(scope);
        recordEvent(id, "REJECT", "USER", "用户拒绝 AI 长期记忆：" + memory.memoryTitle());
    }

    public void restoreMemory(Long id) {
        if (!tablesExist()) {
            return;
        }
        UserScope scope = currentScope();
        AiMemoryItemVO memory = memoryMapper.selectMemoryById(id, scope.userId(), scope.userCode());
        if (memory == null) {
            return;
        }
        memoryMapper.changeMemoryStatus(id, scope.userId(), scope.userCode(), AiMemoryGovernanceService.STATUS_ACTIVE);
        if (StringUtils.hasText(memory.conflictGroup())) {
            memoryMapper.supersedeConflictMemories(scope.userId(), scope.userCode(), memory.conflictGroup(), id);
        }
        compileProfile(scope);
        recordEvent(id, "RESTORE", "USER", "用户恢复 AI 长期记忆：" + memory.memoryTitle());
    }

    public void clearMemories() {
        if (!tablesExist()) {
            return;
        }
        UserScope scope = currentScope();
        List<AiMemoryItemVO> memories = memoryMapper.selectMemories(scope.userId(), scope.userCode(), null, null, null, 0, 1000);
        memoryMapper.clearMemories(scope.userId(), scope.userCode());
        for (AiMemoryItemVO memory : memories) {
            qdrantClient.delete(memory.qdrantPointId());
        }
        compileProfile(scope);
        recordEvent(null, "CLEAR", "USER", "用户清空 AI 长期记忆，count=" + memories.size());
    }

    public AiMemoryRecallResult recall(String question, String conversationId) {
        if (!tablesExist()) {
            return new AiMemoryRecallResult(false, 0, "", List.of());
        }
        UserScope scope = currentScope();
        AiMemoryProfileVO profile = profile();
        if (!Boolean.TRUE.equals(profile.memoryEnabled())) {
            return new AiMemoryRecallResult(false, 0, "", List.of());
        }
        String keyword = normalize(question);
        List<AiMemoryItemVO> memories = recallFromQdrant(scope, keyword);
        if (memories.isEmpty()) {
            memories = memoryMapper.selectRecallCandidates(scope.userId(), scope.userCode(), keyword, MAX_RECALL_COUNT);
        }
        memories = selectRecallableMemories(memories);
        memories = maskRecords(memories);
        if (memories.isEmpty()) {
            recordEvent(null, "RECALL_EMPTY", "AI_MEMORY", "未召回到可用长期记忆", conversationId);
            return new AiMemoryRecallResult(true, 0, "", List.of());
        }
        StringBuilder context = new StringBuilder();
        context.append("用户长期偏好：").append(profile.answerStyle()).append("\n");
        List<AiCitation> citations = new ArrayList<>();
        for (AiMemoryItemVO memory : memories) {
            memoryMapper.markMemoryRecalled(memory.id());
            memoryMapper.markMemoryApplied(memory.id());
            // 每次召回命中同时强化记忆，置信度自动递增
            if (memory.confidence() != null && memory.confidence() < 0.96) {
                memoryMapper.markMemoryReinforced(memory.id());
            }
            context.append("- ").append(memory.memoryTitle()).append("：").append(memory.memorySummary()).append("\n");
            citations.add(new AiCitation("AI_MEMORY", "长期记忆", "memory:" + memory.id(), memory.memorySummary()));
        }
        recordEvent(null, "RECALL", "AI_MEMORY",
                "AI 召回长期记忆，conversationId=" + conversationId + "，hitCount=" + memories.size(), conversationId);
        auditLogService.recordMemory(conversationId, "RECALL", "AI_MEMORY", null, memories.size(),
                "AI 召回当前账号长期记忆，hitCount=" + memories.size());
        return new AiMemoryRecallResult(true, memories.size(), context.toString(), citations);
    }

    public void rememberInteraction(String conversationId, String userMessage, String assistantMessage, List<AiToolCall> toolCalls) {
        if (!tablesExist()) {
            return;
        }
        AiMemoryProfileVO profile = profile();
        if (!Boolean.TRUE.equals(profile.memoryEnabled())) {
            recordEvent(null, "SKIP_DISABLED", "AI_MEMORY", "用户已关闭长期记忆，跳过自动写入", conversationId);
            return;
        }
        ExtractionResult extraction = extractCandidates(userMessage, assistantMessage, toolCalls);
        for (AiMemoryCandidate candidate : extraction.candidates()) {
            saveCandidate(conversationId, userMessage, toolCalls, candidate, extraction.isLlmExtracted());
        }
    }

    private void saveCandidate(String conversationId, String userMessage, List<AiToolCall> toolCalls,
                               AiMemoryCandidate candidate, boolean isLlmExtracted) {
        UserScope scope = currentScope();
        String summary = safeMemory(candidate.memorySummary());
        if (!StringUtils.hasText(summary) || containsSensitiveContent(summary)) {
            recordEvent(null, "SKIP_SENSITIVE", "AI_MEMORY", "候选记忆包含敏感内容或为空，已拒绝写入", conversationId);
            return;
        }
        if (candidate.confidence() < 0.72) {
            recordEvent(null, "SKIP_LOW_CONFIDENCE", "AI_MEMORY", "候选记忆置信度较低，已跳过", conversationId);
            return;
        }
        if (memoryMapper.countDuplicateMemory(scope.userId(), scope.userCode(), candidate.memoryType(), summary) > 0) {
            recordEvent(null, "SKIP_DUPLICATE", "AI_MEMORY", "候选记忆已存在，已跳过", conversationId);
            return;
        }
        AiMemoryGovernanceDecision decision = memoryGovernanceService.decide(candidate, userMessage, toolCalls, isLlmExtracted);
        String finalStatus = decision.status();
        List<Long> activeConflictIds = StringUtils.hasText(decision.conflictGroup())
                ? memoryMapper.selectActiveConflictMemoryIds(scope.userId(), scope.userCode(), decision.conflictGroup())
                : List.of();
        /*
         * 如果新候选不是非常明确的强偏好，而同一冲突组已有生效记忆，就先进入冲突态。
         * 这样可以防止“偶然一句话”或模型抽取偏差覆盖用户已经稳定使用的长期偏好。
         */
        if (!activeConflictIds.isEmpty()
                && !AiMemoryGovernanceService.STATUS_ACTIVE.equals(finalStatus)
                && !AiMemoryGovernanceService.STATUS_HALLUCINATION.equals(finalStatus)) {
            finalStatus = AiMemoryGovernanceService.STATUS_CONFLICTED;
        }
        Long id = idGenerator.nextId();
        String pointId = UUID.randomUUID().toString();
        boolean qdrantSaved = qdrantClient.upsert(pointId, summary, Map.of(
                "memoryId", id,
                "userId", scope.userId(),
                "userCode", scope.userCode(),
                "memoryType", candidate.memoryType(),
                "memoryScope", decision.memoryScope(),
                "scopeValue", decision.scopeValue(),
                "conflictGroup", decision.conflictGroup(),
                "status", finalStatus,
                "confidence", candidate.confidence(),
                "enabled", memoryGovernanceService.isRecallableStatus(finalStatus)
        ));
        memoryMapper.insertMemory(id, scope.userId(), scope.userCode(), candidate.memoryType(),
                decision.memoryKey(), decision.memoryScope(), decision.scopeValue(), decision.conflictGroup(),
                decision.priority(), decision.evidenceCount(), finalStatus, decision.policyJson(),
                safeMemory(candidate.memoryTitle()), summary, candidate.confidence(), qdrantSaved ? pointId : null,
                conversationId, traceContextSupport.currentOrNewTraceId());
        if (AiMemoryGovernanceService.STATUS_ACTIVE.equals(finalStatus) && StringUtils.hasText(decision.conflictGroup())) {
            int superseded = memoryMapper.supersedeConflictMemories(scope.userId(), scope.userCode(), decision.conflictGroup(), id);
            if (superseded > 0) {
                recordEvent(id, "SUPERSEDE_CONFLICT", "AI_MEMORY",
                        "新记忆替代同组旧偏好，conflictGroup=" + decision.conflictGroup() + "，count=" + superseded, conversationId);
            }
        }
        compileProfile(scope);
        String eventType = switch (finalStatus) {
            case AiMemoryGovernanceService.STATUS_ACTIVE -> qdrantSaved ? "CREATE_ACTIVE" : "CREATE_ACTIVE_WITHOUT_VECTOR";
            case AiMemoryGovernanceService.STATUS_CONFLICTED -> "CREATE_CONFLICTED";
            case AiMemoryGovernanceService.STATUS_HALLUCINATION -> "CREATE_SUSPECTED_HALLUCINATION";
            default -> "CREATE_CANDIDATE";
        };
        recordEvent(id, eventType, "AI_MEMORY",
                "AI 写入长期记忆候选：" + candidate.memoryType() + "，status=" + finalStatus + "，qdrantSaved=" + qdrantSaved, conversationId);
        auditLogService.recordMemory(conversationId, qdrantSaved ? "CREATE" : "CREATE_WITHOUT_VECTOR",
                "AI_MEMORY", String.valueOf(id), 0,
                "AI 写入长期记忆候选：" + candidate.memoryType() + "，status=" + finalStatus);
    }

    /**
     * 从一轮 AI 对话中提取长期记忆候选。
     * <p>
     * 优先级：LLM 提炼 > 关键词匹配降级。
     * LLM 可用时由模型判断对话是否包含偏好/习惯，从根源上解决"查什么存什么"的问题。
     * LLM 不可用时降级为关键词匹配，仅抓取用户明确表达偏好的语句，不再使用兜底规则。
     */
    private ExtractionResult extractCandidates(String userMessage, String assistantMessage, List<AiToolCall> toolCalls) {
        List<String> toolTargets = collectToolTargets(toolCalls);

        // 1. 优先使用 LLM 提炼记忆
        AiMemoryExtractor.ExtractionDecision decision = memoryExtractor.extract(userMessage, assistantMessage, toolTargets);
        if (decision.processed()) {
            if (decision.candidate() != null) {
                return ExtractionResult.of(List.of(decision.candidate()), true);
            }
            return ExtractionResult.empty(); // LLM 判断该对话不值得记忆，不存任何内容
        }

        // 2. LLM 不可用时降级为关键词匹配（仅保留高置信度规则）
        List<AiMemoryCandidate> keywordCandidates = keywordExtract(userMessage, assistantMessage, toolCalls);
        return ExtractionResult.of(keywordCandidates, false);
    }

    /**
     * 收集工具调用中的目标模块名，供 LLM 记忆提炼用。
     */
    private List<String> collectToolTargets(List<AiToolCall> toolCalls) {
        List<String> targets = new ArrayList<>();
        for (AiToolCall toolCall : toolCalls == null ? List.<AiToolCall>of() : toolCalls) {
            if (StringUtils.hasText(toolCall.target())) {
                targets.add(toolCall.target());
            }
        }
        return targets;
    }

    /**
     * 关键词匹配记忆提取 —— 仅当 LLM 不可用时的降级方案。
     * <p>
     * 只匹配用户明确表达偏好的语句（"以后都这样""我主要查"等），
     * 不再使用兜底规则把每条消息都当成 QUERY_HABIT 存储。
     */
    private List<AiMemoryCandidate> keywordExtract(String userMessage, String assistantMessage, List<AiToolCall> toolCalls) {
        List<AiMemoryCandidate> candidates = new ArrayList<>();
        String message = normalize(userMessage);
        if (!StringUtils.hasText(message)) {
            return candidates;
        }
        // 用户明确表达偏好意图
        if (containsAny(message, "以后", "以后都", "默认", "我希望", "我喜欢", "记住", "习惯")) {
            candidates.add(new AiMemoryCandidate("ANSWER_STYLE", "用户表达了回答或服务偏好", message, 0.88));
        }
        // 用户指定回答格式
        if (containsAny(message, "先给结论", "先说结论", "简短", "详细", "表格", "不要太长")) {
            candidates.add(new AiMemoryCandidate("ANSWER_STYLE", "用户回答格式偏好", message, 0.90));
        }
        // 用户主动表达持续业务关注
        if (containsAny(message, "我主要查", "我常看", "我关注", "我经常")) {
            candidates.add(new AiMemoryCandidate("QUERY_HABIT", "用户主动表达持续业务关注", message, 0.86));
        }
        // 工具调用过的模块
        for (AiToolCall toolCall : toolCalls == null ? List.<AiToolCall>of() : toolCalls) {
            if (StringUtils.hasText(toolCall.target())) {
                candidates.add(new AiMemoryCandidate("FAVORITE_MODULE", "用户常查模块：" + toolCall.target(),
                        "用户在 AI 助手中查询过 " + toolCall.target() + "，问题摘要：" + message, 0.74));
            }
        }
        // 不再使用兜底规则：没有命中任何关键词就意味着这只是一次普通的业务查询，不值得记录
        return candidates;
    }

    private List<AiMemoryItemVO> recallFromQdrant(UserScope scope, String keyword) {
        List<Long> ids = qdrantClient.search(scope.userId(), scope.userCode(), keyword, MAX_RECALL_COUNT);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<AiMemoryItemVO> memories = new ArrayList<>();
        for (Long id : ids) {
            AiMemoryItemVO memory = memoryMapper.selectMemoryById(id, scope.userId(), scope.userCode());
            if (memory != null) {
                memories.add(memory);
            }
        }
        return memories;
    }

    private List<AiMemoryItemVO> selectRecallableMemories(List<AiMemoryItemVO> memories) {
        List<AiMemoryItemVO> result = new ArrayList<>();
        LinkedHashSet<String> conflictGroups = new LinkedHashSet<>();
        for (AiMemoryItemVO memory : memories == null ? List.<AiMemoryItemVO>of() : memories) {
            if (memory == null || !memoryGovernanceService.isRecallableStatus(memory.status())) {
                continue;
            }
            String conflictGroup = StringUtils.hasText(memory.conflictGroup()) ? memory.conflictGroup() : "memory:" + memory.id();
            /*
             * 同一冲突组只取优先级最高的一条，避免“回答简短”和“回答详细”这类冲突偏好同时进入上下文。
             */
            if (conflictGroups.add(conflictGroup)) {
                result.add(memory);
            }
            if (result.size() >= MAX_RECALL_COUNT) {
                break;
            }
        }
        return result;
    }

    private void updateProfileHabits(AiMemoryCandidate candidate) {
        UserScope scope = currentScope();
        AiMemoryProfileVO profile = profile();
        LinkedHashSet<String> modules = splitSet(profile.favoriteModules());
        LinkedHashSet<String> habits = splitSet(profile.queryHabits());
        if ("FAVORITE_MODULE".equals(candidate.memoryType())) {
            modules.add(candidate.memoryTitle());
        } else if ("QUERY_HABIT".equals(candidate.memoryType())) {
            habits.add(candidate.memorySummary());
        }
        memoryMapper.updateProfileHabits(scope.userId(), scope.userCode(),
                joinLimited(modules), joinLimited(habits));
    }

    private void compileProfile(UserScope scope) {
        if (!governanceColumnsReady()) {
            return;
        }
        ensureProfile(scope);
        List<AiMemoryItemVO> activeMemories = memoryMapper.selectActiveMemoriesForProfile(scope.userId(), scope.userCode(), 200);
        AiUserProfileCompiler.ProfileSnapshot snapshot = profileCompiler.compile(activeMemories, DEFAULT_ANSWER_STYLE);
        memoryMapper.updateProfileCompiled(scope.userId(), scope.userCode(),
                snapshot.answerStyle(), snapshot.favoriteModules(), snapshot.queryHabits(),
                snapshot.answerStyleJson(), snapshot.queryStrategyJson(), snapshot.moduleAffinityJson(),
                snapshot.profileConfidence());
        recordEvent(null, "PROFILE_COMPILE", "AI_MEMORY",
                "AI 用户画像已按有效长期记忆重新编译，memoryCount=" + activeMemories.size());
    }

    private void ensureProfile(UserScope scope) {
        if (!tablesExist()) {
            return;
        }
        if (memoryMapper.selectProfile(scope.userId(), scope.userCode()) == null) {
            memoryMapper.insertDefaultProfile(idGenerator.nextId(), scope.userId(), scope.userCode(), DEFAULT_ANSWER_STYLE);
        }
    }

    private boolean tablesExist() {
        return columnChecker.hasColumn("ai_user_profile", "user_id")
                && columnChecker.hasColumn("ai_user_memory", "memory_summary")
                && columnChecker.hasColumn("ai_memory_event", "event_type");
    }

    private boolean governanceColumnsReady() {
        return tablesExist()
                && columnChecker.hasColumn("ai_user_memory", "memory_scope")
                && columnChecker.hasColumn("ai_user_memory", "conflict_group")
                && columnChecker.hasColumn("ai_user_profile", "profile_version");
    }

    private AiMemoryProfileVO defaultProfile(UserScope scope) {
        return new AiMemoryProfileVO(scope.userId(), scope.userCode(), true, DEFAULT_ANSWER_STYLE, "", "", 0L, null);
    }

    private List<AiMemoryItemVO> maskRecords(List<AiMemoryItemVO> records) {
        List<AiMemoryItemVO> masked = new ArrayList<>();
        for (AiMemoryItemVO record : records == null ? List.<AiMemoryItemVO>of() : records) {
            masked.add(new AiMemoryItemVO(record.id(), record.memoryType(), masker.mask(record.memoryTitle()),
                    masker.mask(record.memorySummary()), record.confidence(), record.qdrantPointId(),
                    record.createTime(), record.updateTime(), record.lastRecallTime(),
                    record.status(), record.memoryScope(), record.scopeValue(), record.conflictGroup(),
                    record.priority(), record.evidenceCount(), record.negativeCount(), record.supersededBy(),
                    record.lastAppliedAt(), masker.mask(record.policyJson())));
        }
        return masked;
    }

    private void recordEvent(Long memoryId, String eventType, String source, String summary) {
        recordEvent(memoryId, eventType, source, summary, null);
    }

    private void recordEvent(Long memoryId, String eventType, String source, String summary, String conversationId) {
        if (!tablesExist()) {
            return;
        }
        UserScope scope = currentScope();
        try {
            memoryMapper.insertMemoryEvent(idGenerator.nextId(), memoryId, eventType, source,
                    scope.userId(), scope.userCode(), traceContextSupport.currentOrNewTraceId(),
                    traceContextSupport.currentOrNewOperationId(), currentLoginSessionId(),
                    conversationId, safeMemory(summary));
        } catch (RuntimeException exception) {
            log.debug("AI 记忆事件写入失败，eventType={}, reason={}", eventType, exception.getMessage());
        }
    }

    private UserScope currentScope() {
        // 优先从 SSE 异步线程的 ThreadLocal 上下文读取登录标识
        String sseLoginId = SseChatContext.getLoginId();
        if (sseLoginId != null && !sseLoginId.isBlank() && !"null".equals(sseLoginId)) {
            return new UserScope(sseLoginId, nullToBlank(SseChatContext.getUserCode()));
        }
        Object loginId = StpUtil.getLoginIdDefaultNull();
        String userId = loginId == null ? "anonymous" : String.valueOf(loginId);
        String userCode = "";
        if (loginId != null) {
            userCode = String.valueOf(StpUtil.getSessionByLoginId(loginId).get("userCode", ""));
        }
        return new UserScope(userId, userCode);
    }

    private String currentLoginSessionId() {
        String sseLoginId = SseChatContext.getLoginId();
        if (sseLoginId != null && !sseLoginId.isBlank() && !"null".equals(sseLoginId)) {
            return nullToBlank(SseChatContext.getLoginSessionId());
        }
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId == null ? "" : String.valueOf(StpUtil.getSessionByLoginId(loginId).get(TraceContextSupport.LOGIN_SESSION_ID, ""));
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String getSessionValue(String loginId, String key) {
        try {
            return String.valueOf(StpUtil.getSessionByLoginId(loginId).get(key, ""));
        } catch (RuntimeException e) {
            log.debug("读取会话属性失败, loginId={}, key={}", loginId, key, e);
            return "";
        }
    }

    /**
     * 记忆内容安全处理 —— 仅对手机号/邮箱/身份证/凭证做精确正则掩码。
     * <p>
     * LLM 已按要求输出脱敏后的语义摘要，本方法仅做补充保护。
     * 不再使用 LogMaskUtils.maskText()（随机星号，会破坏语义内容）。
     */
    private String safeMemory(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String masked = masker.mask(value);
        return masked.length() <= MAX_SUMMARY_LENGTH ? masked : masked.substring(0, MAX_SUMMARY_LENGTH);
    }

    private boolean containsSensitiveContent(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return containsAny(lower, "password", "token", "api_key", "apikey", "secret", "authorization", "bearer", "drop table", "update set")
                || value.matches(".*1[3-9]\\d{9}.*")
                || value.matches(".*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*");
    }

    private boolean containsAny(String value, String... words) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (String word : words) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private LinkedHashSet<String> splitSet(String value) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (!StringUtils.hasText(value)) {
            return result;
        }
        for (String item : value.split("\\n")) {
            if (StringUtils.hasText(item)) {
                result.add(item.trim());
            }
        }
        return result;
    }

    private String joinLimited(LinkedHashSet<String> values) {
        return String.join("\n", values.stream().limit(20).toList());
    }

    /**
     * 触发一轮记忆生命周期维护（供定时任务或管理接口调用）。
     * <p>
     * 对用户完全无感：自动升级候选记忆、恢复疑似幻觉、衰减过期记忆、清理归档记忆。
     */
    public AiMemoryLifecycleManager.MaintenanceResult runMaintenance() {
        if (!tablesExist()) {
            return new AiMemoryLifecycleManager.MaintenanceResult(0, 0, 0, 0, 0, "tables not ready");
        }
        return lifecycleManager.executeMaintenance();
    }

    /**
     * 提取结果：携带来源标记（LLM 提炼 vs 关键词降级），供治理判定参考。
     */
    private record ExtractionResult(List<AiMemoryCandidate> candidates, boolean isLlmExtracted) {
        static ExtractionResult of(List<AiMemoryCandidate> candidates, boolean isLlmExtracted) {
            return new ExtractionResult(candidates, isLlmExtracted);
        }
        static ExtractionResult empty() {
            return new ExtractionResult(List.of(), false);
        }
    }

    private record UserScope(String userId, String userCode) {
    }
}
