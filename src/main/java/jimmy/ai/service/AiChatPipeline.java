package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.model.AiChatRequest;
import jimmy.ai.model.AiChatResponse;
import jimmy.ai.model.AiCitation;
import jimmy.ai.model.AiConversationVO;
import jimmy.ai.model.AiDataResult;
import jimmy.ai.model.AiExecutionPlan;
import jimmy.ai.model.AiLogAnalysisResponse;
import jimmy.ai.model.AiLogAnalyzeRequest;
import jimmy.ai.model.AiMemoryRecallResult;
import jimmy.ai.model.AiMessageVO;
import jimmy.ai.model.AiReadonlyQueryResult;
import jimmy.ai.model.AiToolCall;
import jimmy.ai.util.SseChatContext;
import jimmy.common.trace.TraceContextSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.OutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AI 问答统一编排管线。
 * <p>
 * 普通问答和 SSE 流式问答都从这里执行同一套只读流程，避免后续修复上下文、工具调用、
 * 记忆召回或兜底查询时只改到其中一个入口。
 */
@Slf4j
@Service
public class AiChatPipeline {

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
    private final AiIntentPlanner intentPlanner;
    private final long heartbeatSeconds;

    public AiChatPipeline(AiKnowledgeService knowledgeService,
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
                          AiIntentPlanner intentPlanner,
                          @Value("${app.ai.sse.heartbeat-seconds:10}") long heartbeatSeconds) {
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
        this.intentPlanner = intentPlanner;
        this.heartbeatSeconds = Math.max(1, heartbeatSeconds);
    }

    public AiChatResponse chat(AiChatRequest request) {
        return execute(request, null);
    }

    public void chatStream(AiChatRequest request, OutputStream outputStream, String loginId, List<String> permissions,
                           String roleCode, String customerId, String username, String userCode, String loginSessionId) {
        SseChatContext.setLoginIdAndPermissions(loginId, permissions);
        SseChatContext.setRoleCode(roleCode);
        SseChatContext.setCustomerId(customerId);
        SseChatContext.setUsername(username);
        SseChatContext.setUserCode(userCode);
        SseChatContext.setLoginSessionId(loginSessionId);
        bindSseTraceContext(loginId, userCode, username, roleCode, loginSessionId);
        try {
            execute(request, outputStream);
        } finally {
            SseChatContext.clear();
            toolCallContext.snapshotAndClear();
        }
    }

