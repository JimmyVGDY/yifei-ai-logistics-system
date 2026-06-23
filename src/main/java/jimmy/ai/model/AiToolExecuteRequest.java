package jimmy.ai.model;

import java.util.Map;

/**
 * AI 工具执行请求 —— Python AI 服务调用 Java 内部工具执行端点时传入的请求体。
 */
public record AiToolExecuteRequest(String toolName, Map<String, Object> arguments) {
}
