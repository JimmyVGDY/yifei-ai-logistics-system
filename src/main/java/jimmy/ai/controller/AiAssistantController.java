package jimmy.ai.controller;

import jimmy.ai.model.AiChatRequest;
import jimmy.ai.model.AiChatResponse;
import jimmy.ai.model.AiConversationVO;
import jimmy.ai.model.AiLogAnalysisResponse;
import jimmy.ai.model.AiLogAnalyzeRequest;
import jimmy.ai.service.AiAssistantService;
import jimmy.logistics.annotation.OperationLog;
import jimmy.model.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 助手接口 —— 仅开放只读问答、日志排障和当前用户会话查询。
 */
@RestController
@RequestMapping("/ai")
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;

    public AiAssistantController(AiAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
    }

    @OperationLog("AI助手-普通问答")
    @PostMapping("/chat")
    public ApiResponse<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        return ApiResponse.success(aiAssistantService.chat(request));
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
}
