package jimmy.ai.mapper;

import jimmy.ai.model.AiDocumentIndexRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * RAG 文档索引状态 Mapper。
 * <p>
 * 文档向量仍存放在 Qdrant，本 Mapper 只维护“哪个文档、哪个哈希、是否索引成功”的增量索引元数据。
 */
@Mapper
public interface AiDocumentIndexMapper {

    AiDocumentIndexRecord selectBySourcePath(@Param("sourcePath") String sourcePath);

    int upsertSuccess(@Param("id") Long id,
                      @Param("sourcePath") String sourcePath,
                      @Param("fileName") String fileName,
                      @Param("contentHash") String contentHash,
                      @Param("chunkCount") int chunkCount);

    int markFailed(@Param("id") Long id,
                   @Param("sourcePath") String sourcePath,
                   @Param("fileName") String fileName,
                   @Param("contentHash") String contentHash,
                   @Param("errorMessage") String errorMessage);
}
