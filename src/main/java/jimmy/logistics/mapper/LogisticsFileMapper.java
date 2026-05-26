package jimmy.logistics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LogisticsFileMapper {

    int insertUploadedFile(@Param("id") Long id,
                           @Param("originalName") String originalName,
                           @Param("storedName") String storedName,
                           @Param("relativePath") String relativePath,
                           @Param("fileSize") Long fileSize,
                           @Param("contentType") String contentType,
                           @Param("uploadUser") String uploadUser);
}
