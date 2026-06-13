package jimmy.logistics.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.concurrent.TimeUnit;

/**
 * 数据库字段存在性检测工具。
 * <p>
 * 用于兼容增量迁移前后的数据库结构：在运行时检测某张表是否存在某字段，
 * 结果使用 Guava Cache 缓存（30分钟 TTL），避免频繁查询元数据。
 * </p>
 * <p>
 * 增量迁移后最多 30 分钟内缓存自动刷新，无需重启应用。
 * </p>
 */
@Slf4j
public final class ColumnExistenceChecker {

    /** 字段存在性缓存（30分钟自动过期），key 格式: {@code tableName.columnName} */
    private final Cache<String, Boolean> cache = CacheBuilder.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(500)
            .build();

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
        Boolean cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }
        boolean result = resolveColumnExistence(tableName, columnName);
        cache.put(cacheKey, result);
        return result;
    }

    private boolean resolveColumnExistence(String tableName, String columnName) {
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
    }
}
