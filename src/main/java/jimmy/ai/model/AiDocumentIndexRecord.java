package jimmy.ai.model;

/**
 * RAG 文档索引状态记录。
 * <p>
 * 该记录只保存文档元数据和内容哈希，不保存文档正文；正文仍以 Markdown 源文件和 Qdrant 脱敏片段为准。
 *
 * @param sourcePath  文档相对路径
 * @param contentHash 文档内容哈希
 * @param chunkCount  最近一次成功索引的分块数
 * @param status      SUCCESS / FAILED
 */
public record AiDocumentIndexRecord(String sourcePath, String contentHash, Integer chunkCount, String status) {
}
