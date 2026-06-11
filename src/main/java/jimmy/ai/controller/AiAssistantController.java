package jimmy.ai.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jimmy.ai.model.AgentResult;
import jimmy.ai.model.AiChatRequest;
import jimmy.ai.model.DailyBriefingVO;
import jimmy.ai.model.AiChatResponse;
import jimmy.ai.model.AiConversationVO;
import jimmy.ai.model.AiLogAnalysisResponse;
import jimmy.ai.model.AiLogAnalyzeRequest;
import jimmy.ai.model.AiMemoryItemVO;
import jimmy.ai.model.AiMemoryProfileVO;
import jimmy.ai.model.AgentResult;
import jimmy.ai.model.AiMemorySettingsRequest;
import jimmy.ai.model.FeedbackRequest;
import jimmy.ai.service.AiAgentOrchestrator;
import jimmy.ai.service.AiAssistantService;
import jimmy.ai.service.AiMemoryService;
import jimmy.ai.service.AiProactiveAlertService;
import jimmy.common.model.ApiResponse;
import jimmy.common.model.PageResult;
import jimmy.common.trace.TraceContextSupport;
import jimmy.logistics.annotation.OperationLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

/**
 * AI 助手接口：只开放只读问答、日志排障、当前用户会话和长期记忆管理能力。
 */
