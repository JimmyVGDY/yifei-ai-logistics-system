package jimmy.ai.model;

/**
 * Prompt 模板渲染结果。
 *
 * @param templateCode    模板编码
 * @param templateVersion 模板版本；代码兜底模板固定为 0
 * @param content         渲染后的提示词内容
 * @param outputSchema    期望输出结构说明
 * @param modelPurpose    模型调用用途
 * @param fallback        是否使用了代码兜底模板
 */
public record PromptRenderResult(String templateCode,
                                 int templateVersion,
                                 String content,
                                 String outputSchema,
                                 String modelPurpose,
                                 boolean fallback) {

    public static PromptRenderResult fallback(String templateCode, String content, String modelPurpose) {
        return new PromptRenderResult(templateCode, 0, content, null, modelPurpose, true);
    }
}
