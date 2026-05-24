package jimmy.config;

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

@Configuration
@Profile("dev")
@EnableConfigurationProperties(LocalFrontendProperties.class)
public class LocalFrontendLauncher implements ApplicationRunner {

    private final LocalFrontendProperties properties;

    public LocalFrontendLauncher(LocalFrontendProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (properties.isAutoStart() && !isFrontendAvailable()) {
            startFrontend();
        }
        if (properties.isAutoOpen()) {
            waitForFrontendAndOpen();
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

        ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", "npm", "run", "dev");
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
        Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", properties.getUrl()});
    }
}
