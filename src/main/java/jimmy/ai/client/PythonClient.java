package jimmy.ai.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * Python AI 服务 HTTP 客户端。
 * <p>
 * 所有请求走 127.0.0.1:8001，不经过公网。
 * OTel {@code traceparent} 由 Spring RestClient 自动传播。
 */
@Component
public class PythonClient {

    private final RestClient restClient;
    private final boolean enabled;

    public PythonClient(@Value("${app.ai.python.base-url:http://127.0.0.1:8001}") String baseUrl,
                        @Value("${app.ai.python.enabled:false}") boolean enabled,
                        @Value("${app.ai.python.timeout-seconds:180}") int timeoutSeconds,
                        RestClient.Builder builder) {
        this.enabled = enabled;
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ── 健康检查 ──

    public Map<String, Object> health() {
        return restClient.get()
                .uri("/health")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    // ── 工具注册表 ──

    public List<Map<String, Object>> fetchToolRegistry() {
        return restClient.get()
                .uri("/internal/tools/registry")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public Map<String, Object> executeTool(String toolName, Map<String, Object> arguments) {
        return restClient.post()
                .uri("/internal/tool/execute")
                .body(Map.of("toolName", toolName, "arguments", arguments))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    // ── 记忆 ──

    public Map<String, Object> recallMemories(String userId, String question, int limit) {
        return restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/memory/recall")
                        .queryParam("userId", userId)
                        .queryParam("question", question)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public Map<String, Object> syncMemory(String action, long memoryId, String content) {
        return restClient.post()
                .uri("/internal/memory/sync")
                .body(Map.of("action", action, "memoryId", memoryId, "content", content))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public Map<String, Object> extractMemories(String conversationText) {
        return restClient.post()
                .uri("/internal/memory/extract")
                .body(Map.of("conversationText", conversationText))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    // ── RAG ──

    public Map<String, Object> searchKnowledge(String query, int topK) {
        return restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/rag/search")
                        .queryParam("query", query)
                        .queryParam("topK", topK)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public Map<String, Object> reindexDocuments() {
        return restClient.post()
                .uri("/internal/rag/reindex")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    // ── 异步任务 ──

    public Map<String, Object> submitTask(String taskType, Map<String, Object> params) {
        return restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/tasks/submit")
                        .queryParam("taskType", taskType)
                        .build())
                .body(params)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public Map<String, Object> getTaskStatus(String taskId) {
        return restClient.get()
                .uri("/internal/tasks/{taskId}/status", taskId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public Map<String, Object> cancelTask(String taskId) {
        return restClient.post()
                .uri("/internal/tasks/{taskId}/cancel", taskId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    // ── SSE 流式对话 ──

    /**
     * 建立到 Python 的 SSE 连接，返回 RestClient 的响应体用于流式读取。
     * 调用方负责逐行读取并透传给前端 SseEmitter。
     */
    public RestClient.ResponseSpec chatStream(Map<String, Object> request) {
        return restClient.post()
                .uri("/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(request)
                .retrieve();
    }
}
