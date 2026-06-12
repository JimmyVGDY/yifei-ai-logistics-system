package jimmy.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jimmy.ai.mapper.AiDocumentIndexMapper;
import jimmy.ai.model.AiDocumentIndexRecord;
import jimmy.ai.model.DocumentChunk;
import jimmy.common.id.CompactSnowflakeIdGenerator;
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
import java.util.stream.Collectors;

/**
 * RAG 文档索引器：启动时扫描 README 和 docs/*.md，将文档分块后写入 Qdrant。
 * <p>
 * MySQL 中的 ai_document_index 只保存文档路径、内容哈希和索引状态，用来判断是否需要重建向量；
 * Qdrant 保存用于语义检索的向量和脱敏 payload。Qdrant 或索引表不可用时只降级，不阻断应用启动。
 */
@Slf4j
@Component
public class AiDocumentIndexer {

    private static final String DOCS_COLLECTION = "logistics_docs";
    private static final int MAX_CHUNKS_PER_FILE = 50;

    private final AiDocumentChunker chunker;
    private final AiMemoryVectorEncoder vectorEncoder;
    private final AiDocumentIndexMapper documentIndexMapper;
    private final CompactSnowflakeIdGenerator idGenerator;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public AiDocumentIndexer(AiDocumentChunker chunker,
                             AiMemoryVectorEncoder vectorEncoder,
                             AiDocumentIndexMapper documentIndexMapper,
                             CompactSnowflakeIdGenerator idGenerator,
                             RestClient.Builder builder,
                             ObjectMapper objectMapper,
                             @Value("${app.ai.memory.qdrant.base-url:http://127.0.0.1:6333}") String baseUrl,
                             @Value("${app.ai.memory.qdrant.enabled:true}") boolean enabled) {
        this.chunker = chunker;
        this.vectorEncoder = vectorEncoder;
        this.documentIndexMapper = documentIndexMapper;
        this.idGenerator = idGenerator;
        this.restClient = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    /**
     * 应用启动后执行 RAG 文档索引。
     * <p>
     * 该过程是增量、可降级的：文档内容哈希未变化时跳过；任意单文件失败只记录状态，不影响后续文件和应用启动。
     */
    @PostConstruct
    public void indexAll() {
        if (!enabled) {
            log.info("RAG 文档索引已禁用，跳过启动索引");
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
            Map<String, Long> fileNameCounts = files.stream()
                    .collect(Collectors.groupingBy(path -> path.getFileName().toString(), Collectors.counting()));
            int totalChunks = 0;
            int totalIndexed = 0;
            int skipped = 0;
            for (Path file : files) {
                IndexOutcome outcome = indexOneFile(file, fileNameCounts.getOrDefault(file.getFileName().toString(), 0L) <= 1);
                totalChunks += outcome.chunkCount();
                totalIndexed += outcome.indexedCount();
                if (outcome.skipped()) {
                    skipped++;
                }
            }
            ensureDocsCollectionReady();
            log.info("RAG 文档索引完成，文件数={}, 跳过未变化={}, 总分块={}, 写入分块={}",
                    files.size(), skipped, totalChunks, totalIndexed);
        } catch (RuntimeException exception) {
            log.warn("RAG 文档索引异常，已跳过，reason={}", exception.getMessage());
        } catch (Error error) {
            log.warn("RAG 文档索引发生严重错误，已跳过，AI 知识检索将降级为关键词匹配，error={}", error.toString());
        }
    }

    /**
     * 重建指定文档索引，供定时任务或后续管理入口调用。
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
        try {
            ensureDocsCollection();
            indexOneFile(file, true);
        } catch (RuntimeException exception) {
            log.warn("RAG 重建索引失败，fileName={}, reason={}", fileName, exception.getMessage());
        }
    }

    private IndexOutcome indexOneFile(Path file, boolean legacySourceNameUnique) {
        String fileName = file.getFileName().toString();
        String sourcePath = normalizeSourcePath(file);
        String contentHash = hashFile(file);
        try {
            AiDocumentIndexRecord record = documentIndexMapper.selectBySourcePath(sourcePath);
            if (record != null && "SUCCESS".equals(record.status()) && contentHash.equals(record.contentHash())) {
                log.debug("RAG 文档未变化，跳过索引，sourcePath={}", sourcePath);
                return new IndexOutcome(0, 0, true);
            }
        } catch (RuntimeException exception) {
            log.debug("RAG 索引状态表暂不可用，将执行降级全量索引，sourcePath={}, reason={}", sourcePath, exception.getMessage());
        }

        try {
            // 新向量以 sourcePath 作为 source，避免同名文档互相覆盖；sourcePath 过滤用于后续精准清理。
            deleteByMetadata("sourcePath", sourcePath);
            deleteByMetadata("source", sourcePath);
            if (legacySourceNameUnique) {
                deleteByMetadata("source", fileName);
            }

            List<DocumentChunk> chunks = chunker.chunk(file, sourcePath);
            if (chunks.size() > MAX_CHUNKS_PER_FILE) {
                log.warn("RAG 文档分块超限，sourcePath={}, chunks={}，仅索引前 {} 块",
                        sourcePath, chunks.size(), MAX_CHUNKS_PER_FILE);
                chunks = chunks.subList(0, MAX_CHUNKS_PER_FILE);
            }
            int indexed = 0;
            for (DocumentChunk chunk : chunks) {
                if (upsertChunk(chunk)) {
                    indexed++;
                }
            }
            markIndexed(sourcePath, fileName, contentHash, chunks.size());
            log.info("RAG 文档索引完成，sourcePath={}, chunks={}, indexed={}", sourcePath, chunks.size(), indexed);
            return new IndexOutcome(chunks.size(), indexed, false);
        } catch (RuntimeException exception) {
            markFailed(sourcePath, fileName, contentHash, exception.getMessage());
            log.warn("RAG 文档索引失败，sourcePath={}, reason={}", sourcePath, exception.getMessage());
            return new IndexOutcome(0, 0, false);
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

    private List<Path> scanDocs() {
        List<Path> paths = new java.util.ArrayList<>();
        Path readme = Path.of("README.md");
        if (Files.exists(readme)) {
            paths.add(readme);
        }
        Path docs = Path.of("docs");
        if (Files.isDirectory(docs)) {
            try (var stream = Files.list(docs)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".md"))
                        .sorted()
                        .forEach(paths::add);
            } catch (Exception exception) {
                log.debug("RAG 文档扫描异常，docs/={}", exception.getMessage());
            }
        }
        return paths;
    }

    private void markIndexed(String sourcePath, String fileName, String contentHash, int chunkCount) {
        try {
            documentIndexMapper.upsertSuccess(idGenerator.nextId(), sourcePath, fileName, contentHash, chunkCount);
        } catch (RuntimeException exception) {
            log.debug("RAG 索引状态写入失败，不影响向量检索，sourcePath={}, reason={}", sourcePath, exception.getMessage());
        }
    }

    private void markFailed(String sourcePath, String fileName, String contentHash, String errorMessage) {
        try {
            String safeError = StringUtils.hasText(errorMessage) && errorMessage.length() > 900
                    ? errorMessage.substring(0, 900)
                    : errorMessage;
            documentIndexMapper.markFailed(idGenerator.nextId(), sourcePath, fileName, contentHash, safeError);
        } catch (RuntimeException exception) {
            log.debug("RAG 索引失败状态写入失败，sourcePath={}, reason={}", sourcePath, exception.getMessage());
        }
    }

    private void deleteByMetadata(String key, String value) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        try {
            Map<String, Object> filter = Map.of("must", List.of(
                    Map.of("key", key, "match", Map.of("value", value))
            ));
            restClient.post()
                    .uri("/collections/{collection}/points/delete?wait=true", DOCS_COLLECTION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("filter", filter))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException exception) {
            log.debug("RAG 删除旧向量失败，key={}, value={}, reason={}", key, value, exception.getMessage());
        }
    }

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

    private void ensureDocsCollection() {
        try {
            restClient.get().uri("/collections/{collection}", DOCS_COLLECTION)
                    .retrieve().body(String.class);
            return;
        } catch (RuntimeException ignored) {
            // 集合不存在时继续创建。
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

    private void ensureDocsCollectionReady() {
        // 当前以 collection 可访问作为就绪条件；索引状态由 ai_document_index 表记录。
    }

    private String normalizeSourcePath(Path file) {
        return file.normalize().toString().replace("\\", "/");
    }

    private String hashFile(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readString(file, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("文档哈希计算失败: " + file, exception);
        }
    }

    private record IndexOutcome(int chunkCount, int indexedCount, boolean skipped) {
    }
}