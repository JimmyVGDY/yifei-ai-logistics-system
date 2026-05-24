package jimmy.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class InfrastructureStatus {

    private String component;
    private String status;
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
