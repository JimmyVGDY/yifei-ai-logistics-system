package jimmy.ai.model;

/**
 * AI Prompt 模板编码集中定义。
 * <p>
 * 模板编码会写入 Token 用量和审计日志，后续排查模型行为时可以定位到具体模板版本。
 */
public final class AiPromptTemplateCodes {

    private AiPromptTemplateCodes() {
    }

    public static final String AI_CHAT_SYSTEM = "AI_CHAT_SYSTEM";
    public static final String AI_CHAT_USER = "AI_CHAT_USER";
    public static final String AI_SQL_GENERATE_SYSTEM = "AI_SQL_GENERATE_SYSTEM";
    public static final String AI_SQL_GENERATE_USER = "AI_SQL_GENERATE_USER";
    public static final String AI_SQL_SELF_CHECK_SYSTEM = "AI_SQL_SELF_CHECK_SYSTEM";
    public static final String AI_SQL_SELF_CHECK_USER = "AI_SQL_SELF_CHECK_USER";
    public static final String AI_SQL_REPAIR_SYSTEM = "AI_SQL_REPAIR_SYSTEM";
    public static final String AI_SQL_REPAIR_USER = "AI_SQL_REPAIR_USER";
    public static final String AI_FILE_ANALYSIS_SYSTEM = "AI_FILE_ANALYSIS_SYSTEM";
    public static final String AI_FILE_ANALYSIS_USER = "AI_FILE_ANALYSIS_USER";
    public static final String AI_MEMORY_EXTRACT_SYSTEM = "AI_MEMORY_EXTRACT_SYSTEM";
    public static final String AI_MEMORY_EXTRACT_USER = "AI_MEMORY_EXTRACT_USER";
}
