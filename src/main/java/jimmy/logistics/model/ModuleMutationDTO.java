package jimmy.logistics.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用模块增删改请求体 —— 动态接收任意字段的 JSON。
 * <p>
 * 通过 {@code @JsonAnySetter} 接收前端传来的所有字段，
 * 在 Service 层通过白名单过滤后才写入数据库，防止未授权字段注入。
 * </p>
 */
public class ModuleMutationDTO {

    /** 前端传入的字段键值对，由 {@code @JsonAnySetter} 自动填充 */
    private final Map<String, Object> values = new LinkedHashMap<>();

    @JsonAnySetter
    public void put(String key, Object value) {
        values.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getValues() {
        return values;
    }
}
