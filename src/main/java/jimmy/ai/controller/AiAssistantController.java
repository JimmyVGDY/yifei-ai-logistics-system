package jimmy.ai.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import jimmy.ai.model.AiChatRequest;
import jimmy.ai.model.AiChatResponse;
import jimmy.ai.model.AiConversationVO;
import jimmy.ai.model.AiLogAnalysisResponse;
import jimmy.ai.model.AiLogAnalyzeRequest;
import jimmy.ai.model.AiMemoryItemVO;
import jimmy.ai.model.AiMemoryProfileVO;
import jimmy.ai.model.AiMemorySettingsRequest;
import jimmy.ai.service.AiAssistantService;
import jimmy.ai.service.AiMemoryService;
import jimmy.logistics.annotation.OperationLog;
import jimmy.model.ApiResponse;
import jimmy.model.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * AI 助手接口 —— 仅开放只读问答、日志排障和当前用户会话查询。
 */
@RestController
@RequestMapping("/ai")
public class AiAssistantController {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantController.class);

    private final AiAssistantService aiAssistantService;
    private final AiMemoryService aiMemoryService;
    private final Executor aiChatExecutor;

    public AiAssistantController(AiAssistantService aiAssistantService,
                                 AiMemoryService aiMemoryService,
                                 @Qualifier("aiChatExecutor") Executor aiChatExecutor) {
        this.aiAssistantService = aiAssistantService;
        this.aiMemoryService = aiMemoryService;
        this.aiChatExecutor = aiChatExecutor;
    }

    @OperationLog("AI助手-普通问答")
    @PostMapping("/chat")
    public ApiResponse<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        return ApiResponse.success(aiAssistantService.chat(request));
    }

    /**
     * SSE 流式对话端点。
     * <p>
     * 前端通过 EventSource 连接此端点，后端在后台线程中处理 AI 对话，
     * 实时推送工具调用进度（thinking / tool_start / tool_result / done / error）。
     * 相比普通 POST /chat，用户可以看到 AI 当前在查什么、命中多少条数据、已用时多少。
     * </p>
     * <p>
     * 参数通过 query string 传递（EventSource 不支持 POST body）。
     * </p>
     */
    @OperationLog("AI助手-SSE流式问答")
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam String message,
                                 @RequestParam(required = false) String conversationId,
                                 @RequestParam(required = false) String pageContext) {
        SseEmitter emitter = new SseEmitter(120_000L); // 2 分钟超时
        AiChatRequest request = new AiChatRequest(message, conversationId, pageContext);
        // 捕获当前 HTTP 线程的登录标识和权限列表，异步线程中 Sa-Token 上下文不可用
        String loginId = String.valueOf(StpUtil.getLoginIdDefaultNull());
        List<String> permissions = StpUtil.getPermissionList();
        // 捕获 Session 属性（roleCode / customerId / username / userCode），用于数据权限隔离
        String roleCode = String.valueOf(StpUtil.getSession().get("roleCode", ""));
        String customerId = String.valueOf(StpUtil.getSession().get("customerId", ""));
        String username = String.valueOf(StpUtil.getSession().get("username", ""));
        String userCode = String.valueOf(StpUtil.getSession().get("userCode", ""));

        emitter.onTimeout(() -> log.info("SSE 连接超时，conversationId={}", conversationId));
        emitter.onError(throwable -> log.warn("SSE 连接异常，conversationId={}", conversationId, throwable));
        emitter.onCompletion(() -> log.info("SSE 连接正常关闭，conversationId={}", conversationId));

        aiChatExecutor.execute(() -> aiAssistantService.chatStream(request, emitter, loginId, permissions, roleCode, customerId, username, userCode));
        return emitter;
    }

    @OperationLog("AI助手-日志分析")
    @PostMapping("/logs/analyze")
    public ApiResponse<AiLogAnalysisResponse> analyzeLogs(@RequestBody AiLogAnalyzeRequest request) {
        return ApiResponse.success(aiAssistantService.analyzeLogs(request));
    }

    @OperationLog("AI助手-查询会话列表")
    @GetMapping("/conversations")
    public ApiResponse<List<AiConversationVO>> conversations() {
        return ApiResponse.success(aiAssistantService.conversations());
    }

    @OperationLog("AI助手-查询会话详情")
    @GetMapping("/conversations/{id}")
    public ApiResponse<AiConversationVO> conversation(@PathVariable String id) {
        return ApiResponse.success(aiAssistantService.conversation(id));
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
}
