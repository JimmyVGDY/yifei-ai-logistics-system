package jimmy.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jimmy.ai.model.AiCitation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 文档知识检索 —— 语义搜索（Qdrant）为主，关键词匹配（Index 扫描）为降级。
 * <p>
 * 升级后优先使用 Qdrant 向量语义搜索（理解同义词和意图），
 * Qdrant 不可用或未索引时自动降级为原有关键词匹配，不影响应用正常运行。
 */
@Slf4j
@Service
public class AiKnowledgeService {

    private static final int SNIPPET_LENGTH = 420;
    private static final double MIN_SCORE = 0.6;

    private final AiMemoryVectorEncoder vectorEncoder;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean vectorEnabled;
    private final String docsCollection;

    public AiKnowledgeService(AiMemoryVectorEncoder vectorEncoder,
                               RestClient.Builder builder,
                               ObjectMapper objectMapper,
                               @org.springframework.beans.factory.annotation.Value("${app.ai.rag.qdrant.base-url:${app.ai.memory.qdrant.base-url:http://127.0.0.1:6333}}") String baseUrl,
                               @org.springframework.beans.factory.annotation.Value("${app.ai.rag.enabled:${app.ai.memory.qdrant.enabled:true}}") boolean vectorEnabled,
                               @org.springframework.beans.factory.annotation.Value("${app.ai.rag.qdrant.collection:logistics_docs}") String docsCollection) {
        this.vectorEncoder = vectorEncoder;
        this.restClient = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.vectorEnabled = vectorEnabled;
        this.docsCollection = docsCollection;
    }

    /**
     * 检索与用户问题相关的文档引用。
     * <p>
     * 优先 Qdrant 语义搜索（理解同义词和意图），不可用时降级为关键词匹配。
     *
     * @param keyword 用户问题文本
     * @return 相关文档引用列表（最多 5 条）
     */
    public List<AiCitation> search(String keyword) {
        String normalized = StringUtils.hasText(keyword) ? keyword.trim() : "";
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }

        // 1. 先做向量语义搜索，再叠加关键词精确命中，减少泛文档误引用。
        List<AiCitation> vectorResults = vectorSearch(normalized);
        List<AiCitation> keywordResults = keywordFallback(normalized, false);
        List<AiCitation> merged = mergeCitations(vectorResults, keywordResults);
        if (!merged.isEmpty()) {
            log.debug("AI 知识检索命中混合检索结果，vectorCount={}, keywordCount={}, mergedCount={}",
                    vectorResults.size(), keywordResults.size(), merged.size());
            return merged;
        }

        // 2. 仍无结果时保留原 README 兜底，避免用户询问系统概览时完全无参考资料。
        log.debug("AI 知识检索未命中精确结果，使用 README 兜底，keyword={}", normalized);
        return keywordFallback(normalized, true);
    }

    /**
     * Qdrant 语义搜索：向量化用户问题 → 在 logistics_docs 集合中搜索 Top 5。
     */
    private List<AiCitation> vectorSearch(String question) {
        if (!vectorEnabled) {
            return List.of();
        }
        if (!qdrantReady()) {
            return List.of();
        }
        try {
            Map<String, Object> body = Map.of(
                    "vector", vectorEncoder.encode(question),
                    "limit", 5,
                    "with_payload", true
            );
            String json = restClient.post()
                    .uri("/collections/{collection}/points/search", docsCollection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseResults(json);
        } catch (RuntimeException exception) {
            log.debug("Qdrant 语义搜索失败，降级关键词匹配，reason={}", exception.getMessage());
            return List.of();
        }
    }

    /**
     * 解析 Qdrant 搜索结果，提取 score ≥ 0.6 的文档块。
     */
    private List<AiCitation> parseResults(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        List<AiCitation> citations = new ArrayList<>();
        try {
            JsonNode results = objectMapper.readTree(json).path("result");
            for (JsonNode item : results) {
                double score = item.path("score").asDouble(0);
                if (score < MIN_SCORE) {
                    continue;
                }
                JsonNode payload = item.path("payload");
                String source = payload.path("source").asText("");
                String section = payload.path("section").asText("");
                String content = payload.path("content").asText("");
                String title = source + (StringUtils.hasText(section) ? " → " + section : "");
                citations.add(new AiCitation("DOC", title, source, snippet(content, 0)));
                if (citations.size() >= 5) {
                    break;
                }
            }
        } catch (Exception exception) {
            log.debug("解析 Qdrant 搜索结果失败，reason={}", exception.getMessage());
        }
        return citations;
    }

    /**
     * 关键词降级匹配：原有关键词 indexOf 逻辑。
     */
    private List<AiCitation> keywordFallback(String keyword, boolean includeDefaultReadme) {
        String normalized = keyword.toLowerCase(Locale.ROOT);
        List<AiCitation> citations = new ArrayList<>();
        for (Path path : candidateDocuments()) {
            if (citations.size() >= 5) {
                break;
            }
            try {
                if (!Files.exists(path)) {
                    continue;
                }
                String content = Files.readString(path, StandardCharsets.UTF_8);
                String lowerContent = content.toLowerCase(Locale.ROOT);
                int index = StringUtils.hasText(normalized) ? lowerContent.indexOf(normalized) : -1;
                if (index < 0 && (!includeDefaultReadme || !path.getFileName().toString().equalsIgnoreCase("README.md"))) {
                    continue;
                }
                citations.add(new AiCitation("DOC", path.getFileName().toString(), path.toString(), snippet(content, Math.max(index, 0))));
            } catch (IOException exception) {
                log.debug("AI 文档检索跳过不可读文件，path={}, reason={}", path, exception.getMessage());
            }
        }
        return citations;
    }

    private List<AiCitation> mergeCitations(List<AiCitation> first, List<AiCitation> second) {
        List<AiCitation> merged = new ArrayList<>();
        for (AiCitation citation : first) {
            addCitation(merged, citation);
        }
        for (AiCitation citation : second) {
            addCitation(merged, citation);
        }
        return merged.size() > 5 ? merged.subList(0, 5) : merged;
    }

    private void addCitation(List<AiCitation> merged, AiCitation citation) {
        boolean exists = merged.stream()
                .anyMatch(item -> item.reference().equals(citation.reference()) && item.title().equals(citation.title()));
        if (!exists) {
            merged.add(citation);
        }
    }

    private boolean qdrantReady() {
        try {
            restClient.get().uri("/readyz").retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private List<Path> candidateDocuments() {
        List<Path> paths = new ArrayList<>();
        paths.add(Path.of("README.md"));
        Path docs = Path.of("docs");
        if (Files.isDirectory(docs)) {
            try (var stream = Files.list(docs)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".md"))
                        .sorted()
                        .forEach(paths::add);
            } catch (IOException exception) {
                log.debug("AI 文档目录读取失败，reason={}", exception.getMessage());
            }
        }
        return paths;
    }

    private String snippet(String content, int index) {
        int start = Math.max(0, index - 120);
        int end = Math.min(content.length(), start + SNIPPET_LENGTH);
        return content.substring(start, end).replaceAll("\\s+", " ").trim();
    }
}
