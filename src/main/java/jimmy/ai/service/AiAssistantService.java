package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.model.AiChatRequest;
import jimmy.ai.model.AiChatResponse;
import jimmy.ai.model.AiCitation;
import jimmy.ai.model.AiConversationVO;
import jimmy.ai.model.AiDataResult;
import jimmy.ai.model.AiLogAnalysisResponse;
import jimmy.ai.model.AiLogAnalyzeRequest;
import jimmy.ai.model.AiMessageVO;
import jimmy.ai.model.AiMemoryRecallResult;
import jimmy.ai.model.AiToolCall;
import jimmy.ai.entity.AiMessageFeedback;
import jimmy.ai.mapper.AiMessageFeedbackMapper;
import jimmy.ai.model.FeedbackRequest;
import jimmy.ai.util.SseChatContext;
import jimmy.ai.model.AiReadonlyQueryResult;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.common.model.PageResult;
import jimmy.common.trace.TraceContextSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.OutputStream;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AI 助手主服务：编排知识检索、只读业务查询、日志排障、模型调用和会话缓存。
 */
@Slf4j
@Service
public class AiAssistantService {

    private final AiKnowledgeService knowledgeService;
    private final AiReadonlyQueryService readonlyQueryService;
    private final AiLogAnalysisService logAnalysisService;
    private final AiModelGateway modelGateway;
    private final AiConversationService conversationService;
    private final AiSensitiveDataMasker masker;
    private final TraceContextSupport traceContextSupport;
    private final AiAuditLogService auditLogService;
    private final AiBusinessQueryTools businessQueryTools;
    private final AiToolCallContext toolCallContext;
    private final AiMemoryService memoryService;
    private final AiFallbackHandler fallbackHandler;
    private final AiMessageFeedbackMapper feedbackMapper;
    private final CompactSnowflakeIdGenerator idGenerator;
    private final AiChatPipeline chatPipeline;

    public AiAssistantService(AiKnowledgeService knowledgeService,
                              AiReadonlyQueryService readonlyQueryService,
                              AiLogAnalysisService logAnalysisService,
                              AiModelGateway modelGateway,
                              AiConversationService conversationService,
                              AiSensitiveDataMasker masker,
                              TraceContextSupport traceContextSupport,
                              AiAuditLogService auditLogService,
                              AiBusinessQueryTools businessQueryTools,
                              AiToolCallContext toolCallContext,
                              AiMemoryService memoryService,
                              AiFallbackHandler fallbackHandler,
                              AiMessageFeedbackMapper feedbackMapper,
                              CompactSnowflakeIdGenerator idGenerator,
                              AiChatPipeline chatPipeline) {
        this.knowledgeService = knowledgeService;
        this.readonlyQueryService = readonlyQueryService;
        this.logAnalysisService = logAnalysisService;
        this.modelGateway = modelGateway;
        this.conversationService = conversationService;
        this.masker = masker;
        this.traceContextSupport = traceContextSupport;
        this.auditLogService = auditLogService;
        this.businessQueryTools = businessQueryTools;
        this.toolCallContext = toolCallContext;
        this.memoryService = memoryService;
        this.fallbackHandler = fallbackHandler;
        this.feedbackMapper = feedbackMapper;
        this.idGenerator = idGenerator;
        this.chatPipeline = chatPipeline;
    }

    public AiChatResponse chat(AiChatRequest request) {
        return chatPipeline.chat(request);
    }

    /**
     * SSE 流式对话：委托给 {@link AiChatPipeline#chatStream} 统一处理。
     */
    public void chatStream(AiChatRequest request, OutputStream outputStream, String loginId, List<String> permissions,
                           String roleCode, String customerId, String username, String userCode, String loginSessionId) {
        chatPipeline.chatStream(request, outputStream, loginId, permissions, roleCode, customerId, username, userCode, loginSessionId);
    }

