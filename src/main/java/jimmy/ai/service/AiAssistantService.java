package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.model.AiChatRequest;
import jimmy.ai.model.AiChatResponse;
import jimmy.ai.model.AiCitation;
import jimmy.ai.model.AiConversationVO;
import jimmy.ai.model.AiLogAnalysisResponse;
import jimmy.ai.model.AiLogAnalyzeRequest;
import jimmy.ai.model.AiToolCall;
import jimmy.config.TraceContextSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    public AiAssistantService(AiKnowledgeService knowledgeService,
                              AiReadonlyQueryService readonlyQueryService,
                              AiLogAnalysisService logAnalysisService,
                              AiModelGateway modelGateway,
                              AiConversationService conversationService,
                              AiSensitiveDataMasker masker,
                              TraceContextSupport traceContextSupport) {
        this.knowledgeService = knowledgeService;
        this.readonlyQueryService = readonlyQueryService;
        this.logAnalysisService = logAnalysisService;
        this.modelGateway = modelGateway;
        this.conversationService = conversationService;
        this.masker = masker;
        this.traceContextSupport = traceContextSupport;
    }

    public AiChatResponse chat(AiChatRequest request) {
        String safeMessage = masker.mask(request.message());
        List<AiCitation> citations = new ArrayList<>(knowledgeService.search(safeMessage));
        List<AiToolCall> toolCalls = new ArrayList<>();

        StringBuilder context = new StringBuilder(contextText(citations));
        AiReadonlyQueryResult queryResult = readonlyQueryService.query(safeMessage);
        if (queryResult.executed()) {
            citations.addAll(queryResult.citations());
            toolCalls.addAll(queryResult.toolCalls());
            context.append("\n业务只读查询摘要：").append(queryResult.answerContext()).append("\n");
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

        String systemPrompt = """
                你是物流管理系统的 AI 助手，只能做只读问答、系统使用说明、业务数据摘要和日志排障。
                不允许承诺已经新增、修改、删除、导入、导出或上传数据。
                涉及写操作时，只能给出人工操作路径和注意事项。
                如果上下文提示权限不足，只能回复友好的中文权限提示，不要暴露权限码、内部模块名、字段名、SQL 或异常堆栈。
                回答必须基于给定上下文；不知道就说明需要进一步查询。
                """;
        String userPrompt = "用户问题：" + safeMessage
                + "\n页面上下文：" + nullToBlank(request.pageContext())
                + "\n参考资料：\n" + context;
        Optional<String> modelAnswer = modelGateway.chat(systemPrompt, userPrompt);
        String answer = modelAnswer.orElseGet(() -> fallbackAnswer(citations, toolCalls));
        AiConversationVO conversation = saveConversation(request, safeMessage, answer);
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

    private AiConversationVO saveConversation(AiChatRequest request, String safeMessage, String answer) {
        try {
            return conversationService.append(currentUserId(), request.conversationId(), safeMessage, answer);
        } catch (RuntimeException exception) {
            log.warn("AI 会话缓存失败，已使用临时会话兜底，reason={}", exception.getMessage());
            String id = StringUtils.hasText(request.conversationId()) ? request.conversationId() : traceContextSupport.newOperationId();
            return new AiConversationVO(id, "临时会话", "", "", List.of());
        }
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
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId == null ? "anonymous" : String.valueOf(loginId);
    }

    private String nullToBlank(String value) {
        return value == null ? "" : masker.mask(value);
    }
}
