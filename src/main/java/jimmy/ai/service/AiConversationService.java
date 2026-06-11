package jimmy.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jimmy.ai.mapper.AiConversationMapper;
import jimmy.ai.model.AiCitation;
import jimmy.ai.model.AiConversationVO;
import jimmy.ai.model.AiMessageVO;
import jimmy.ai.model.AiToolCall;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.common.model.PageResult;
import jimmy.common.trace.TraceContextSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

/**
 * AI 会话服务。
 * <p>
 * MySQL 保存完整历史，Redis 只缓存最近上下文。这样服务重启后历史会话不会丢失，
 * 同时 AI 连续追问仍能快速拿到最近几轮上下文。
 */
@Slf4j
@Service
public class AiConversationService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int CONTEXT_MESSAGE_LIMIT = 20;
    private static final int DETAIL_MESSAGE_LIMIT = 200;

    private final AiConversationMapper conversationMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TraceContextSupport traceContextSupport;
    private final CompactSnowflakeIdGenerator idGenerator;
    private final AiSensitiveDataMasker masker;
    private final Duration ttl;

    public AiConversationService(AiConversationMapper conversationMapper,
                                 StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper,
                                 TraceContextSupport traceContextSupport,
                                 CompactSnowflakeIdGenerator idGenerator,
                                 AiSensitiveDataMasker masker,
                                 @Value("${app.ai.conversation-ttl-seconds:3600}") long ttlSeconds) {
        this.conversationMapper = conversationMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.traceContextSupport = traceContextSupport;
        this.idGenerator = idGenerator;
        this.masker = masker;
        this.ttl = Duration.ofSeconds(Math.max(300, ttlSeconds));
    }

    /**
     * 启动后尽力迁移 Redis 中尚未落库的旧会话。
     * <p>
     * 旧版会话没有 userCode，只能按 Redis key 中的 userId 迁移；迁移失败不会影响主应用启动。
     */
    @PostConstruct
    public void migrateRedisConversations() {
        try {
            Set<String> keys = redisTemplate.keys("ai:conversation:*:*");
            if (keys == null || keys.isEmpty()) {
                return;
            }
            int migrated = 0;
            for (String key : keys) {
                AiConversationVO conversation = readCache(key);
                String[] parts = key.split(":");
                if (conversation == null || parts.length < 4) {
                    continue;
                }
                String userId = parts[2];
                upsertConversation(userId, "", conversation.conversationId(), conversation.title(), conversation.contextSnapshot());
                for (AiMessageVO message : conversation.messages() == null ? Collections.<AiMessageVO>emptyList() : conversation.messages()) {
                    insertMessage(userId, "", conversation.conversationId(), message.role(), message.content(),
                            message.status(), null, null, message.traceId(), message.operationId(), message.loginSessionId());
                }
                migrated++;
            }
            if (migrated > 0) {
                log.info("AI Redis 旧会话迁移完成，count={}", migrated);
            }
        } catch (RuntimeException exception) {
            log.debug("AI Redis 旧会话迁移跳过，reason={}", exception.getMessage());
        }
    }

    public PageResult<AiConversationVO> page(String userId, String userCode, String status, String keyword, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 50);
        String safeStatus = normalizeStatus(status);
        String safeKeyword = normalize(keyword);
        long total = conversationMapper.countConversations(userId, userCode, safeStatus, safeKeyword);
        List<AiConversationVO> records = conversationMapper.selectConversations(
                userId, userCode, safeStatus, safeKeyword, (long) (safePage - 1) * safePageSize, safePageSize);
        return new PageResult<>(records, safePage, safePageSize, total);
    }

    public AiConversationVO find(String userId, String userCode, String conversationId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(conversationId)) {
            return null;
        }
        AiConversationVO conversation = conversationMapper.selectConversation(userId, userCode, conversationId);
        if (conversation == null) {
            return null;
        }
        List<AiMessageVO> messages = conversationMapper.selectMessages(conversationId, userId, userCode, DETAIL_MESSAGE_LIMIT);
        AiConversationVO detail = withMessages(conversation, messages);
        writeCache(userId, detail);
        return detail;
    }

    public AiConversationVO appendUserMessage(String userId, String userCode, String conversationId, String userMessage) {
        String resolvedConversationId = resolveConversationId(conversationId);
        String safeMessage = masker.mask(userMessage);
        upsertConversation(userId, userCode, resolvedConversationId, title(safeMessage), null);
        insertMessage(userId, userCode, resolvedConversationId, "user", safeMessage, "SUCCESS", null, null,
                traceContextSupport.currentOrNewTraceId(), traceContextSupport.currentOrNewOperationId(), currentLoginSessionId());
        AiConversationVO conversation = refreshSummary(userId, userCode, resolvedConversationId, contextSnapshot(safeMessage, null, null));
        writeCache(userId, conversation);
        return conversation;
    }

    public AiConversationVO appendAssistantMessage(String userId,
                                                   String userCode,
                                                   String conversationId,
                                                   String assistantMessage,
                                                   List<AiToolCall> toolCalls,
                                                   List<AiCitation> citations,
                                                   String previousUserMessage) {
        String safeAnswer = masker.mask(assistantMessage);
        insertMessage(userId, userCode, conversationId, "assistant", safeAnswer, "SUCCESS",
                toolSummary(toolCalls), citationSummary(citations),
                traceContextSupport.currentOrNewTraceId(), traceContextSupport.currentOrNewOperationId(), currentLoginSessionId());
        AiConversationVO conversation = refreshSummary(userId, userCode, conversationId,
                contextSnapshot(previousUserMessage, toolCalls, safeAnswer));
        writeCache(userId, conversation);
        return conversation;
    }

    public AiConversationVO appendFailedAssistantMessage(String userId,
                                                         String userCode,
                                                         String conversationId,
                                                         String errorMessage,
                                                         String previousUserMessage) {
        String safeError = masker.mask(errorMessage);
        insertMessage(userId, userCode, conversationId, "assistant", safeError, "FAILED", null, null,
                traceContextSupport.currentOrNewTraceId(), traceContextSupport.currentOrNewOperationId(), currentLoginSessionId());
        AiConversationVO conversation = refreshSummary(userId, userCode, conversationId,
                contextSnapshot(previousUserMessage, null, safeError));
        writeCache(userId, conversation);
        return conversation;
    }

    public AiConversationVO append(String userId, String conversationId, String userMessage, String assistantMessage) {
        AiConversationVO conversation = appendUserMessage(userId, "", conversationId, userMessage);
        return appendAssistantMessage(userId, "", conversation.conversationId(), assistantMessage, List.of(), List.of(), userMessage);
    }

    public List<AiConversationVO> list(String userId) {
        return page(userId, "", "ACTIVE", null, 1, 50).records();
    }

    public AiConversationVO find(String userId, String conversationId) {
        return find(userId, "", conversationId);
    }

    public void archive(String userId, String userCode, String conversationId) {
        conversationMapper.archiveConversation(conversationId, userId, userCode);
        redisTemplate.delete(key(userId, conversationId));
    }

    public void restore(String userId, String userCode, String conversationId) {
        conversationMapper.restoreConversation(conversationId, userId, userCode);
        redisTemplate.delete(key(userId, conversationId));
    }

    public void delete(String userId, String userCode, String conversationId) {
        conversationMapper.deleteConversation(conversationId, userId, userCode);
        redisTemplate.delete(key(userId, conversationId));
    }

    public void clear(String userId, String userCode, String status) {
        conversationMapper.clearConversations(userId, userCode, normalizeStatus(status));
        Set<String> keys = redisTemplate.keys(key(userId, "*"));
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private void upsertConversation(String userId, String userCode, String conversationId, String title, String contextSnapshot) {
        conversationMapper.upsertConversation(idGenerator.nextId(), conversationId, userId, userCode,
                StringUtils.hasText(title) ? title : "新会话", truncate(contextSnapshot, 1000));
    }

    private void insertMessage(String userId,
                               String userCode,
                               String conversationId,
                               String role,
                               String content,
                               String status,
                               String toolSummary,
                               String citationSummary,
                               String traceId,
                               String operationId,
                               String loginSessionId) {
        conversationMapper.insertMessage(idGenerator.nextId(), String.valueOf(idGenerator.nextId()), conversationId,
                userId, userCode, role, truncate(content, 4000), status, traceId, operationId, loginSessionId,
                truncate(toolSummary, 1000), truncate(citationSummary, 1000));
    }

    private AiConversationVO refreshSummary(String userId, String userCode, String conversationId, String contextSnapshot) {
        conversationMapper.updateConversationSummary(conversationId, userId, userCode, truncate(contextSnapshot, 1000));
        return find(userId, userCode, conversationId);
    }

    private AiConversationVO withMessages(AiConversationVO conversation, List<AiMessageVO> messages) {
        return new AiConversationVO(conversation.conversationId(), conversation.title(), conversation.status(),
                conversation.createdAt(), conversation.updatedAt(), conversation.archivedAt(),
                conversation.messageCount(), conversation.contextSnapshot(), messages);
    }

    private void writeCache(String userId, AiConversationVO conversation) {
        try {
            List<AiMessageVO> messages = conversation.messages() == null ? List.of() : conversation.messages();
            if (messages.size() > CONTEXT_MESSAGE_LIMIT) {
                messages = new ArrayList<>(messages.subList(messages.size() - CONTEXT_MESSAGE_LIMIT, messages.size()));
            }
            AiConversationVO cached = withMessages(conversation, messages);
            redisTemplate.opsForValue().set(key(userId, conversation.conversationId()), objectMapper.writeValueAsString(cached), ttl);
        } catch (JsonProcessingException exception) {
            log.debug("AI 会话缓存写入失败，conversationId={}, reason={}", conversation.conversationId(), exception.getMessage());
        }
    }

    private AiConversationVO readCache(String key) {
        String json = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AiConversationVO.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String resolveConversationId(String conversationId) {
        return StringUtils.hasText(conversationId) ? conversationId : traceContextSupport.newOperationId();
    }

    private String key(String userId, String conversationId) {
        return "ai:conversation:" + userId + ":" + conversationId;
    }

    private String title(String message) {
        if (!StringUtils.hasText(message)) {
            return "新会话";
        }
        String trimmed = message.trim();
        return trimmed.length() <= 24 ? trimmed : trimmed.substring(0, 24);
    }

    private String contextSnapshot(String userMessage, List<AiToolCall> toolCalls, String assistantMessage) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(userMessage)) {
            builder.append("最近用户问题：").append(userMessage.trim()).append("\n");
        }
        if (toolCalls != null && !toolCalls.isEmpty()) {
            StringJoiner joiner = new StringJoiner("；");
            for (AiToolCall toolCall : toolCalls) {
                joiner.add(toolCall.toolName() + "/" + toolCall.target());
            }
            builder.append("最近工具目标：").append(joiner).append("\n");
        }
        if (StringUtils.hasText(assistantMessage)) {
            builder.append("最近回答摘要：").append(truncate(assistantMessage, 300));
        }
        return masker.mask(builder.toString());
    }

    private String toolSummary(List<AiToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner("；");
        for (AiToolCall toolCall : toolCalls) {
            joiner.add(toolCall.toolName() + "-" + toolCall.target() + "-" + toolCall.result());
        }
        return masker.mask(joiner.toString());
    }

    private String citationSummary(List<AiCitation> citations) {
        if (citations == null || citations.isEmpty()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner("；");
        for (AiCitation citation : citations) {
            joiner.add(citation.title() + "-" + citation.reference());
        }
        return masker.mask(joiner.toString());
    }

    private String currentLoginSessionId() {
        return traceContextSupport.current(TraceContextSupport.LOGIN_SESSION_ID);
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "ACTIVE";
        }
        String upper = status.trim().toUpperCase();
        return "ARCHIVED".equals(upper) ? "ARCHIVED" : "ACTIVE";
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? masker.mask(value.trim()) : null;
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
