package jimmy.logistics.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 通用 CRUD 模块白名单配置。
 * <p>
 * 动态表名和动态字段名只允许来自该配置，前端不能直接决定真实表名和列名。
 */
class CrudConfig {

    final String tableName;
    final String createTimeColumn;
    final String updateTimeColumn;
    final List<String> columns;
    final List<String> nullableColumns;
    final String generatedCodeColumn;
    final String generatedCodePrefix;

    CrudConfig(String tableName, String createTimeColumn, String updateTimeColumn, String... columns) {
        this(tableName, createTimeColumn, updateTimeColumn, new ArrayList<>(), null, null, columns);
    }

    static CrudConfig withGeneratedCode(String tableName, String createTimeColumn, String updateTimeColumn,
                                        String generatedCodeColumn, String generatedCodePrefix, String... columns) {
        return new CrudConfig(tableName, createTimeColumn, updateTimeColumn, new ArrayList<>(), generatedCodeColumn, generatedCodePrefix, columns);
    }

    static CrudConfig withNullable(String tableName, String createTimeColumn, String updateTimeColumn,
                                   List<String> nullableColumns, String... columns) {
        return new CrudConfig(tableName, createTimeColumn, updateTimeColumn, nullableColumns, null, null, columns);
    }

    private CrudConfig(String tableName, String createTimeColumn, String updateTimeColumn, List<String> nullableColumns,
                       String generatedCodeColumn, String generatedCodePrefix, String... columns) {
        this.tableName = tableName;
        this.createTimeColumn = createTimeColumn;
        this.updateTimeColumn = updateTimeColumn;
        this.columns = Arrays.asList(columns);
        this.nullableColumns = nullableColumns;
        this.generatedCodeColumn = generatedCodeColumn;
        this.generatedCodePrefix = generatedCodePrefix;
    }
}