    private AiChatResponse execute(AiChatRequest request, OutputStream outputStream) {
        long start = System.currentTimeMillis();
        boolean streaming = outputStream != null;
        String safeMessage = masker.mask(request.message());
        String conversationId = resolveConversationId(request.conversationId());
        List<AiCitation> citations = new ArrayList<>();
        List<AiToolCall> toolCalls = new ArrayList<>();
        List<AiDataResult> dataResults = new ArrayList<>();
        String previousUserMessage = null;

        try {
            if (streaming) {
                toolCallContext.begin(outputStream);
                toolCallContext.notifyThinking();
                toolCallContext.notifyHeartbeat("AI 助手已接收问题，正在准备上下文。", heartbeatSeconds);
            }
            toolCallContext.setConversation(conversationId, currentUserId(), currentUserCode());
            AiConversationVO currentConversation = currentConversation(conversationId);
            previousUserMessage = latestUserMessage(currentConversation);
            conversationService.appendUserMessage(currentUserId(), currentUserCode(), conversationId, safeMessage);
            auditLogService.recordUserQuestion(conversationId, safeMessage);

            if (org.springframework.util.StringUtils.hasText(request.cursorId())) {
                return handleCursorContinuation(request.cursorId(), conversationId, safeMessage, citations, toolCalls,
                        dataResults, start, streaming);
            }

            AiExecutionPlan executionPlan = intentPlanner.plan(safeMessage);
            log.info("AI 执行计划已生成，conversationId={}, mode={}, modules={}, reason={}",
                    conversationId, executionPlan.mode(), executionPlan.candidateModules(), executionPlan.reason());
            citations.addAll(knowledgeService.search(safeMessage));
            AiMemoryRecallResult memoryRecall = memoryService.recall(safeMessage, conversationId);
            StringBuilder context = new StringBuilder("AI 执行计划：")
                    .append(executionPlan.mode().label())
                    .append("；候选模块=")
                    .append(executionPlan.candidateModules())
                    .append("；原因=")
                    .append(executionPlan.reason())
                    .append("\n")
                    .append(contextText(citations));
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
                toolCalls.add(new AiToolCall("日志排障", "操作日志", analysis.summary()));
            }

            String userPrompt = buildUserPrompt(request, safeMessage, conversationId, context);
            Optional<String> modelAnswer = streaming
                    ? safeChatWithTools(buildSystemPrompt(), userPrompt, safeMessage)
                    : callModelWithTools(buildSystemPrompt(), userPrompt, safeMessage);
            AiToolCallContext.Snapshot toolSnapshot = toolCallContext.snapshotAndClear();
            if (streaming) {
                toolCallContext.begin(outputStream);
            }
            citations.addAll(toolSnapshot.citations());
            toolCalls.addAll(toolSnapshot.toolCalls());
            dataResults.addAll(toolSnapshot.dataResults());
            toolSnapshot.contexts().forEach(toolContext -> context.append("\nAI 工具调用摘要：").append(toolContext).append("\n"));

            boolean fallbackQueryExecuted = false;
            if (toolCalls.isEmpty()) {
                AiReadonlyQueryResult queryResult = readonlyQueryService.query(safeMessage, previousUserMessage,
                        conversationId, currentUserId(), currentUserCode());
                if (queryResult.executed()) {
                    fallbackQueryExecuted = true;
                    citations.addAll(queryResult.citations());
                    toolCalls.addAll(queryResult.toolCalls());
                    if (queryResult.rows() != null && !queryResult.rows().isEmpty()) {
                        AiToolCall firstToolCall = queryResult.toolCalls().isEmpty()
                                ? new AiToolCall("业务数据查询", "业务数据", "已返回结构化数据")
                                : queryResult.toolCalls().getFirst();
                        dataResults.add(new AiDataResult(firstToolCall.toolName(), firstToolCall.target(),
                                firstToolCall.result(), queryResult.columns(), queryResult.rows(),
                                queryResult.cursorId(), queryResult.total(), queryResult.returnedCount(),
                                queryResult.remainingCount(), queryResult.hasMore(), queryResult.nextPageHint()));
                    }
                    context.append("\n业务只读查询摘要：").append(queryResult.answerContext()).append("\n");
                }
            }

            for (AiToolCall toolCall : toolCalls) {
                auditLogService.recordToolCall(conversationId, toolCall.toolName(), toolCall.target(), safeMessage, toolCall.result());
            }
            String answer = fallbackQueryExecuted
                    ? fallbackHandler.fallbackAnswer(context.toString(), toolCalls)
                    : modelAnswer.orElseGet(() -> fallbackHandler.fallbackAnswer(context.toString(), toolCalls));
            AiConversationVO conversation = saveConversation(conversationId, safeMessage, answer, toolCalls, citations);
            memoryService.rememberInteraction(conversation.conversationId(), safeMessage, answer, toolCalls);
            auditLogService.recordResponse(conversation.conversationId(), safeMessage,
                    "引用来源=" + citations.size() + "，工具调用=" + toolCalls.size() + "，模型已配置=" + modelGateway.configured(),
                    System.currentTimeMillis() - start);
            log.info("AI助手问答完成，conversationId={}, streaming={}, modelConfigured={}, citationCount={}, toolCallCount={}, costMs={}",
                    conversation.conversationId(), streaming, modelGateway.configured(), citations.size(), toolCalls.size(), System.currentTimeMillis() - start);
            if (streaming) {
                toolCallContext.notifyDone(conversation.conversationId(), answer, citations, toolCalls);
            }
            return new AiChatResponse(conversation.conversationId(), answer, citations, toolCalls, dataResults,
                    traceContextSupport.currentOrNewTraceId(), traceContextSupport.currentOrNewOperationId());
        } catch (Exception exception) {
            log.error("AI助手问答异常，conversationId={}, streaming={}, message={}", conversationId, streaming, exception.getMessage(), exception);
            if (streaming) {
                appendFailedAssistantMessage(conversationId, previousUserMessage);
                toolCallContext.notifyError("系统响应超时，请稍后重试");
                return new AiChatResponse(conversationId, "系统响应超时，请稍后重试", citations, toolCalls, dataResults,
                        traceContextSupport.currentOrNewTraceId(), traceContextSupport.currentOrNewOperationId());
            }
            throw exception;
        }
    }

    private AiChatResponse handleCursorContinuation(String cursorId,
                                                    String conversationId,
                                                    String safeMessage,
                                                    List<AiCitation> citations,
                                                    List<AiToolCall> toolCalls,
                                                    List<AiDataResult> dataResults,
                                                    long start,
                                                    boolean streaming) {
        /*
         * 前端点击某个结果卡片的“继续查看”时会携带 cursorId。
         * 这里直接按该游标继续分页，避免一次回答里存在多个业务表格时，后端误取“最新游标”或让模型重新猜上下文。
         */
        AiReadonlyQueryResult queryResult = readonlyQueryService.queryCursor(cursorId, conversationId, currentUserId(), currentUserCode());
        citations.addAll(queryResult.citations());
        toolCalls.addAll(queryResult.toolCalls());
        if (queryResult.rows() != null && !queryResult.rows().isEmpty()) {
            AiToolCall firstToolCall = queryResult.toolCalls().isEmpty()
                    ? new AiToolCall("业务数据查询", "业务数据", "已返回结构化数据")
                    : queryResult.toolCalls().getFirst();
            dataResults.add(new AiDataResult(firstToolCall.toolName(), firstToolCall.target(),
                    firstToolCall.result(), queryResult.columns(), queryResult.rows(),
                    queryResult.cursorId(), queryResult.total(), queryResult.returnedCount(),
                    queryResult.remainingCount(), queryResult.hasMore(), queryResult.nextPageHint()));
        }
        if (streaming && queryResult.executed()) {
            /*
             * SSE 前端只从 tool_result 事件收集表格数据，done 事件只承载最终文本。
             * 因此游标续页必须显式推送一次工具结果，否则页面会出现“有回答但没有表格”的情况。
             */
            AiToolCall firstToolCall = queryResult.toolCalls().isEmpty()
                    ? new AiToolCall("业务数据查询", "业务数据", "已返回结构化数据")
                    : queryResult.toolCalls().getFirst();
            toolCallContext.notifyToolResult(firstToolCall.toolName(), firstToolCall.target(), queryResult);
        }
        for (AiToolCall toolCall : toolCalls) {
            auditLogService.recordToolCall(conversationId, toolCall.toolName(), toolCall.target(), safeMessage, toolCall.result());
        }
        String answer = fallbackHandler.fallbackAnswer("业务只读查询摘要：" + queryResult.answerContext(), toolCalls);
        AiConversationVO conversation = saveConversation(conversationId, safeMessage, answer, toolCalls, citations);
        auditLogService.recordResponse(conversation.conversationId(), safeMessage,
                "按查询游标继续分页，工具调用=" + toolCalls.size(), System.currentTimeMillis() - start);
        if (streaming) {
            toolCallContext.notifyDone(conversation.conversationId(), answer, citations, toolCalls);
        }
        return new AiChatResponse(conversation.conversationId(), answer, citations, toolCalls, dataResults,
                traceContextSupport.currentOrNewTraceId(), traceContextSupport.currentOrNewOperationId());
    }

    private String buildUserPrompt(AiChatRequest request, String safeMessage, String conversationId, StringBuilder context) {
        return "用户问题：" + safeMessage
                + "\n当前业务日期：" + currentBusinessDate()
                + "\n页面上下文：" + nullToBlank(request.pageContext())
                + "\n对话历史：\n" + conversationHistory(conversationId)
                + "\n参考资料：\n" + context;
    }

    private String buildSystemPrompt() {
        return """
                你是物流管理系统的 AI 助手，只能做只读问答、系统使用说明、业务数据摘要和日志排障。
                当前业务日期由系统在用户提示中提供，涉及“今天、今日、昨天、最近7天、最近30天”等相对时间时，必须按该日期计算，不要使用模型训练时的历史日期。
                你可以调用后端只读工具查询业务数据，但不能承诺已经新增、修改、删除、导入、导出或上传数据。
                如果对话历史中有最近几轮的对话内容，请结合上下文理解用户的追问和省略表达。
                用户说得模糊时，优先使用全场景模糊搜索；用户提到客户全貌、订单完整链路、司机任务链路、车辆任务链路或异常影响时，使用业务联合查询。
                涉及统计、排名、汇总、关联或连表分析时，才使用临时只读 SQL 工具。
                如果上下文或工具结果提示权限不足，只能回复友好的中文权限提示，不要暴露权限码、内部模块名、字段名、SQL 或异常堆栈。
                回答必须基于给定上下文或工具结果；不知道就说明需要进一步查询。
                当工具结果已返回结构化数据时，聊天气泡只输出结论摘要、关键风险和查看建议，不要重复生成 Markdown 明细表格，也不要声称已完整列出所有记录。
                每次回答最多调用 %d 次工具。能用一次查询回答的问题，不要拆分多次调用。
                """.formatted(toolCallContext.maxToolCalls());
    }

    private Optional<String> callModelWithTools(String systemPrompt, String userPrompt, String originalQuestion) {
        toolCallContext.begin();
        toolCallContext.setOriginalQuestion(originalQuestion);
        try {
            return modelGateway.chat(systemPrompt, userPrompt, "chat", null, businessQueryTools);
        } catch (RuntimeException exception) {
            toolCallContext.snapshotAndClear();
            throw exception;
        }
    }

    private Optional<String> safeChatWithTools(String systemPrompt, String userPrompt, String originalQuestion) {
        try {
            return callModelWithTools(systemPrompt, userPrompt, originalQuestion);
        } catch (RuntimeException exception) {
            log.warn("SSE 模型调用失败，将使用兜底回答，reason={}", exception.getMessage());
            return Optional.empty();
        }
    }

    private void bindSseTraceContext(String loginId, String userCode, String username, String roleCode, String loginSessionId) {
        traceContextSupport.put(TraceContextSupport.USER_ID, loginId);
        traceContextSupport.put(TraceContextSupport.USER_CODE, userCode);
        traceContextSupport.put(TraceContextSupport.USERNAME_MASKED, masker.mask(username));
        traceContextSupport.put(TraceContextSupport.ROLE_CODE, roleCode);
        traceContextSupport.put(TraceContextSupport.LOGIN_SESSION_ID, loginSessionId);
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

    private void appendFailedAssistantMessage(String conversationId, String previousUserMessage) {
        try {
            conversationService.appendFailedAssistantMessage(currentUserId(), currentUserCode(), conversationId,
                    "系统响应超时，请稍后重试", previousUserMessage);
        } catch (RuntimeException saveException) {
            log.debug("AI SSE 失败消息落库失败，conversationId={}, reason={}", conversationId, saveException.getMessage());
        }
    }

    private String resolveConversationId(String conversationId) {
        return StringUtils.hasText(conversationId) ? conversationId : traceContextSupport.newOperationId();
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
        String[] parts = tail.split("[\\s,，。；;]+");
        return parts.length == 0 || !StringUtils.hasText(parts[0]) ? null : parts[0].trim();
    }

    private String currentBusinessDate() {
        return LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String currentUserId() {
        String sseLoginId = SseChatContext.getLoginId();
        if (StringUtils.hasText(sseLoginId) && !"null".equalsIgnoreCase(sseLoginId)) {
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

    static String latestUserMessage(AiConversationVO conversation) {
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

    private static boolean isContinuationRequest(String text) {
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

    private String conversationHistory(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return "";
        }
        try {
            List<AiMessageVO> messages = conversationService.recentMessages(conversationId, currentUserId(), currentUserCode(), 10);
            if (messages == null || messages.isEmpty()) {
                return "";
            }
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
