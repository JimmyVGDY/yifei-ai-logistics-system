package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.util.SseChatContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import io.micrometer.observation.ObservationRegistry;

import java.util.Optional;
import jimmy.ai.model.AiRuntimeProperties;
import jimmy.ai.model.PromptRenderResult;
import jimmy.ai.model.PromptTemplateMetadata;

/**
 * Spring AI 模型网关 —— 隔离模型供应商配置、调用、Token 追踪和异常兜底。
 * <p>
 * 每次模型调用自动记录 Token 消耗到 {@code ai_token_usage} 表，
 * 写入失败不影响主流程。
 */
@Slf4j
@Component
public class AiModelGateway {

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final AiRuntimePropertiesProvider runtimePropertiesProvider;
    private final AiSensitiveDataMasker masker;
    private final AiTokenUsageService tokenUsageService;

    public AiModelGateway(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                          AiRuntimePropertiesProvider runtimePropertiesProvider,
                          AiSensitiveDataMasker masker,
                          AiTokenUsageService tokenUsageService) {
        this.chatClientBuilderProvider = chatClientBuilderProvider;
        this.runtimePropertiesProvider = runtimePropertiesProvider;
        this.masker = masker;
        this.tokenUsageService = tokenUsageService;
    }

    public boolean configured() {
        return runtimePropertiesProvider.current().configured();
    }

    public Optional<String> chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, "chat", null, new Object[0]);
    }

    public Optional<String> chat(String systemPrompt, String userPrompt, String purpose) {
        return chat(systemPrompt, userPrompt, purpose, null, new Object[0]);
    }

    /**
     * 带 Spring AI Tool Calling 的模型调用入口。
     * <p>
     * tools 只接收后端已经封装好的只读工具 Bean。模型只能选择调用工具，
     * 真正的权限、数据范围、SQL 安全和脱敏仍由工具内部的后端服务兜底。
     * <p>
     * 每次调用自动记录 Token 消耗（prompt/completion/total）和调用耗时，
     * 异步写入 ai_token_usage 表，写入失败不影响主流程。
     *
     * @param systemPrompt   系统提示词
     * @param userPrompt     用户提示词
     * @param purpose        调用用途（chat / sql_generate / sql_self_check / sql_repair / memory_extract）
     * @param conversationId 关联的 AI 会话 ID（可选）
     * @param tools          工具 Bean 数组
     */
    public Optional<String> chat(String systemPrompt, String userPrompt, String purpose,
                                  String conversationId, Object... tools) {
        return chat(systemPrompt, userPrompt, purpose, conversationId, PromptTemplateMetadata.none(), tools);
    }

    /**
     * 使用已渲染 Prompt 模板调用模型，并把模板编码和版本写入 Token 用量。
     */
    public Optional<String> chat(PromptRenderResult systemPrompt,
                                 PromptRenderResult userPrompt,
                                 String purpose,
                                 String conversationId,
                                 Object... tools) {
        return chat(
                systemPrompt == null ? "" : systemPrompt.content(),
                userPrompt == null ? "" : userPrompt.content(),
                purpose,
                conversationId,
                PromptTemplateMetadata.of(systemPrompt, userPrompt),
                tools
        );
    }

    private Optional<String> chat(String systemPrompt, String userPrompt, String purpose,
                                  String conversationId, PromptTemplateMetadata promptMetadata, Object... tools) {
        AiRuntimeProperties properties = runtimePropertiesProvider.current();
        if (!properties.configured()) {
            return Optional.empty();
        }
        long start = System.currentTimeMillis();
        try {
            ChatClient.ChatClientRequestSpec requestSpec = chatClient(properties)
                    .prompt()
                    .system(masker.mask(systemPrompt))
                    .user(masker.mask(userPrompt));
            if (tools != null && tools.length > 0) {
                requestSpec = requestSpec.tools(tools);
            }
            ChatResponse chatResponse = requestSpec.call().chatResponse();
            String answer = chatResponse.getResult().getOutput().getText();
            long durationMs = System.currentTimeMillis() - start;
            recordUsage(properties, purpose, conversationId, promptMetadata, chatResponse, durationMs);
            return Optional.ofNullable(masker.mask(answer));
        } catch (RuntimeException exception) {
            long durationMs = System.currentTimeMillis() - start;
            log.warn("Spring AI 模型调用失败，已返回本地兜底，reason={}", exception.getMessage());
            recordFailedUsage(properties, purpose, conversationId, promptMetadata, durationMs);
            return Optional.empty();
        }
    }

    /**
     * 记录成功调用的 Token 用量。
     */
    private void recordUsage(AiRuntimeProperties properties, String purpose, String conversationId,
                              PromptTemplateMetadata promptMetadata, ChatResponse chatResponse, long durationMs) {
        try {
            Usage usage = chatResponse.getMetadata() != null ? chatResponse.getMetadata().getUsage() : null;
            int promptTokens = usage != null ? (int) usage.getPromptTokens() : 0;
            int completionTokens = usage != null ? (int) usage.getCompletionTokens() : 0;
            tokenUsageService.record(
                    properties.model(), purpose, promptMetadata.templateCode(), promptMetadata.templateVersion(),
                    promptTokens, completionTokens,
                    currentUserId(), currentUserCode(), conversationId,
                    properties.baseUrl(), durationMs);
        } catch (RuntimeException exception) {
            log.debug("AI Token 用量记录异常，已跳过，reason={}", exception.getMessage());
        }
    }

    /**
     * 记录失败调用的基本信息（Token 数为 0，仅记录调用事实和耗时）。
     */
    private void recordFailedUsage(AiRuntimeProperties properties, String purpose, String conversationId,
                                   PromptTemplateMetadata promptMetadata, long durationMs) {
        try {
            tokenUsageService.record(
                    properties.model(), purpose, promptMetadata.templateCode(), promptMetadata.templateVersion(), 0, 0,
                    currentUserId(), currentUserCode(), conversationId,
                    properties.baseUrl(), durationMs);
        } catch (RuntimeException exception) {
            log.debug("AI Token 用量失败记录异常，已跳过，reason={}", exception.getMessage());
        }
    }

    /**
     * 获取当前登录用户 ID，SSE 异步线程优先。
     */
    private String currentUserId() {
        String sseLoginId = SseChatContext.getLoginId();
        if (sseLoginId != null && !sseLoginId.isBlank() && !"null".equals(sseLoginId)) {
            return sseLoginId;
        }
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId == null ? "anonymous" : String.valueOf(loginId);
    }

    /**
     * 获取当前登录用户业务编号，SSE 异步线程优先。
     */
    private String currentUserCode() {
        String sseUserCode = SseChatContext.getUserCode();
        if (sseUserCode != null && !sseUserCode.isBlank() && !"null".equalsIgnoreCase(sseUserCode)) {
            return sseUserCode;
        }
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            return loginId == null ? "" : String.valueOf(StpUtil.getSessionByLoginId(loginId).get("userCode", ""));
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private ChatClient chatClient(AiRuntimeProperties properties) {
        if ("nacos:spring-ai.yml".equals(properties.source())) {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .apiKey(properties.apiKey())
                    .baseUrl(properties.baseUrl())
                    .build();
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(properties.model())
                    .build();
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(options)
                    .toolCallingManager(ToolCallingManager.builder().build())
                    .retryTemplate(RetryTemplate.defaultInstance())
                    .observationRegistry(ObservationRegistry.NOOP)
                    .build();
            return ChatClient.create(chatModel);
        }
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) {
            throw new IllegalStateException("Spring AI ChatClient 未初始化");
        }
        return builder.build();
    }
}
