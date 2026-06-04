package jimmy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地前端开发服务配置属性 —— 绑定 {@code app.local-frontend} 前缀。
 */
@ConfigurationProperties(prefix = "app.local-frontend")
public class LocalFrontendProperties {

    private boolean autoStart = true;
    private boolean autoOpen = true;
    private String url = "http://127.0.0.1:5173";
    private String workingDirectory = "frontend";
    private String startCommand = "npm run dev";

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public boolean isAutoOpen() {
        return autoOpen;
    }

    public void setAutoOpen(boolean autoOpen) {
        this.autoOpen = autoOpen;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public String getStartCommand() {
        return startCommand;
    }

    public void setStartCommand(String startCommand) {
        this.startCommand = startCommand;
    }
}
