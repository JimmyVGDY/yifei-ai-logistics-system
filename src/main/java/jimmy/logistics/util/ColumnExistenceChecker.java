package jimmy.logistics.util;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 数据库字段存在性检测工具。
 * <p>
 * 用于兼容增量迁移前后的数据库结构：在运行时检测某张表是否存在某字段，
 * 结果使用 {@link ConcurrentHashMap} 缓存，避免频繁查询元数据。
 * </p>
 * <p>
 * 所有检测结果在 JVM 生命周期内一直缓存，表结构变更后需重启应用以刷新。
 * </p>
 */
@Slf4j
public final class ColumnExistenceChecker {

    /** 字段存在性缓存，key 格式: {@code tableName.columnName} */
    private final ConcurrentMap<String, Boolean> cache = new ConcurrentHashMap<>();

    private final DataSource dataSource;

    public ColumnExistenceChecker(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 检测表是否存在指定字段。
     *
     * @param tableName  表名
     * @param columnName 字段名
     * @return true 字段存在，false 不存在或检测失败
     */
    public boolean hasColumn(String tableName, String columnName) {
        String cacheKey = tableName + "." + columnName;
        return cache.computeIfAbsent(cacheKey, key -> {
            // 兼容 MySQL/Linux 下大小写敏感与不敏感两种配置。
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                String[] tableCandidates = {tableName, tableName.toUpperCase(), tableName.toLowerCase()};
                String[] columnCandidates = {columnName, columnName.toUpperCase(), columnName.toLowerCase()};
                for (String table : tableCandidates) {
                    for (String column : columnCandidates) {
                        try (ResultSet rs = metaData.getColumns(connection.getCatalog(), null, table, column)) {
                            if (rs.next()) {
                                return true;
                            }
                        }
                    }
                }
            } catch (Exception exception) {
                log.warn("检测表字段失败，tableName={}, columnName={}, reason={}",
                        tableName, columnName, exception.getMessage());
            }
            return false;
        });
    }
}