    private String currentBusinessDate() {
        return java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))
                .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
    }

    public AiLogAnalysisResponse analyzeLogs(AiLogAnalyzeRequest request) {
        AiLogAnalysisResponse response = logAnalysisService.analyze(request);
        log.info("AI助手日志排障完成，timelineCount={}, riskCount={}", response.timeline().size(), response.riskPoints().size());
        return response;
    }

    public PageResult<AiConversationVO> conversations(String status, String keyword, int page, int pageSize) {
        return conversationService.page(currentUserId(), currentUserCode(), status, keyword, page, pageSize);
    }

    public AiConversationVO conversation(String conversationId) {
        return conversationService.find(currentUserId(), currentUserCode(), conversationId);
    }

    public void archiveConversation(String conversationId) {
        conversationService.archive(currentUserId(), currentUserCode(), conversationId);
        auditLogService.recordToolCall(conversationId, "会话管理", "归档会话", conversationId, "用户归档 AI 会话");
    }

    public void restoreConversation(String conversationId) {
        conversationService.restore(currentUserId(), currentUserCode(), conversationId);
        auditLogService.recordToolCall(conversationId, "会话管理", "恢复会话", conversationId, "用户恢复 AI 会话");
    }

    public void deleteConversation(String conversationId) {
        conversationService.delete(currentUserId(), currentUserCode(), conversationId);
        auditLogService.recordToolCall(conversationId, "会话管理", "删除会话", conversationId, "用户删除 AI 会话");
    }

    public void clearConversations(String status) {
        conversationService.clear(currentUserId(), currentUserCode(), status);
        auditLogService.recordToolCall("-", "会话管理", "清空会话", "清空当前账号 AI 会话", "用户清空 AI 会话，范围=" + status);
    }

    private AiConversationVO saveConversation(String conversationId,
                                              String safeMessage,
                                              String answer,
                                              List<AiToolCall> toolCalls,
                                              List<AiCitation> citations) {
        try {
            return conversationService.appendAssistantMessage(currentUserId(), currentUserCode(), conversationId,
                    answer, toolCalls, citations, safeMessage);
        } catch (RuntimeException exception) {
            log.warn("AI 会话持久化失败，已使用临时会话兜底，reason={}", exception.getMessage());
            String id = StringUtils.hasText(conversationId) ? conversationId : traceContextSupport.newOperationId();
            return new AiConversationVO(id, "临时会话", "", "", List.of());
        }
    }

    private String resolveConversationId(String conversationId) {
        return StringUtils.hasText(conversationId) ? conversationId : traceContextSupport.newOperationId();
    }

    private String contextText(List<AiCitation> citations) {
        StringBuilder builder = new StringBuilder();
        for (AiCitation citation : citations) {
            builder.append("[").append(citation.title()).append("] ").append(citation.snippet()).append("\n");
        }
        return builder.toString();
    }

    private boolean looksLikeLogQuestion(String message) {
        String lower = message == null ? "" : message.toLowerCase();
        return lower.contains("traceid") || lower.contains("operationid") || lower.contains("loginsessionid")
                || lower.contains("日志") || lower.contains("报错");
    }

    private String extractToken(String message, String name) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String lower = message.toLowerCase();
        String marker = name.toLowerCase();
        int index = lower.indexOf(marker);
        if (index < 0) {
            return null;
        }
        String tail = message.substring(index + name.length()).replaceFirst("^[\\s:=：]+", "");
        String[] parts = tail.split("[\\s,，;；]+");
        return parts.length == 0 || !StringUtils.hasText(parts[0]) ? null : parts[0].trim();
    }

    private String currentUserId() {
        // 优先从 SSE 异步线程读取 Controller 传递的登录标识
        String sseLoginId = SseChatContext.getLoginId();
        if (sseLoginId != null && !sseLoginId.isBlank() && !"null".equals(sseLoginId)) {
            return sseLoginId;
        }
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId == null ? "anonymous" : String.valueOf(loginId);
    }

    private String currentUserCode() {
        String sseUserCode = SseChatContext.getUserCode();
        if (StringUtils.hasText(sseUserCode) && !"null".equalsIgnoreCase(sseUserCode)) {
            return sseUserCode;
        }
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            return loginId == null ? "" : String.valueOf(StpUtil.getSessionByLoginId(loginId).get("userCode", ""));
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private AiConversationVO currentConversation(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return null;
        }
        try {
            return conversationService.find(currentUserId(), currentUserCode(), conversationId);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    String latestUserMessage(AiConversationVO conversation) {
        if (conversation == null || conversation.messages() == null) {
            return null;
        }
        List<AiMessageVO> messages = conversation.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            AiMessageVO message = messages.get(i);
            if ("user".equals(message.role())
                    && StringUtils.hasText(message.content())
                    && !isContinuationRequest(message.content())) {
                return message.content();
            }
        }
        return null;
    }

    /**
     * 连续追问时跳过“剩余、下一批、继续看”等翻页式表达，回溯到最近一次真实查询。
     * <p>
     * 例如：
     * 用户先问“查看今天订单明细”，再问“查看剩余28条”，再问“下一批”。
     * 第三轮必须继承第一轮查询，而不是继承第二轮的“剩余28条”。
     */
    private boolean isContinuationRequest(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return text.contains("剩余")
                || text.contains("余下")
                || text.contains("剩下")
                || text.contains("后面的")
                || text.contains("后续")
                || text.contains("更多")
                || text.contains("继续看")
                || text.contains("接着看")
                || text.contains("下一批")
                || text.contains("下一页")
                || text.contains("查看更多");
    }

    /**
     * 记录用户对 AI 回答的点赞/点踩反馈。
     * <p>
     * 写入失败不抛出异常，仅记录日志，确保不影响主流程。
     *
     * @param request 反馈请求（messageId / conversationId / rating / 可选 comment）
     */
    public void recordFeedback(FeedbackRequest request) {
        // 反馈为辅助功能，关键字段缺失时静默跳过，不向前端返回错误。
        if (request == null || !org.springframework.util.StringUtils.hasText(request.messageId())
                || !org.springframework.util.StringUtils.hasText(request.conversationId())
                || !org.springframework.util.StringUtils.hasText(request.rating())) {
            log.debug("AI 反馈数据不完整，已跳过，messageId={}, conversationId={}, rating={}",
                    request != null ? request.messageId() : null,
                    request != null ? request.conversationId() : null,
                    request != null ? request.rating() : null);
            return;
        }
        try {
            AiMessageFeedback feedback = new AiMessageFeedback();
            feedback.setId(idGenerator.nextId());
            feedback.setMessageId(request.messageId());
            feedback.setConversationId(request.conversationId());
            feedback.setUserId(currentUserId());
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

    /**
     * 构建多轮对话历史文本，注入模型上下文。
     * <p>
     * 从 MySQL 读取最近 10 条消息（最多 5 轮对话），去重后拼接为角色标注文本。
     * 限制总长 2000 字符，保留最近轮次优先。
     * 新会话（无历史消息）返回空字符串。
     */
    private String conversationHistory(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return "";
        }
        try {
            List<AiMessageVO> messages = conversationService.recentMessages(conversationId, currentUserId(), currentUserCode(), 10);
            if (messages == null || messages.isEmpty()) {
                return "";
            }
            // 去重：连续同角色同内容的消息只保留一条
            List<AiMessageVO> deduped = new ArrayList<>();
            String lastContent = "";
            for (AiMessageVO message : messages) {
                if (message.content().equals(lastContent)) {
                    continue;
                }
                deduped.add(message);
                lastContent = message.content();
            }
            StringBuilder history = new StringBuilder();
            int totalLength = 0;
            int maxLength = 2000;
            // 逆序拼接，优先保留最近的消息
            for (int i = deduped.size() - 1; i >= 0 && totalLength < maxLength; i--) {
                AiMessageVO message = deduped.get(i);
                String roleLabel = "user".equals(message.role()) ? "用户" : "AI助手";
                String line = roleLabel + "：" + message.content() + "\n";
                if (totalLength + line.length() > maxLength) {
                    break;
                }
                history.insert(0, line);
                totalLength += line.length();
            }
            return history.toString().trim();
        } catch (RuntimeException exception) {
            log.debug("构建多轮对话历史失败，conversationId={}, reason={}", conversationId, exception.getMessage());
            return "";
        }
    }

    private String nullToBlank(String value) {
        return value == null ? "" : masker.mask(value);
    }
}
