package jimmy.ai.model;

import java.util.Map;

/**
 * AI 工具定义 —— 描述一个可供 AI 模型调用的工具，包含 OpenAI function-calling 格式的参数 Schema。
 */
public record AiToolDefinition(String name, String description, Map<String, Object> parameters) {
}
