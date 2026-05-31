package jimmy.logistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 业务文件数据访问 —— 记录上传文件的原始名、存储路径和上传人。
 */
@Mapper
public interface LogisticsFileMapper {

    /** 插入文件上传记录 */
    int insertUploadedFile(@Param("id") Long id,
                           @Param("originalName") String originalName,
                           @Param("storedName") String storedName,
                           @Param("relativePath") String relativePath,
                           @Param("fileSize") Long fileSize,
                           @Param("contentType") String contentType,
                           @Param("uploadUser") String uploadUser);
}
