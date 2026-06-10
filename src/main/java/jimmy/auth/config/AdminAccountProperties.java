package jimmy.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 管理账号配置属性 —— 绑定 {@code app.auth.admin} 前缀的 YAML 配置。
 */
@Component
@ConfigurationProperties(prefix = "app.auth.admin")
public class AdminAccountProperties {

    private String username = "admin";
    private String password = "xlh963311213";

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
