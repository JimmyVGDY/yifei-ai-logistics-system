package jimmy.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Qdrant 记忆向量客户端。
 * <p>
 * 所有异常都在客户端内降级，避免向量库不可用影响物流主流程和 AI 只读问答。
 */
@Slf4j
@Component
public class AiQdrantMemoryClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiMemoryVectorEncoder vectorEncoder;
    private final String collectionName;
    private final boolean enabled;

    public AiQdrantMemoryClient(RestClient.Builder builder,
                                ObjectMapper objectMapper,
                                AiMemoryVectorEncoder vectorEncoder,
                                @Value("${app.ai.memory.qdrant.base-url:http://127.0.0.1:6333}") String baseUrl,
                                @Value("${app.ai.memory.qdrant.collection:logistics_ai_user_memory}") String collectionName,
                                @Value("${app.ai.memory.qdrant.enabled:true}") boolean enabled) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.vectorEncoder = vectorEncoder;
        this.collectionName = collectionName;
        this.enabled = enabled;
    }

    public boolean available() {
        if (!enabled) {
            return false;
        }
        try {
            restClient.get().uri("/readyz").retrieve().toBodilessEntity();
            ensureCollection();
            return true;
        } catch (RestClientException exception) {
            log.debug("Qdrant 长期记忆不可用，reason={}", exception.getMessage());
            return false;
        }
    }

    public boolean upsert(String pointId, String text, Map<String, Object> payload) {
        if (!StringUtils.hasText(pointId) || !enabled) {
            return false;
        }
        try {
            ensureCollection();
            Map<String, Object> body = Map.of("points", List.of(Map.of(
                    "id", pointId,
                    "vector", vectorEncoder.encode(text),
                    "payload", payload
            )));
            restClient.put()
                    .uri("/collections/{collection}/points?wait=true", collectionName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException exception) {
            log.warn("Qdrant 长期记忆写入失败，pointId={}, reason={}", pointId, exception.getMessage());
            return false;
        }
    }

    public List<Long> search(String userId, String userCode, String text, int limit) {
        if (!enabled || !StringUtils.hasText(userId)) {
            return List.of();
        }
        try {
            ensureCollection();
            Map<String, Object> filter = Map.of("must", List.of(
                    Map.of("key", "userId", "match", Map.of("value", userId)),
                    Map.of("key", "userCode", "match", Map.of("value", userCode == null ? "" : userCode)),
                    Map.of("key", "enabled", "match", Map.of("value", true))
            ));
            Map<String, Object> body = Map.of(
                    "vector", vectorEncoder.encode(text),
                    "limit", Math.max(1, Math.min(limit, 10)),
                    "with_payload", true,
                    "filter", filter
            );
            String json = restClient.post()
                    .uri("/collections/{collection}/points/search", collectionName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseMemoryIds(json);
        } catch (RestClientException exception) {
            log.debug("Qdrant 长期记忆召回失败，userId={}, reason={}", userId, exception.getMessage());
            return List.of();
        }
    }

    public void delete(String pointId) {
        if (!enabled || !StringUtils.hasText(pointId)) {
            return;
        }
        try {
            Map<String, Object> body = Map.of("points", List.of(pointId));
            restClient.post()
                    .uri("/collections/{collection}/points/delete?wait=true", collectionName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("Qdrant 长期记忆删除失败，pointId={}, reason={}", pointId, exception.getMessage());
        }
    }

    private void ensureCollection() {
        try {
            restClient.get()
                    .uri("/collections/{collection}", collectionName)
                    .retrieve()
                    .toBodilessEntity();
            return;
        } catch (RestClientException ignored) {
            // 集合不存在时继续创建；创建失败由外层降级处理。
        }
        Map<String, Object> body = Map.of("vectors", Map.of(
                "size", AiMemoryVectorEncoder.VECTOR_SIZE,
                "distance", "Cosine"
        ));
        restClient.put()
                .uri("/collections/{collection}", collectionName)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private List<Long> parseMemoryIds(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode result = objectMapper.readTree(json).path("result");
            List<Long> ids = new ArrayList<>();
            for (JsonNode item : result) {
                JsonNode memoryId = item.path("payload").path("memoryId");
                if (memoryId.canConvertToLong()) {
                    ids.add(memoryId.asLong());
                }
            }
            return ids;
        } catch (Exception exception) {
            return List.of();
        }
    }
}
