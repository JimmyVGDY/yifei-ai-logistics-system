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
import jimmy.ai.util.SseChatContext;
import jimmy.config.TraceContextSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
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
                              AiMemoryService memoryService) {
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
    }

    public AiChatResponse chat(AiChatRequest request) {
        long start = System.currentTimeMillis();
        String safeMessage = masker.mask(request.message());
        String conversationId = resolveConversationId(request.conversationId());
        auditLogService.recordUserQuestion(conversationId, safeMessage);
        List<AiCitation> citations = new ArrayList<>(knowledgeService.search(safeMessage));
        List<AiToolCall> toolCalls = new ArrayList<>();
        AiConversationVO currentConversation = currentConversation(conversationId);
        String previousUserMessage = latestUserMessage(currentConversation);
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

        String systemPrompt = buildSystemPrompt();
        String userPrompt = "用户问题：" + safeMessage
                + "\n页面上下文：" + nullToBlank(request.pageContext())
                + "\n上一轮用户问题：" + nullToBlank(previousUserMessage)
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
        String answer = fallbackQueryExecuted ? fallbackAnswer(citations, toolCalls) : modelAnswer.orElseGet(() -> fallbackAnswer(citations, toolCalls));
        AiConversationVO conversation = saveConversation(conversationId, safeMessage, answer);
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
    public void chatStream(AiChatRequest request, SseEmitter emitter, String loginId, List<String> permissions,
                           String roleCode, String customerId, String username, String userCode) {
        long start = System.currentTimeMillis();
        // 将 Controller 捕获的登录标识、权限列表和 Session 属性注入当前异步线程
        SseChatContext.setLoginIdAndPermissions(loginId, permissions);
        SseChatContext.setRoleCode(roleCode);
        SseChatContext.setCustomerId(customerId);
        SseChatContext.setUsername(username);
        SseChatContext.setUserCode(userCode);
        String safeMessage = masker.mask(request.message());
        String conversationId = resolveConversationId(request.conversationId());
        List<AiCitation> citations = new ArrayList<>();
        List<AiToolCall> toolCalls = new ArrayList<>();

        try {
            // 初始化 SSE 上下文
            toolCallContext.begin(emitter);
            toolCallContext.notifyThinking();

            auditLogService.recordUserQuestion(conversationId, safeMessage);
            citations.addAll(new ArrayList<>(knowledgeService.search(safeMessage)));
            AiConversationVO currentConversation = currentConversation(conversationId);
            String previousUserMessage = latestUserMessage(currentConversation);
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

            String systemPrompt = buildSystemPrompt();
            String userPrompt = "用户问题：" + safeMessage
                    + "\n页面上下文：" + nullToBlank(request.pageContext())
                    + "\n上一轮用户问题：" + nullToBlank(previousUserMessage)
                    + "\n参考资料：\n" + context;

            // 模型调用 —— 工具方法内部会通过 AiToolCallContext 自动推送 tool_start / tool_result 事件
            Optional<String> modelAnswer = safeChatWithTools(systemPrompt, userPrompt);
            // snapshotAndClear() 会移出 Holder，需要立即重新绑定 emitter 以保证 notifyDone() 能推送最终事件
            AiToolCallContext.Snapshot toolSnapshot = toolCallContext.snapshotAndClear();
            toolCallContext.begin(emitter);
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
            String answer = fallbackQueryExecuted ? fallbackAnswer(citations, toolCalls) : modelAnswer.orElseGet(() -> fallbackAnswer(citations, toolCalls));
            AiConversationVO conversation = saveConversation(conversationId, safeMessage, answer);
            memoryService.rememberInteraction(conversation.conversationId(), safeMessage, answer, toolCalls);
            auditLogService.recordResponse(conversation.conversationId(), safeMessage,
                    "引用来源=" + citations.size() + "，工具调用=" + toolCalls.size() + "，模型已配置=" + modelGateway.configured(),
                    System.currentTimeMillis() - start);
            log.info("AI助手SSE问答完成，conversationId={}, modelConfigured={}, citationCount={}, toolCallCount={}, costMs={}",
                    conversation.conversationId(), modelGateway.configured(), citations.size(), toolCalls.size(), System.currentTimeMillis() - start);

            // 推送最终答案
            toolCallContext.notifyDone(answer, citations, toolCalls);
        } catch (Exception exception) {
            log.error("AI助手SSE问答异常，conversationId={}, message={}", conversationId, exception.getMessage(), exception);
            toolCallContext.notifyError("系统响应超时，请稍后重试");
        } finally {
            // 统一关闭 SSE 连接：延迟 complete 以确保所有 send() 事件的异步分派已完成。
            // send() 和 complete() 分别触发 Spring MVC 的异步 dispatch，极短时间内连续调用会导致
            // complete() dispatch 覆写尚未执行完的 send() dispatch，最终 done 事件无法到达前端。
            safeCompleteEmitter(emitter);
            SseChatContext.clear();
            toolCallContext.snapshotAndClear();
        }
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
                用户说得模糊时，优先使用全场景模糊搜索；用户提到客户全貌、订单完整链路、司机任务链路、车辆任务链路或异常影响时，使用业务联合查询。
                涉及统计、排名、汇总、关联或连表分析时，才使用临时只读 SQL 工具。
                如果上下文或工具结果提示权限不足，只能回复友好的中文权限提示，不要暴露权限码、内部模块名、字段名、SQL 或异常堆栈。
                回答必须基于给定上下文或工具结果；不知道就说明需要进一步查询。
                每次回答最多调用 5 次工具。能用一次查询回答的问题，不要拆分多次调用。
                """;
    }

    /**
     * 安全关闭 SSE 连接。
     * <p>
     * SseEmitter.send() 与 complete() 各自触发独立的 Spring MVC 异步 dispatch。
     * 如果两者间隔极短，complete() 的 dispatch 可能覆写尚未执行的 send() dispatch，
     * 导致最后的 done/error 事件无法到达前端。
     * <p>
     * 延迟 500ms 确保所有 send() 事件的 dispatch 都有足够时间被 MVC 异步执行器消费。
     */
    private void safeCompleteEmitter(SseEmitter emitter) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            emitter.complete();
        } catch (Exception e) {
            log.warn("SSE emitter.complete() 失败，conversationId 未知", e);
        }
    }

    private Optional<String> callModelWithTools(String systemPrompt, String userPrompt) {
        toolCallContext.begin();
        try {
            return modelGateway.chat(systemPrompt, userPrompt, businessQueryTools);
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

    public List<AiConversationVO> conversations() {
        return conversationService.list(currentUserId());
    }

    public AiConversationVO conversation(String conversationId) {
        return conversationService.find(currentUserId(), conversationId);
    }

    private AiConversationVO saveConversation(String conversationId, String safeMessage, String answer) {
        try {
            return conversationService.append(currentUserId(), conversationId, safeMessage, answer);
        } catch (RuntimeException exception) {
            log.warn("AI 会话缓存失败，已使用临时会话兜底，reason={}", exception.getMessage());
            String id = StringUtils.hasText(conversationId) ? conversationId : traceContextSupport.newOperationId();
            return new AiConversationVO(id, "临时会话", "", "", List.of());
        }
    }

    private String resolveConversationId(String conversationId) {
        return StringUtils.hasText(conversationId) ? conversationId : traceContextSupport.newOperationId();
    }

    private String fallbackAnswer(List<AiCitation> citations, List<AiToolCall> toolCalls) {
        StringBuilder builder = new StringBuilder();
        if (!modelGateway.configured()) {
            builder.append("当前未配置模型 API Key，已返回本地只读检索结果。");
        } else {
            builder.append("模型调用暂时不可用，已返回本地只读检索结果。");
        }
        if (!toolCalls.isEmpty()) {
            builder.append("\n\n已执行工具：");
            for (AiToolCall toolCall : toolCalls) {
                builder.append("\n- ").append(toolCall.toolName())
                        .append("：").append(toolCall.target())
                        .append("，").append(toolCall.result());
            }
        }
        if (!citations.isEmpty()) {
            builder.append("\n\n可参考资料：");
            for (AiCitation citation : citations) {
                builder.append("\n- ").append(citation.title()).append("：").append(citation.snippet());
            }
        }
        if (citations.isEmpty() && toolCalls.isEmpty()) {
            builder.append("\n\n没有找到足够的本地上下文。你可以补充订单号、客户名、页面名称、traceId、operationId 或 loginSessionId。");
        }
        return masker.mask(builder.toString());
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

    private AiConversationVO currentConversation(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return null;
        }
        try {
            return conversationService.find(currentUserId(), conversationId);
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

    private String nullToBlank(String value) {
        return value == null ? "" : masker.mask(value);
    }
}
