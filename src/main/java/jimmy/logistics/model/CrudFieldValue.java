package jimmy.logistics.model;

public class CrudFieldValue {

    private final String column;
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
