package jimmy.ai.service;

import jimmy.ai.entity.AiMessageFeedback;
import jimmy.ai.mapper.AiMessageFeedbackMapper;
import jimmy.ai.model.AiChatRequest;
import jimmy.ai.model.AiChatResponse;
import jimmy.ai.model.AiConversationVO;
import jimmy.ai.model.AiLogAnalysisResponse;
import jimmy.ai.model.AiLogAnalyzeRequest;
import jimmy.ai.model.FeedbackRequest;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.common.model.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.OutputStream;

import java.util.List;

/**
 * AI 助手门面 —— 会话管理、日志排障和反馈记录。
 * <p>
 * 问答编排（chat/chatStream）委托给 {@link AiChatPipeline}。
 */
@Slf4j
@Service
public class AiAssistantService {

    private final AiChatPipeline chatPipeline;
    private final AiLogAnalysisService logAnalysisService;
    private final AiConversationService conversationService;
    private final AiAuditLogService auditLogService;
    private final AiMessageFeedbackMapper feedbackMapper;
    private final CompactSnowflakeIdGenerator idGenerator;
    private final UserContextResolver userContext;

    public AiAssistantService(AiChatPipeline chatPipeline,
                              AiLogAnalysisService logAnalysisService,
                              AiConversationService conversationService,
                              AiAuditLogService auditLogService,
                              AiMessageFeedbackMapper feedbackMapper,
                              CompactSnowflakeIdGenerator idGenerator,
                              UserContextResolver userContext) {
        this.chatPipeline = chatPipeline;
        this.logAnalysisService = logAnalysisService;
        this.conversationService = conversationService;
        this.auditLogService = auditLogService;
        this.feedbackMapper = feedbackMapper;
        this.idGenerator = idGenerator;
        this.userContext = userContext;
    }

    // ── 问答委托 ──

    public AiChatResponse chat(AiChatRequest request) {
        return chatPipeline.chat(request);
    }

    public void chatStream(AiChatRequest request, OutputStream outputStream, String loginId, List<String> permissions,
                           String roleCode, String customerId, String username, String userCode, String loginSessionId) {
        chatPipeline.chatStream(request, outputStream, loginId, permissions, roleCode, customerId, username, userCode, loginSessionId);
    }

    // ── 日志排障 ──

    public AiLogAnalysisResponse analyzeLogs(AiLogAnalyzeRequest request) {
        AiLogAnalysisResponse response = logAnalysisService.analyze(request);
        log.info("AI助手日志排障完成，timelineCount={}, riskCount={}", response.timeline().size(), response.riskPoints().size());
        return response;
    }

    // ── 会话管理 ──

    public PageResult<AiConversationVO> conversations(String status, String keyword, int page, int pageSize) {
        return conversationService.page(userContext.currentUserId(), userContext.currentUserCode(), status, keyword, page, pageSize);
    }

    public AiConversationVO conversation(String conversationId) {
        return conversationService.find(userContext.currentUserId(), userContext.currentUserCode(), conversationId);
    }

    public void archiveConversation(String conversationId) {
        conversationService.archive(userContext.currentUserId(), userContext.currentUserCode(), conversationId);
        auditLogService.recordToolCall(conversationId, "会话管理", "归档会话", conversationId, "用户归档 AI 会话");
    }

    public void restoreConversation(String conversationId) {
        conversationService.restore(userContext.currentUserId(), userContext.currentUserCode(), conversationId);
        auditLogService.recordToolCall(conversationId, "会话管理", "恢复会话", conversationId, "用户恢复 AI 会话");
    }

    public void deleteConversation(String conversationId) {
        conversationService.delete(userContext.currentUserId(), userContext.currentUserCode(), conversationId);
        auditLogService.recordToolCall(conversationId, "会话管理", "删除会话", conversationId, "用户删除 AI 会话");
    }

    public void clearConversations(String status) {
        conversationService.clear(userContext.currentUserId(), userContext.currentUserCode(), status);
        auditLogService.recordToolCall("-", "会话管理", "清空会话", "清空当前账号 AI 会话", "用户清空 AI 会话，范围=" + status);
    }

    // ── 用户反馈 ──

    public void recordFeedback(FeedbackRequest request) {
        if (request == null || !org.springframework.util.StringUtils.hasText(request.messageId())
                || !org.springframework.util.StringUtils.hasText(request.conversationId())
                || !org.springframework.util.StringUtils.hasText(request.rating())) {
            log.debug("AI 反馈数据不完整，已跳过，messageId={}", request != null ? request.messageId() : null);
            return;
        }
        try {
            AiMessageFeedback feedback = new AiMessageFeedback();
            feedback.setId(idGenerator.nextId());
            feedback.setMessageId(request.messageId());
            feedback.setConversationId(request.conversationId());
            feedback.setUserId(userContext.currentUserId());
            feedback.setRating(request.rating());
            feedback.setComment(request.comment());
            feedback.setCreatedAt(java.time.LocalDateTime.now());
            feedbackMapper.insert(feedback);
            log.info("AI 反馈已记录，messageId={}, rating={}", request.messageId(), request.rating());
            auditLogService.recordToolCall(request.conversationId(), "用户反馈", request.rating(),
                    request.messageId(), "用户对 AI 回答提交了反馈");
        } catch (RuntimeException exception) {
            log.debug("AI 反馈记录失败，messageId={}, reason={}", request.messageId(), exception.getMessage());
        }
    }
}
