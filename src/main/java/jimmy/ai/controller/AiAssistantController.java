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
import jimmy.ai.model.AiMemorySettingsRequest;
import jimmy.ai.model.FeedbackRequest;
import jimmy.ai.service.AiAgentOrchestrator;
import jimmy.ai.service.AiAssistantService;
import jimmy.ai.service.AiFileAnalysisService;
import jimmy.ai.service.AiMemoryService;
import jimmy.ai.service.AiProactiveAlertService;
import jimmy.ai.util.SseChatContext;
import jimmy.common.model.ApiResponse;
import jimmy.common.model.PageResult;
import jimmy.common.trace.TraceContextSupport;
import jimmy.logistics.annotation.OperationLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final AiFileAnalysisService fileAnalysisService;
    private final TraceContextSupport traceContextSupport;
    private final boolean legacyGetStreamEnabled;

    public AiAssistantController(AiAssistantService aiAssistantService,
                                 AiMemoryService aiMemoryService,
                                 AiAgentOrchestrator agentOrchestrator,
                                 AiProactiveAlertService proactiveAlertService,
                                 AiFileAnalysisService fileAnalysisService,
                                 TraceContextSupport traceContextSupport,
                                 @Value("${app.ai.sse.legacy-get-enabled:true}") boolean legacyGetStreamEnabled) {
        this.aiAssistantService = aiAssistantService;
        this.aiMemoryService = aiMemoryService;
        this.agentOrchestrator = agentOrchestrator;
        this.proactiveAlertService = proactiveAlertService;
        this.fileAnalysisService = fileAnalysisService;
        this.traceContextSupport = traceContextSupport;
        this.legacyGetStreamEnabled = legacyGetStreamEnabled;
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
        if (!legacyGetStreamEnabled) {
            // 旧 GET 入口会把用户问题放进 URL。默认保留兼容，生产可关闭并提示前端刷新使用 POST SSE。
            StreamingResponseBody disabled = outputStream -> outputStream.write(
                    "event: error\ndata: {\"message\":\"旧版流式入口已关闭，请刷新页面后重试。\"}\n\n"
                            .getBytes(StandardCharsets.UTF_8));
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(disabled);
        }
        return streamResponse(new AiChatRequest(message, conversationId, pageContext, null));
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
        @SuppressWarnings("unchecked")
        Map<String, Set<String>> columnIndex = (Map<String, Set<String>>) StpUtil.getSession().get("columnIndex");

        StreamingResponseBody stream = outputStream -> {
            SseChatContext.setLoginIdAndPermissions(loginId, permissions);
            SseChatContext.setRoleCode(roleCode);
            SseChatContext.setCustomerId(customerId);
            SseChatContext.setUsername(username);
            SseChatContext.setUserCode(userCode);
            SseChatContext.setLoginSessionId(loginSessionId);
            SseChatContext.setColumnIndex(columnIndex);
            try {
                aiAssistantService.chatStream(request, outputStream, loginId, permissions, roleCode, customerId, username, userCode, loginSessionId);
            } finally {
                SseChatContext.clear();
            }
        };
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
                                                               @RequestParam(required = false) String memoryType,
                                                               @RequestParam(required = false) String status) {
        return ApiResponse.success(aiMemoryService.items(page, pageSize, keyword, memoryType, status));
    }

    @OperationLog("AI助手-批准长期记忆")
    @PutMapping("/memory/items/{id}/approve")
    public ApiResponse<Void> approveMemory(@PathVariable Long id) {
        aiMemoryService.approveMemory(id);
        return ApiResponse.success(null);
    }

    @OperationLog("AI助手-拒绝长期记忆")
    @PutMapping("/memory/items/{id}/reject")
    public ApiResponse<Void> rejectMemory(@PathVariable Long id) {
        aiMemoryService.rejectMemory(id);
        return ApiResponse.success(null);
    }

    @OperationLog("AI助手-恢复长期记忆")
    @PutMapping("/memory/items/{id}/restore")
    public ApiResponse<Void> restoreMemory(@PathVariable Long id) {
        aiMemoryService.restoreMemory(id);
        return ApiResponse.success(null);
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
    public ApiResponse<Void> submitFeedback(@RequestBody FeedbackRequest request) {
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
        String usernameMasked = String.valueOf(StpUtil.getSessionByLoginId(loginId).get("usernameMasked", ""));
        String userCode = String.valueOf(StpUtil.getSessionByLoginId(loginId).get("userCode", ""));
        String loginSessionId = String.valueOf(StpUtil.getSessionByLoginId(loginId)
                .get(TraceContextSupport.LOGIN_SESSION_ID, ""));
        @SuppressWarnings("unchecked")
        Map<String, Set<String>> agentColumnIndex = (Map<String, Set<String>>) StpUtil.getSessionByLoginId(loginId).get("columnIndex");
        String traceId = traceContextSupport.currentOrNewTraceId();
        String operationId = traceContextSupport.currentOrNewOperationId();

        httpResponse.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        httpResponse.setCharacterEncoding("UTF-8");
        httpResponse.setHeader("Cache-Control", "no-cache");
        httpResponse.setHeader("X-Accel-Buffering", "no");

        StreamingResponseBody stream = outputStream -> {
            try {
                // StreamingResponseBody 会切换到异步线程执行，Sa-Token 和 MDC 的 ThreadLocal 不会自动传递。
                // 这里恢复 Controller 预先捕获的权限、数据范围和链路标识，保证 Agent 工具查询仍受原账号约束。
                SseChatContext.setLoginIdAndPermissions(loginIdStr, permissions);
                SseChatContext.setRoleCode(roleCode);
                SseChatContext.setCustomerId(customerId);
                SseChatContext.setUsername(username);
                SseChatContext.setUserCode(userCode);
                SseChatContext.setLoginSessionId(loginSessionId);
                SseChatContext.setColumnIndex(agentColumnIndex);
                traceContextSupport.put(TraceContextSupport.TRACE_ID, traceId);
                traceContextSupport.put(TraceContextSupport.OPERATION_ID, operationId);
                traceContextSupport.put(TraceContextSupport.LOGIN_SESSION_ID, loginSessionId);
                traceContextSupport.put(TraceContextSupport.USER_ID, loginIdStr);
                traceContextSupport.put(TraceContextSupport.USER_CODE, userCode);
                traceContextSupport.put(TraceContextSupport.USERNAME_MASKED, usernameMasked);
                traceContextSupport.put(TraceContextSupport.ROLE_CODE, roleCode);
                AgentResult result = agentOrchestrator.execute(
                        request.message(), resolveConversationId(request.conversationId()), outputStream);
                log.info("AI Agent 编排完成，iterations={}, toolCalls={}, costMs={}",
                        result.totalIterations(), result.totalToolCalls(), result.durationMs());
            } catch (RuntimeException exception) {
                log.error("AI Agent 编排异常", exception);
            } finally {
                SseChatContext.clear();
                traceContextSupport.clearTraceContext();
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

    /**
     * 多模态文件分析：上传文件（Excel/CSV/TXT）由 AI 分析并返回摘要。
     * <p>
     * 支持格式：.xlsx / .xls / .csv / .txt，文件上限 10MB。
     */
    @OperationLog("AI助手-文件分析")
    @PostMapping(value = "/chat/with-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<String> chatWithFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "message", defaultValue = "") String message) {
        StpUtil.checkLogin();
        return ApiResponse.success(fileAnalysisService.analyze(file, message));
    }

    private String resolveConversationId(String conversationId) {
        return StringUtils.hasText(conversationId) ? conversationId : traceContextSupport.newOperationId();
    }
}
