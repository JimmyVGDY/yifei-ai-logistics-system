package jimmy.ai.model;

import java.util.Map;

/**
 * 文档分块 —— RAG 检索的基本单元。
 * <p>
 * 每个 chunk 包含一段连续的文档文本及其元数据（来源文件、标题、章节名），
 * 向量化后存入 Qdrant 供语义检索。
 *
 * @param chunkId   分块唯一标识
 * @param content   文本内容（已去除 Markdown 标记符号）
 * @param metadata  元数据（source: 文件名, title: 文档标题, section: 当前章节标题）
 */
public record DocumentChunk(String chunkId, String content, Map<String, Object> metadata) {
}
