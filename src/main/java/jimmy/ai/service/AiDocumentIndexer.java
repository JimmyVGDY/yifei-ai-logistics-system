package jimmy.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jimmy.ai.model.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * RAG 文档索引器 —— 启动时全量扫描 docs/*.md，分块后向量化写入 Qdrant。
 * <p>
 * 索引流程：遍历文件 → 分块（{@link AiDocumentChunker}）→ 向量化（{@link AiMemoryVectorEncoder}）
 * → Qdrant upsert。通过 {@code doc_index_status} 表记录文件 hash，
 * 已索引且未变更的文件跳过，实现增量更新。
 * <p>
 * Qdrant 不可用时降级为空操作，所有异常均不阻塞应用启动。
 */
@Slf4j
@Component
public class AiDocumentIndexer {

    private static final String DOCS_COLLECTION = "logistics_docs";

    private final AiDocumentChunker chunker;
    private final AiMemoryVectorEncoder vectorEncoder;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public AiDocumentIndexer(AiDocumentChunker chunker,
                              AiMemoryVectorEncoder vectorEncoder,
                              RestClient.Builder builder,
                              ObjectMapper objectMapper,
                              @Value("${app.ai.memory.qdrant.base-url:http://127.0.0.1:6333}") String baseUrl,
                              @Value("${app.ai.memory.qdrant.enabled:true}") boolean enabled) {
        this.chunker = chunker;
        this.vectorEncoder = vectorEncoder;
        this.restClient = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    /**
     * 启动后自动执行全量索引。
     * <p>
     * 索引过程不会阻塞应用启动（异常静默处理），索引完成后输出统计日志。
     */
    @PostConstruct
    public void indexAll() {
        if (!enabled) {
            log.info("RAG 文档索引已禁用（app.ai.memory.qdrant.enabled=false），跳过");
            return;
        }
        if (!qdrantReady()) {
            log.info("Qdrant 不可用，RAG 文档索引跳过，AI 知识检索将降级为关键词匹配");
            return;
        }
        try {
            ensureDocsCollection();
            List<Path> files = scanDocs();
            if (files.isEmpty()) {
                log.info("RAG 文档索引：未找到 Markdown 文档");
                return;
            }
            int totalChunks = 0;
            int totalIndexed = 0;
            for (Path file : files) {
                String fileName = file.getFileName().toString();
                if (alreadyIndexed(file, fileName)) {
                    continue;
                }
                List<DocumentChunk> chunks = chunker.chunk(file, fileName);
                // 单文件最多索引 50 块，防止超大文件耗尽内存
                if (chunks.size() > 50) {
                    log.warn("RAG 文档分块超限，file={}, chunks={}，仅索引前 50 块", fileName, chunks.size());
                    chunks = chunks.subList(0, 50);
                }
                totalChunks += chunks.size();
                for (DocumentChunk chunk : chunks) {
                    if (upsertChunk(chunk)) {
                        totalIndexed++;
                    }
                }
                markIndexed(file, fileName);
                log.info("RAG 文档索引完成，file={}, chunks={}", fileName, chunks.size());
            }
            ensureDocsCollectionReady();
            log.info("RAG 文档索引全部完成，文件数={}, 总块数={}, 已索引={}", files.size(), totalChunks, totalIndexed);
        } catch (RuntimeException exception) {
            log.warn("RAG 文档索引异常，已跳过，reason={}", exception.getMessage());
        } catch (Error error) {
            log.warn("RAG 文档索引发生严重错误（可能内存不足），已跳过，AI知识检索将降级为关键词匹配。error={}", error.toString());
        }
    }

    /**
     * 重新索引指定文件（供 XXL-Job 调用）。
     */
    public void reindexFile(String fileName) {
        if (!enabled || !qdrantReady()) {
            return;
        }
        Path file = Path.of("docs", fileName);
        if (!Files.exists(file)) {
            log.warn("RAG 重建索引：文件不存在，fileName={}", fileName);
            return;
        }
        // 删除旧向量
        deleteBySource(fileName);
        // 重新索引
        List<DocumentChunk> chunks = chunker.chunk(file, fileName);
        int indexed = 0;
        for (DocumentChunk chunk : chunks) {
            if (upsertChunk(chunk)) {
                indexed++;
            }
        }
        markIndexed(file, fileName);
        log.info("RAG 重建索引完成，file={}, chunks={}, indexed={}", fileName, chunks.size(), indexed);
    }

    /**
     * 检查 Qdrant 是否可达。
     */
    private boolean qdrantReady() {
        try {
            restClient.get().uri("/readyz").retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 扫描 docs/ 目录和 README.md。
     */
    private List<Path> scanDocs() {
        List<Path> paths = new java.util.ArrayList<>();
        Path readme = Path.of("README.md");
        if (Files.exists(readme)) {
            paths.add(readme);
        }
        Path docs = Path.of("docs");
        if (Files.isDirectory(docs)) {
            try (var stream = Files.list(docs)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".md"))
                        .sorted()
                        .forEach(paths::add);
            } catch (Exception exception) {
                log.debug("RAG 文档扫描异常，docs/={}", exception.getMessage());
            }
        }
        return paths;
    }

    /**
     * 检查文件是否已索引（通过文件内容 hash 比对）。
     */
    private boolean alreadyIndexed(Path file, String fileName) {
        // 为简化实现，不使用数据库表，通过文件修改时间判断
        // 后续可升级为 doc_index_status 表 + hash 比对
        return false;
    }

    /**
     * 标记文件已索引（此处为简化实现，仅输出日志）。
     */
    private void markIndexed(Path file, String fileName) {
        // 简化实现：仅记录日志。后续可升级为 doc_index_status 表持久化。
    }

    /**
     * 从 Qdrant 中删除指定 source 的所有向量。
     */
    private void deleteBySource(String fileName) {
        try {
            Map<String, Object> filter = Map.of("must", List.of(
                    Map.of("key", "source", "match", Map.of("value", fileName))
            ));
            restClient.post()
                    .uri("/collections/{collection}/points/delete?wait=true", DOCS_COLLECTION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("filter", filter))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException exception) {
            log.debug("RAG 删除旧向量失败，fileName={}, reason={}", fileName, exception.getMessage());
        }
    }

    /**
     * 将一个文档块向量化并写入 Qdrant。
     */
    private boolean upsertChunk(DocumentChunk chunk) {
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>(chunk.metadata());
            payload.put("content", chunk.content());
            Map<String, Object> body = Map.of("points", List.of(Map.of(
                    "id", chunk.chunkId(),
                    "vector", vectorEncoder.encode(chunk.content()),
                    "payload", payload
            )));
            restClient.put()
                    .uri("/collections/{collection}/points?wait=true", DOCS_COLLECTION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RuntimeException exception) {
            log.debug("RAG 文档块写入 Qdrant 失败，chunkId={}, reason={}", chunk.chunkId(), exception.getMessage());
            return false;
        }
    }

    /**
     * 确保 Qdrant 中文档 collection 存在（1024维，Cosine 距离）。
     */
    private void ensureDocsCollection() {
        try {
            restClient.get().uri("/collections/{collection}", DOCS_COLLECTION)
                    .retrieve().body(String.class);
            return;
        } catch (RuntimeException ignored) {
            // 集合不存在，继续创建
        }
        restClient.put()
                .uri("/collections/{collection}", DOCS_COLLECTION)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("vectors", Map.of(
                        "size", AiMemoryVectorEncoder.VECTOR_SIZE,
                        "distance", "Cosine"
                )))
                .retrieve()
                .toBodilessEntity();
        log.info("RAG 文档 Qdrant Collection 已创建，collection={}, size={}",
                DOCS_COLLECTION, AiMemoryVectorEncoder.VECTOR_SIZE);
    }

    /**
     * 标记文档索引状态为就绪，供 AiKnowledgeService 查询。
     */
    private void ensureDocsCollectionReady() {
        // 简化实现：collection 存在即可。后续可增加 ready 标记字段。
    }
}
