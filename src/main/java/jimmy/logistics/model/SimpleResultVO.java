package jimmy.logistics.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.LinkedHashMap;
import java.util.Map;

public class SimpleResultVO {

    private final Map<String, Object> values = new LinkedHashMap<>();

    public SimpleResultVO add(String key, Object value) {
        values.put(key, value);
        return this;
    }

    public static SimpleResultVO from(Map<String, Object> source) {
        SimpleResultVO result = new SimpleResultVO();
        if (source != null) {
            result.values.putAll(source);
        }
        return result;
    }

    @JsonValue
    public Map<String, Object> getValues() {
        return values;
    }
}
