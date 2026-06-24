package jimmy.ai.config;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.model.AiChatRequest;
import jimmy.ai.model.AiMessageVO;
import jimmy.ai.service.AiConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 前端临时会话历史兜底落库。
 * <p>
 * 场景：SSE 流式请求可能因为页面切换、网络中断或 Python 代理异常而没有完整落库，
 * 但前端页面本地仍保留了最近几轮消息。当前 Advice 在 Controller 入参反序列化后、
 * 进入 {@code AiChatPipeline} 前，把前端携带的 clientHistory 尽力补写到 MySQL。
 * <p>
 * 这样后续 {@code AiChatPipeline#conversationHistory} 从 MySQL 构造短期上下文时，
 * 即使上一轮 SSE 未正常 done，也能让“没了？”“继续”“那这个呢？”这类追问接上前文。
 */
@Slf4j
@Component
@ControllerAdvice
public class AiClientHistoryRequestBodyAdvice extends RequestBodyAdviceAdapter {

    private static final int MAX_HISTORY_ITEMS = 8;
    private static final int MAX_CONTENT_LENGTH = 1000;

    private final AiConversationService conversationService;

    public AiClientHistoryRequestBodyAdvice(AiConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return AiChatRequest.class.isAssignableFrom(methodParameter.getParameterType());
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (body instanceof AiChatRequest request) {
            persistClientHistory(request);
        }
        return body;
    }

    private void persistClientHistory(AiChatRequest request) {
        if (request == null
                || !StringUtils.hasText(request.conversationId())
                || request.clientHistory() == null
                || request.clientHistory().isEmpty()) {
            return;
        }

        String userId = currentUserId();
        String userCode = currentUserCode();
        String conversationId = request.conversationId();
        String currentMessage = normalize(request.message());

        try {
            Set<String> existing = existingMessageKeys(conversationId, userId, userCode);
            List<AiChatRequest.ClientHistoryMessage> history = request.clientHistory();
            int start = Math.max(0, history.size() - MAX_HISTORY_ITEMS);

            for (int i = start; i < history.size(); i++) {
                AiChatRequest.ClientHistoryMessage item = history.get(i);
                String role = normalizeRole(item == null ? null : item.role());
                String content = normalize(item == null ? null : item.content());
                if (!StringUtils.hasText(role) || !StringUtils.hasText(content)) {
                    continue;
                }
                // 当前问题会由 AiChatPipeline 正常 appendUserMessage，这里只补历史，避免重复入库。
                if ("user".equals(role) && content.equals(currentMessage)) {
                    continue;
                }
                String key = role + "\u0001" + content;
                if (existing.contains(key)) {
                    continue;
                }
                if ("user".equals(role)) {
                    conversationService.appendUserMessage(userId, userCode, conversationId, content);
                } else if ("assistant".equals(role)) {
                    conversationService.appendAssistantMessage(userId, userCode, conversationId, content,
                            List.of(), List.of(), "");
                }
                existing.add(key);
            }
        } catch (RuntimeException exception) {
            // clientHistory 是兜底能力，失败不能影响正常 AI 请求。
            log.warn("AI 前端临时历史兜底落库失败，conversationId={}, reason={}",
                    conversationId, exception.getMessage());
        }
    }

    private Set<String> existingMessageKeys(String conversationId, String userId, String userCode) {
        Set<String> keys = new HashSet<>();
        try {
            List<AiMessageVO> messages = conversationService.recentMessages(conversationId, userId, userCode, 10);
            for (AiMessageVO message : messages == null ? List.<AiMessageVO>of() : messages) {
                String role = normalizeRole(message.role());
                String content = normalize(message.content());
                if (StringUtils.hasText(role) && StringUtils.hasText(content)) {
                    keys.add(role + "\u0001" + content);
                }
            }
        } catch (RuntimeException exception) {
            log.debug("AI 查询已有会话历史失败，conversationId={}, reason={}", conversationId, exception.getMessage());
        }
        return keys;
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "";
        }
        String normalized = role.trim().toLowerCase();
        return "user".equals(normalized) || "assistant".equals(normalized) ? normalized : "";
    }

    private String normalize(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.trim();
        return normalized.length() > MAX_CONTENT_LENGTH ? normalized.substring(0, MAX_CONTENT_LENGTH) : normalized;
    }

    private String currentUserId() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId == null ? "anonymous" : String.valueOf(loginId);
    }

    private String currentUserCode() {
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            return loginId == null ? "" : String.valueOf(StpUtil.getSessionByLoginId(loginId).get("userCode", ""));
        } catch (RuntimeException exception) {
            return "";
        }
    }
}
