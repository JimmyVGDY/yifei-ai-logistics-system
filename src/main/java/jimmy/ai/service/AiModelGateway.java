package jimmy.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Spring AI 模型网关 —— 隔离模型供应商配置、调用和异常兜底。
 */
@Slf4j
@Component
public class AiModelGateway {

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final String apiKey;
    private final AiSensitiveDataMasker masker;

    public AiModelGateway(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                          @Value("${spring.ai.openai.api-key:}") String apiKey,
                          AiSensitiveDataMasker masker) {
        this.chatClientBuilderProvider = chatClientBuilderProvider;
        this.apiKey = apiKey;
        this.masker = masker;
    }

    public boolean configured() {
        return StringUtils.hasText(apiKey) && !"missing".equalsIgnoreCase(apiKey);
    }

    public Optional<String> chat(String systemPrompt, String userPrompt) {
        if (!configured()) {
            return Optional.empty();
        }
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) {
            return Optional.empty();
        }
        try {
            String answer = builder.build()
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
}