@Slf4j
@RestController
@RequestMapping("/ai")
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;
    private final AiMemoryService aiMemoryService;
    private final AiAgentOrchestrator agentOrchestrator;
    private final AiProactiveAlertService proactiveAlertService;
    private final TraceContextSupport traceContextSupport;

    public AiAssistantController(AiAssistantService aiAssistantService,
                                 AiMemoryService aiMemoryService,
                                 AiAgentOrchestrator agentOrchestrator,
                                 AiProactiveAlertService proactiveAlertService,
                                 TraceContextSupport traceContextSupport) {
        this.aiAssistantService = aiAssistantService;
        this.aiMemoryService = aiMemoryService;
        this.agentOrchestrator = agentOrchestrator;
        this.proactiveAlertService = proactiveAlertService;
        this.traceContextSupport = traceContextSupport;
    }

    @OperationLog("AI助手-普通问答")
    @PostMapping("/chat")
    public ApiResponse<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        return ApiResponse.success(aiAssistantService.chat(request));
    }

    /**
     * SSE 流式对话端点。
     * <p>
     * 前端通过 POST body 传递用户问题，避免把 AI 提问正文暴露到 URL、浏览器历史或代理访问日志。
     * 后端使用 StreamingResponseBody 直接写出 SSE 事件，实时推送 thinking/tool_result/done/error 等进度。
     */
    @OperationLog("AI助手-SSE流式问答")
    @PostMapping(value = "/chat/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> chatStream(@Valid @RequestBody AiChatRequest request) {
        return streamResponse(request);
    }

    /**
     * 兼容旧版 GET 流式接口。新前端已改为 POST，保留该入口只为避免旧页面刷新后立即失效。
     */
    @OperationLog("AI助手-SSE流式问答")
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> chatStreamLegacy(@RequestParam String message,
                                                                  @RequestParam(required = false) String conversationId,
                                                                  @RequestParam(required = false) String pageContext) {
        return streamResponse(new AiChatRequest(message, conversationId, pageContext));
    }

    private ResponseEntity<StreamingResponseBody> streamResponse(AiChatRequest request) {
        // 捕获当前 HTTP 线程的登录态、权限和数据范围；流式输出时可能切换线程，不能再直接依赖 Sa-Token ThreadLocal。
        String loginId = String.valueOf(StpUtil.getLoginIdDefaultNull());
        List<String> permissions = StpUtil.getPermissionList();
        String roleCode = String.valueOf(StpUtil.getSession().get("roleCode", ""));
        String customerId = String.valueOf(StpUtil.getSession().get("customerId", ""));
        String username = String.valueOf(StpUtil.getSession().get("username", ""));
        String userCode = String.valueOf(StpUtil.getSession().get("userCode", ""));
        String loginSessionId = String.valueOf(StpUtil.getSession().get(TraceContextSupport.LOGIN_SESSION_ID, ""));

        StreamingResponseBody stream = outputStream ->
                aiAssistantService.chatStream(request, outputStream, loginId, permissions, roleCode, customerId, username, userCode, loginSessionId);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(stream);
    }

    @OperationLog("AI助手-日志分析")
    @PostMapping("/logs/analyze")
    public ApiResponse<AiLogAnalysisResponse> analyzeLogs(@RequestBody AiLogAnalyzeRequest request) {
        return ApiResponse.success(aiAssistantService.analyzeLogs(request));
    }

    @OperationLog("AI助手-查询会话列表")
    @GetMapping("/conversations")
    public ApiResponse<PageResult<AiConversationVO>> conversations(@RequestParam(defaultValue = "ACTIVE") String status,
                                                                   @RequestParam(required = false) String keyword,
                                                                   @RequestParam(defaultValue = "1") int page,
                                                                   @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(aiAssistantService.conversations(status, keyword, page, pageSize));
    }

    @OperationLog("AI助手-查询会话详情")
    @GetMapping("/conversations/{id}")
    public ApiResponse<AiConversationVO> conversation(@PathVariable String id) {
        return ApiResponse.success(aiAssistantService.conversation(id));
    }

    @OperationLog("AI助手-归档会话")
    @PutMapping("/conversations/{id}/archive")
    public ApiResponse<Void> archiveConversation(@PathVariable String id) {
        aiAssistantService.archiveConversation(id);
        return ApiResponse.success(null);
    }

    @OperationLog("AI助手-恢复会话")
    @PutMapping("/conversations/{id}/restore")
    public ApiResponse<Void> restoreConversation(@PathVariable String id) {
        aiAssistantService.restoreConversation(id);
        return ApiResponse.success(null);
    }

    @OperationLog("AI助手-删除会话")
    @DeleteMapping("/conversations/{id}")
    public ApiResponse<Void> deleteConversation(@PathVariable String id) {
        aiAssistantService.deleteConversation(id);
        return ApiResponse.success(null);
    }

    @OperationLog("AI助手-清空会话")
    @DeleteMapping("/conversations")
    public ApiResponse<Void> clearConversations(@RequestParam(defaultValue = "ACTIVE") String status) {
        aiAssistantService.clearConversations(status);
        return ApiResponse.success(null);
    }

    @OperationLog("AI助手-查询长期记忆画像")
    @GetMapping("/memory/profile")
    public ApiResponse<AiMemoryProfileVO> memoryProfile() {
        return ApiResponse.success(aiMemoryService.profile());
    }

    @OperationLog("AI助手-查询长期记忆列表")
    @GetMapping("/memory/items")
    public ApiResponse<PageResult<AiMemoryItemVO>> memoryItems(@RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "20") int pageSize,
                                                               @RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) String memoryType) {
        return ApiResponse.success(aiMemoryService.items(page, pageSize, keyword, memoryType));
    }

    @OperationLog("AI助手-删除长期记忆")
    @DeleteMapping("/memory/items/{id}")
    public ApiResponse<Void> deleteMemory(@PathVariable Long id) {
        aiMemoryService.deleteMemory(id);
        return ApiResponse.success(null);
    }

    @OperationLog("AI助手-清空长期记忆")
    @DeleteMapping("/memory/items")
    public ApiResponse<Void> clearMemories() {
        aiMemoryService.clearMemories();
        return ApiResponse.success(null);
    }

    @OperationLog("AI助手-更新长期记忆设置")
    @PutMapping("/memory/settings")
    public ApiResponse<AiMemoryProfileVO> updateMemorySettings(@RequestBody AiMemorySettingsRequest request) {
        return ApiResponse.success(aiMemoryService.updateSettings(request));
    }

    @OperationLog("AI助手-提交反馈")
    @PostMapping("/feedback")
    public ApiResponse<Void> submitFeedback(@Valid @RequestBody FeedbackRequest request) {
        aiAssistantService.recordFeedback(request);
        return ApiResponse.success(null);
    }

    /**
     * AI Agent 自主编排流式端点。
     * <p>
     * 模型自主判断是否需要多步工具调用，循环执行直到完成或达到上限（5轮）。
     * SSE 事件：agent_start → thinking → tool_start → tool_result → ... → agent_done
     */
    @OperationLog("AI助手-Agent编排")
    @PostMapping(value = "/agent/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> agentStream(
            @Valid @RequestBody AiChatRequest request,
            HttpServletResponse httpResponse) {
        StpUtil.checkLogin();
        Object loginId = StpUtil.getLoginId();
        String loginIdStr = String.valueOf(loginId);
        List<String> permissions = StpUtil.getPermissionList(loginId);
        String roleCode = String.valueOf(StpUtil.getSessionByLoginId(loginId).get("roleCode", ""));
        String customerId = String.valueOf(StpUtil.getSessionByLoginId(loginId).get("customerId", ""));
        String username = String.valueOf(StpUtil.getSessionByLoginId(loginId).get("username", ""));
        String userCode = String.valueOf(StpUtil.getSessionByLoginId(loginId).get("userCode", ""));
        String loginSessionId = String.valueOf(StpUtil.getSessionByLoginId(loginId)
                .get(TraceContextSupport.LOGIN_SESSION_ID, ""));

        httpResponse.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        httpResponse.setCharacterEncoding("UTF-8");
        httpResponse.setHeader("Cache-Control", "no-cache");
        httpResponse.setHeader("X-Accel-Buffering", "no");

        StreamingResponseBody stream = outputStream -> {
            try {
                AgentResult result = agentOrchestrator.execute(
                        request.message(), resolveConversationId(request.conversationId()), outputStream);
                log.info("AI Agent 编排完成，iterations={}, toolCalls={}, costMs={}",
                        result.totalIterations(), result.totalToolCalls(), result.durationMs());
            } catch (RuntimeException exception) {
                log.error("AI Agent 编排异常", exception);
            }
        };
        return ResponseEntity.ok(stream);
    }

    @OperationLog("AI助手-查询每日简报")
    @GetMapping("/insights/daily")
    public ApiResponse<DailyBriefingVO> dailyBriefing() {
        StpUtil.checkLogin();
        return ApiResponse.success(proactiveAlertService.generateDailyBriefing());
    }

    @OperationLog("AI助手-查询异常检测")
    @GetMapping("/insights/anomalies")
    public ApiResponse<List<String>> anomalies() {
        StpUtil.checkLogin();
        return ApiResponse.success(proactiveAlertService.detectAnomalies());
    }

    private String resolveConversationId(String conversationId) {
        return StringUtils.hasText(conversationId) ? conversationId : traceContextSupport.newOperationId();
    }
}
