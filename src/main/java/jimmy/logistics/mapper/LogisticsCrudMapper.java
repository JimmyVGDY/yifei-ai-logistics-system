package jimmy.logistics.mapper;

import jimmy.logistics.model.CrudFieldValue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LogisticsCrudMapper {

    int insertRecord(@Param("tableName") String tableName,
                     @Param("fields") List<CrudFieldValue> fields);

    int updateRecord(@Param("tableName") String tableName,
                     @Param("id") Long id,
                     @Param("fields") List<CrudFieldValue> fields,
                     @Param("increaseVersion") boolean increaseVersion);

    int logicalDelete(@Param("tableName") String tableName,
                      @Param("id") Long id,
                      @Param("fields") List<CrudFieldValue> fields,
                      @Param("increaseVersion") boolean increaseVersion);

    int physicalDelete(@Param("tableName") String tableName,
                       @Param("id") Long id);

    int countByBusinessCode(@Param("tableName") String tableName,
                            @Param("columnName") String columnName,
                            @Param("code") String code);
}
