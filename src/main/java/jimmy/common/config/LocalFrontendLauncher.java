package jimmy.common.config;

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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 本地前端自动启动器 —— dev 环境下自动启动 Vite 前端并打开浏览器。
 * <p>
 * 仅 {@code @Profile("dev")} 激活，启动失败不阻塞后端。
 */
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
        File workingDirectory = resolveWorkingDirectory();
        if (!workingDirectory.exists()) {
            log.warn("前端目录不存在，跳过自动启动，directory={}", workingDirectory.getAbsolutePath());
            return;
        }

        ProcessBuilder processBuilder = new ProcessBuilder(frontendStartCommand());
        processBuilder.directory(workingDirectory);
        processBuilder.redirectErrorStream(true);
        ensureNodePath(processBuilder);
        processBuilder.start();
        log.info("前端开发服务已尝试启动，directory={}, url={}", workingDirectory.getAbsolutePath(), properties.getUrl());
    }

    private void waitForFrontendAndOpen() throws Exception {
        for (int i = 0; i < 30; i++) {
            if (isFrontendAvailable()) {
                openBrowser();
                log.info("前端页面已打开，url={}", properties.getUrl());
                return;
            }
            TimeUnit.SECONDS.sleep(1);
        }
        log.warn("等待前端开发服务超时，未自动打开浏览器，url={}", properties.getUrl());
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

    private File resolveWorkingDirectory() {
        Path configuredPath = Paths.get(properties.getWorkingDirectory());
        if (configuredPath.isAbsolute()) {
            return configuredPath.toFile();
        }
        return Paths.get(System.getProperty("user.dir"), properties.getWorkingDirectory()).toFile();
    }

    private List<String> frontendStartCommand() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return List.of("cmd", "/c", properties.getStartCommand());
        }
        return List.of("sh", "-c", properties.getStartCommand());
    }

    private void ensureNodePath(ProcessBuilder processBuilder) {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) {
            return;
        }
        List<String> pathItems = new ArrayList<>();
        pathItems.add("C:\\Program Files\\nodejs");
        pathItems.add("C:\\Windows\\System32");
        pathItems.add("C:\\Windows");
        pathItems.add("C:\\Windows\\System32\\Wbem");
        String currentPath = processBuilder.environment().getOrDefault("PATH", "");
        processBuilder.environment().put("PATH", String.join(";", pathItems) + ";" + currentPath);
    }
}
