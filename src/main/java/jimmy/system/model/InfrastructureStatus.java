package jimmy.system.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基础设施健康状态 —— 组件名 + 状态 + 详细信息，支持链式 {@code .detail()} 构建。
 */
public class InfrastructureStatus {

    /** 组件名称（MySQL/Redis/RabbitMQ/ES） */
    private String component;
    /** 健康状态（UP/DOWN/UNKNOWN） */
    private String status;
    /** 详细信息（版本/连接数/延迟等） */
    private Map<String, Object> details = new LinkedHashMap<String, Object>();

    public InfrastructureStatus() {
    }

    public InfrastructureStatus(String component, String status) {
        this.component = component;
        this.status = status;
    }

    public static InfrastructureStatus of(String component, String status) {
        return new InfrastructureStatus(component, status);
    }

    public InfrastructureStatus detail(String key, Object value) {
        this.details.put(key, value);
        return this;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }
}
