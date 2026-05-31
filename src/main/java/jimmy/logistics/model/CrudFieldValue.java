package jimmy.logistics.model;

/**
 * 通用 CRUD 字段绑定 —— 封装动态 SQL 中字段名与值的映射关系。
 * <p>
 * 用于 {@link jimmy.logistics.mapper.LogisticsCrudMapper} 的
 * {@code insertRecord / updateRecord / logicalDelete} 等方法，
 * 配合 MyBatis 的 {@code ${column}} 动态列名和 {@code #{value}} 参数绑定。
 * </p>
 */
public class CrudFieldValue {

    /** 数据库字段名（来自后端白名单配置，不接收用户输入） */
    private final String column;

    /** 字段值（由 MyBatis #{value} 参数绑定，防 SQL 注入） */
    private final Object value;

    public CrudFieldValue(String column, Object value) {
        this.column = column;
        this.value = value;
    }

    public String getColumn() {
        return column;
    }

    public Object getValue() {
        return value;
    }
}
