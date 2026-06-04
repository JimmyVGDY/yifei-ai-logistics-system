package jimmy.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jimmy.ai.model.AiConversationVO;
import jimmy.ai.model.AiMessageVO;
import jimmy.config.TraceContextSupport;
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

/**
 * AI 会话缓存服务 —— 使用 Redis 保存短期上下文，不新增数据库表。
 */
@Service
public class AiConversationService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TraceContextSupport traceContextSupport;
    private final Duration ttl;

    public AiConversationService(StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper,
                                 TraceContextSupport traceContextSupport,
                                 @Value("${app.ai.conversation-ttl-seconds:3600}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.traceContextSupport = traceContextSupport;
        this.ttl = Duration.ofSeconds(Math.max(300, ttlSeconds));
    }

    public AiConversationVO append(String userId, String conversationId, String userMessage, String assistantMessage) {
        String resolvedConversationId = StringUtils.hasText(conversationId) ? conversationId : traceContextSupport.newOperationId();
        AiConversationVO current = find(userId, resolvedConversationId);
        String now = LocalDateTime.now().format(FORMATTER);
        List<AiMessageVO> messages = new ArrayList<>(current == null ? Collections.emptyList() : current.messages());
        messages.add(new AiMessageVO("user", userMessage, now));
        messages.add(new AiMessageVO("assistant", assistantMessage, now));
        if (messages.size() > 20) {
            messages = new ArrayList<>(messages.subList(messages.size() - 20, messages.size()));
        }
        String title = current == null ? title(userMessage) : current.title();
        String createdAt = current == null ? now : current.createdAt();
        AiConversationVO saved = new AiConversationVO(resolvedConversationId, title, createdAt, now, messages);
        write(userId, saved);
        return saved;
    }

    public List<AiConversationVO> list(String userId) {
        String pattern = key(userId, "*");
        List<AiConversationVO> result = new ArrayList<>();
        var keys = redisTemplate.keys(pattern);
        if (keys == null) {
            return result;
        }
        for (String key : keys) {
            AiConversationVO conversation = read(key);
            if (conversation != null) {
                result.add(conversation);
            }
        }
        result.sort((left, right) -> right.updatedAt().compareTo(left.updatedAt()));
        return result;
    }

    public AiConversationVO find(String userId, String conversationId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(conversationId)) {
            return null;
        }
        return read(key(userId, conversationId));
    }

    private void write(String userId, AiConversationVO conversation) {
        try {
            redisTemplate.opsForValue().set(key(userId, conversation.conversationId()), objectMapper.writeValueAsString(conversation), ttl);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI 会话缓存写入失败", exception);
        }
    }

    private AiConversationVO read(String key) {
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
}
