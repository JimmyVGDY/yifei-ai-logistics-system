package jimmy.ai.model;

/**
 * 模型调用关联的 Prompt 模板元信息。
 * <p>
 * 一次调用通常包含 system/user 两段模板，这里记录主模板编码和版本，方便 Token 统计和审计追踪。
 */
public record PromptTemplateMetadata(String templateCode, Integer templateVersion) {

    public static PromptTemplateMetadata of(PromptRenderResult systemPrompt, PromptRenderResult userPrompt) {
        if (systemPrompt != null) {
            return new PromptTemplateMetadata(systemPrompt.templateCode(), systemPrompt.templateVersion());
        }
        if (userPrompt != null) {
            return new PromptTemplateMetadata(userPrompt.templateCode(), userPrompt.templateVersion());
        }
        return none();
    }

    public static PromptTemplateMetadata none() {
        return new PromptTemplateMetadata(null, null);
    }
}
