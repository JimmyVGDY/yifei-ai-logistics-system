package jimmy.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@Profile("dev")
@EnableConfigurationProperties(LocalFrontendProperties.class)
public class LocalFrontendLauncher implements ApplicationRunner {

    private final LocalFrontendProperties properties;

    public LocalFrontendLauncher(LocalFrontendProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (properties.isAutoStart() && !isFrontendAvailable()) {
                startFrontend();
            }
            if (properties.isAutoOpen()) {
                waitForFrontendAndOpen();
            }
        } catch (Exception exception) {
            // 前端启动/打开失败不阻塞后端服务启动，仅记录日志。
            // 典型场景：WSL2 下缺少 xdg-open、CI 环境无 GUI 等。
            log.warn("前端开发服务启动失败，后端服务继续运行，reason={}", exception.getMessage());
        }
    }

    private boolean isFrontendAvailable() {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(properties.getUrl()).openConnection();
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            connection.setRequestMethod("GET");
            int status = connection.getResponseCode();
            return status >= 200 && status < 500;
        } catch (IOException ignored) {
            return false;
        }
    }

    private void startFrontend() throws IOException {
        File workingDirectory = new File(properties.getWorkingDirectory());
        if (!workingDirectory.exists()) {
            return;
        }

        // 跨平台启动命令
        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder processBuilder;
        if (os.contains("win")) {
            processBuilder = new ProcessBuilder("cmd", "/c", "npm", "run", "dev");
        } else {
            processBuilder = new ProcessBuilder("npm", "run", "dev");
        }
        processBuilder.directory(workingDirectory);
        processBuilder.redirectErrorStream(true);
        processBuilder.start();
    }

    private void waitForFrontendAndOpen() throws Exception {
        for (int i = 0; i < 30; i++) {
            if (isFrontendAvailable()) {
                openBrowser();
                return;
            }
            TimeUnit.SECONDS.sleep(1);
        }
    }

    private void openBrowser() throws Exception {
        URI uri = URI.create(properties.getUrl());
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(uri);
            return;
        }
        
        // 跨平台浏览器打开命令
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", properties.getUrl()});
        } else if (os.contains("mac")) {
            Runtime.getRuntime().exec(new String[]{"open", properties.getUrl()});
        } else {
            Runtime.getRuntime().exec(new String[]{"xdg-open", properties.getUrl()});
        }
    }
}
