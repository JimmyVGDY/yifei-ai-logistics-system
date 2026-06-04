package jimmy.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import io.micrometer.observation.ObservationRegistry;

import java.util.Optional;

/**
 * Spring AI 模型网关 —— 隔离模型供应商配置、调用和异常兜底。
 */
@Slf4j
@Component
public class AiModelGateway {

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final AiRuntimePropertiesProvider runtimePropertiesProvider;
    private final AiSensitiveDataMasker masker;

    public AiModelGateway(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                          AiRuntimePropertiesProvider runtimePropertiesProvider,
                          AiSensitiveDataMasker masker) {
        this.chatClientBuilderProvider = chatClientBuilderProvider;
        this.runtimePropertiesProvider = runtimePropertiesProvider;
        this.masker = masker;
    }

    public boolean configured() {
        return runtimePropertiesProvider.current().configured();
    }

    public Optional<String> chat(String systemPrompt, String userPrompt) {
        AiRuntimeProperties properties = runtimePropertiesProvider.current();
        if (!properties.configured()) {
            return Optional.empty();
        }
        try {
            String answer = chatClient(properties)
                    .prompt()
                    .system(masker.mask(systemPrompt))
                    .user(masker.mask(userPrompt))
                    .call()
                    .content();
            return Optional.ofNullable(masker.mask(answer));
        } catch (RuntimeException exception) {
            log.warn("Spring AI 模型调用失败，已返回本地兜底，reason={}", exception.getMessage());
            return Optional.empty();
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
