package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.model.AiChatRequest;
import jimmy.ai.model.AiChatResponse;
import jimmy.ai.model.AiCitation;
import jimmy.ai.model.AiConversationVO;
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
                              CompactSnowflakeIdGenerator idGenerator) {
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
    }

    public AiChatResponse chat(AiChatRequest request) {
        long start = System.currentTimeMillis();
        String safeMessage = masker.mask(request.message());
        String conversationId = resolveConversationId(request.conversationId());
        AiConversationVO currentConversation = currentConversation(conversationId);
        String previousUserMessage = latestUserMessage(currentConversation);
        conversationService.appendUserMessage(currentUserId(), currentUserCode(), conversationId, safeMessage);
        auditLogService.recordUserQuestion(conversationId, safeMessage);
        List<AiCitation> citations = new ArrayList<>(knowledgeService.search(safeMessage));
        List<AiToolCall> toolCalls = new ArrayList<>();
        AiMemoryRecallResult memoryRecall = memoryService.recall(safeMessage, conversationId);

        StringBuilder context = new StringBuilder(contextText(citations));
        if (memoryRecall.hitCount() > 0) {
            citations.addAll(memoryRecall.citations());
            context.append("\nAI 长期记忆召回摘要：").append(memoryRecall.context()).append("\n");
            toolCalls.add(new AiToolCall("长期记忆召回", "当前账号长期偏好", "命中 " + memoryRecall.hitCount() + " 条长期记忆"));
        }

        if (looksLikeLogQuestion(safeMessage)) {
            AiLogAnalysisResponse analysis = logAnalysisService.analyze(new AiLogAnalyzeRequest(
                    extractToken(safeMessage, "traceId"),
                    extractToken(safeMessage, "operationId"),
                    extractToken(safeMessage, "loginSessionId"),
                    null,
                    null,
                    null,
                    null
            ));
            context.append("\n日志排障摘要：").append(analysis.summary()).append("\n");
            AiToolCall logToolCall = new AiToolCall("日志排障", "操作日志", analysis.summary());
            toolCalls.add(logToolCall);
        }

        String conversationHistory = conversationHistory(conversationId);
        String systemPrompt = buildSystemPrompt();
        String userPrompt = "用户问题：" + safeMessage
                + "\n页面上下文：" + nullToBlank(request.pageContext())
                + "\n对话历史：\n" + conversationHistory
                + "\n参考资料：\n" + context;
        Optional<String> modelAnswer = callModelWithTools(systemPrompt, userPrompt);
        AiToolCallContext.Snapshot toolSnapshot = toolCallContext.snapshotAndClear();
        citations.addAll(toolSnapshot.citations());
        toolCalls.addAll(toolSnapshot.toolCalls());
        toolSnapshot.contexts().forEach(toolContext -> context.append("\nAI 工具调用摘要：").append(toolContext).append("\n"));

        /*
         * 模型未配置、模型未主动调用工具或工具调用失败时，继续走后端规则兜底。
         * 这样本地无 API Key 时仍能查询业务数据，也避免模型偶尔偷懒只给泛泛建议。
         */
        boolean fallbackQueryExecuted = false;
        if (toolCalls.isEmpty()) {
            AiReadonlyQueryResult queryResult = readonlyQueryService.query(safeMessage, previousUserMessage);
            if (queryResult.executed()) {
                fallbackQueryExecuted = true;
                citations.addAll(queryResult.citations());
                toolCalls.addAll(queryResult.toolCalls());
                context.append("\n业务只读查询摘要：").append(queryResult.answerContext()).append("\n");
            }
        }

        for (AiToolCall toolCall : toolCalls) {
            auditLogService.recordToolCall(conversationId, toolCall.toolName(), toolCall.target(), safeMessage, toolCall.result());
        }
        String answer = fallbackQueryExecuted ? fallbackHandler.fallbackAnswer(context.toString(), toolCalls) : modelAnswer.orElseGet(() -> fallbackHandler.fallbackAnswer(context.toString(), toolCalls));
        AiConversationVO conversation = saveConversation(conversationId, safeMessage, answer, toolCalls, citations);
        memoryService.rememberInteraction(conversation.conversationId(), safeMessage, answer, toolCalls);
        auditLogService.recordResponse(conversation.conversationId(), safeMessage,
                "引用来源=" + citations.size() + "，工具调用=" + toolCalls.size() + "，模型已配置=" + modelGateway.configured(),
                System.currentTimeMillis() - start);
        log.info("AI助手问答完成，conversationId={}, modelConfigured={}, citationCount={}, toolCallCount={}",
                conversation.conversationId(), modelGateway.configured(), citations.size(), toolCalls.size());
        return new AiChatResponse(
                conversation.conversationId(),
                answer,
                citations,
                toolCalls,
                traceContextSupport.currentOrNewTraceId(),
                traceContextSupport.currentOrNewOperationId()
        );
    }

    /**
     * SSE 流式对话：在后台线程中运行 AI 处理，通过 SseEmitter 实时推送工具调用进度和最终答案。
     * <p>
     * 工具调用过程中会推送 thinking / tool_start / tool_result 事件，
     * 完成时推送 done 事件包含完整答案，异常时推送 error 事件。
     * 前端通过 EventSource 接收这些事件，实现进度条、工具调用日志等实时展示。
     * </p>
     * <p>
     * 业务逻辑与 {@link #chat(AiChatRequest)} 一致，复用了知识检索、记忆召回、
     * 日志排障、模型调用、兜底查询、会话保存和审计日志等全流程。
     * </p>
     *
     * @param request        用户请求
     * @param emitter        SSE 推送通道
     */
    public void chatStream(AiChatRequest request, OutputStream outputStream, String loginId, List<String> permissions,
                           String roleCode, String customerId, String username, String userCode, String loginSessionId) {
        long start = System.currentTimeMillis();
        // 将 Controller 捕获的登录标识、权限列表和 Session 属性注入当前异步线程
        SseChatContext.setLoginIdAndPermissions(loginId, permissions);
        SseChatContext.setRoleCode(roleCode);
        SseChatContext.setCustomerId(customerId);
        SseChatContext.setUsername(username);
        SseChatContext.setUserCode(userCode);
        SseChatContext.setLoginSessionId(loginSessionId);
        bindSseTraceContext(loginId, userCode, username, roleCode, loginSessionId);
        String safeMessage = masker.mask(request.message());
        String conversationId = resolveConversationId(request.conversationId());
        List<AiCitation> citations = new ArrayList<>();
        List<AiToolCall> toolCalls = new ArrayList<>();
        String previousUserMessage = null;

        try {
            // 初始化 SSE 上下文——使用 OutputStream 直接写字节，无异步 dispatch 竞态
            toolCallContext.begin(outputStream);
            toolCallContext.notifyThinking();

            auditLogService.recordUserQuestion(conversationId, safeMessage);
            citations.addAll(new ArrayList<>(knowledgeService.search(safeMessage)));
            AiConversationVO currentConversation = currentConversation(conversationId);
            previousUserMessage = latestUserMessage(currentConversation);
            conversationService.appendUserMessage(currentUserId(), currentUserCode(), conversationId, safeMessage);
            AiMemoryRecallResult memoryRecall = memoryService.recall(safeMessage, conversationId);

            StringBuilder context = new StringBuilder(contextText(citations));
            if (memoryRecall.hitCount() > 0) {
                citations.addAll(memoryRecall.citations());
                context.append("\nAI 长期记忆召回摘要：").append(memoryRecall.context()).append("\n");
                toolCalls.add(new AiToolCall("长期记忆召回", "当前账号长期偏好", "命中 " + memoryRecall.hitCount() + " 条长期记忆"));
            }

            if (looksLikeLogQuestion(safeMessage)) {
                AiLogAnalysisResponse analysis = logAnalysisService.analyze(new AiLogAnalyzeRequest(
                        extractToken(safeMessage, "traceId"),
                        extractToken(safeMessage, "operationId"),
                        extractToken(safeMessage, "loginSessionId"),
                        null, null, null, null
                ));
                context.append("\n日志排障摘要：").append(analysis.summary()).append("\n");
                toolCalls.add(new AiToolCall("日志排障", "操作日志", analysis.summary()));
            }

            String conversationHistory = conversationHistory(conversationId);
            String systemPrompt = buildSystemPrompt();
            String userPrompt = "用户问题：" + safeMessage
                    + "\n页面上下文：" + nullToBlank(request.pageContext())
                    + "\n对话历史：\n" + conversationHistory
                    + "\n参考资料：\n" + context;

            // 模型调用 —— 工具方法内部会通过 AiToolCallContext 自动推送 tool_start / tool_result 事件
            Optional<String> modelAnswer = safeChatWithTools(systemPrompt, userPrompt);
            // snapshotAndClear() 会移出 Holder，需要立即重新绑定 outputStream 以保证 notifyDone() 能推送最终事件
            AiToolCallContext.Snapshot toolSnapshot = toolCallContext.snapshotAndClear();
            toolCallContext.begin(outputStream);
            citations.addAll(toolSnapshot.citations());
            toolCalls.addAll(toolSnapshot.toolCalls());
            toolSnapshot.contexts().forEach(toolContext -> context.append("\nAI 工具调用摘要：").append(toolContext).append("\n"));

            // 兜底查询
            boolean fallbackQueryExecuted = false;
            if (toolCalls.isEmpty()) {
                AiReadonlyQueryResult queryResult = readonlyQueryService.query(safeMessage, previousUserMessage);
                if (queryResult.executed()) {
                    fallbackQueryExecuted = true;
                    citations.addAll(queryResult.citations());
                    toolCalls.addAll(queryResult.toolCalls());
                    context.append("\n业务只读查询摘要：").append(queryResult.answerContext()).append("\n");
                }
            }

            for (AiToolCall toolCall : toolCalls) {
                auditLogService.recordToolCall(conversationId, toolCall.toolName(), toolCall.target(), safeMessage, toolCall.result());
            }
            String answer = fallbackQueryExecuted ? fallbackHandler.fallbackAnswer(context.toString(), toolCalls) : modelAnswer.orElseGet(() -> fallbackHandler.fallbackAnswer(context.toString(), toolCalls));
            AiConversationVO conversation = saveConversation(conversationId, safeMessage, answer, toolCalls, citations);
            memoryService.rememberInteraction(conversation.conversationId(), safeMessage, answer, toolCalls);
            auditLogService.recordResponse(conversation.conversationId(), safeMessage,
                    "引用来源=" + citations.size() + "，工具调用=" + toolCalls.size() + "，模型已配置=" + modelGateway.configured(),
                    System.currentTimeMillis() - start);
            log.info("AI助手SSE问答完成，conversationId={}, modelConfigured={}, citationCount={}, toolCallCount={}, costMs={}",
                    conversation.conversationId(), modelGateway.configured(), citations.size(), toolCalls.size(), System.currentTimeMillis() - start);

            // 推送最终答案
            toolCallContext.notifyDone(conversation.conversationId(), answer, citations, toolCalls);
        } catch (Exception exception) {
            log.error("AI助手SSE问答异常，conversationId={}, message={}", conversationId, exception.getMessage(), exception);
            try {
                conversationService.appendFailedAssistantMessage(currentUserId(), currentUserCode(), conversationId,
                        "系统响应超时，请稍后重试", previousUserMessage);
            } catch (RuntimeException saveException) {
                log.debug("AI SSE 失败消息落库失败，conversationId={}, reason={}", conversationId, saveException.getMessage());
            }
            toolCallContext.notifyError("系统响应超时，请稍后重试");
        } finally {
            // StreamingResponseBody lambda 返回后 Spring MVC 会自动关闭输出流，无需手动 complete()
            SseChatContext.clear();
            toolCallContext.snapshotAndClear();
        }
    }

    /**
     * SSE 异步线程没有原始 HTTP 拦截器的用户 MDC，这里用 Controller 预捕获的会话快照恢复审计字段。
     */
    private void bindSseTraceContext(String loginId, String userCode, String username, String roleCode, String loginSessionId) {
        traceContextSupport.put(TraceContextSupport.USER_ID, loginId);
        traceContextSupport.put(TraceContextSupport.USER_CODE, userCode);
        traceContextSupport.put(TraceContextSupport.USERNAME_MASKED, masker.mask(username));
        traceContextSupport.put(TraceContextSupport.ROLE_CODE, roleCode);
        traceContextSupport.put(TraceContextSupport.LOGIN_SESSION_ID, loginSessionId);
    }

    /**
     * 安全的模型调用：与 callModelWithTools 相同但不抛出异常。
     * 异常时返回 empty，由调用方走 fallbackAnswer 兜底。
     */
    private Optional<String> safeChatWithTools(String systemPrompt, String userPrompt) {
        try {
            return callModelWithTools(systemPrompt, userPrompt);
        } catch (RuntimeException exception) {
            log.warn("SSE 模型调用失败，将使用兜底回答，reason={}", exception.getMessage());
            return Optional.empty();
        }
    }

    private String buildSystemPrompt() {
        return """
                你是物流管理系统的 AI 助手，只能做只读问答、系统使用说明、业务数据摘要和日志排障。
                你可以调用后端只读工具查询业务数据，但不能承诺已经新增、修改、删除、导入、导出或上传数据。
                如果对话历史中有最近几轮的对话内容，请结合上下文理解用户的追问和省略表达。
                用户说得模糊时，优先使用全场景模糊搜索；用户提到客户全貌、订单完整链路、司机任务链路、车辆任务链路或异常影响时，使用业务联合查询。
                涉及统计、排名、汇总、关联或连表分析时，才使用临时只读 SQL 工具。
                如果上下文或工具结果提示权限不足，只能回复友好的中文权限提示，不要暴露权限码、内部模块名、字段名、SQL 或异常堆栈。
                回答必须基于给定上下文或工具结果；不知道就说明需要进一步查询。
                每次回答最多调用 5 次工具。能用一次查询回答的问题，不要拆分多次调用。
                """;
    }

    private Optional<String> callModelWithTools(String systemPrompt, String userPrompt) {
        toolCallContext.begin();
        try {
            return modelGateway.chat(systemPrompt, userPrompt, "chat", null, businessQueryTools);
        } catch (RuntimeException exception) {
            toolCallContext.snapshotAndClear();
            throw exception;
        }
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

    private String latestUserMessage(AiConversationVO conversation) {
        if (conversation == null || conversation.messages() == null) {
            return null;
        }
        List<AiMessageVO> messages = conversation.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            AiMessageVO message = messages.get(i);
            if ("user".equals(message.role()) && StringUtils.hasText(message.content())) {
                return message.content();
            }
        }
        return null;
    }

    /**
     * 记录用户对 AI 回答的点赞/点踩反馈。
     * <p>
     * 写入失败不抛出异常，仅记录日志，确保不影响主流程。
     *
     * @param request 反馈请求（messageId / conversationId / rating / 可选 comment）
     */
    public void recordFeedback(FeedbackRequest request) {
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
