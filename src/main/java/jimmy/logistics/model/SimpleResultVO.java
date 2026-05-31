package jimmy.logistics.model;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 简单结果封装 —— 用于返回操作结果中的关键字段值。
 * <p>
 * 通过 {@code @JsonValue} 直接将内部 Map 序列化为 JSON Object，
 * 避免嵌套 {@code data} 层级。支持链式调用 {@code add()} 构建。
 * </p>
 *
 * <pre>
 * new SimpleResultVO().add("orderNo", "LO001").add("status", "OK");
 * // → {"orderNo": "LO001", "status": "OK"}
 * </pre>
 */
public class SimpleResultVO {

    private final Map<String, Object> values = new LinkedHashMap<>();

    /** 添加键值对，返回自身以支持链式调用 */
    public SimpleResultVO add(String key, Object value) {
        values.put(key, value);
        return this;
    }

    /** 从已有 Map 构建 */
    public static SimpleResultVO from(Map<String, Object> source) {
        SimpleResultVO result = new SimpleResultVO();
        if (source != null) {
            result.values.putAll(source);
        }
        return result;
    }

    /** Jackson 序列化时直接输出内部 Map */
    @JsonValue
    public Map<String, Object> getValues() {
        return values;
    }
}
