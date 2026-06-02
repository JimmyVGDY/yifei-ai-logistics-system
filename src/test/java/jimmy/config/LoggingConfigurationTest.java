package jimmy.config;

import org.junit.jupiter.api.Test;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingConfigurationTest {

    @Test
    void defaultMapperLogLevelShouldNotExposeSqlDebug() throws Exception {
        String applicationYaml = resourceText("/application.yml");

        assertThat(applicationYaml).contains("jimmy.mapper: ${MYBATIS_SQL_LOG_LEVEL:info}");
        assertThat(applicationYaml).doesNotContain("jimmy.mapper: debug");
    }

    @Test
    void productionMapperLogLevelShouldBeExplicitlyRestricted() throws Exception {
        String productionYaml = resourceText("/application-prod.yml");

        assertThat(productionYaml).contains("jimmy.mapper: WARN");
    }

    private String resourceText(String path) throws Exception {
        return StreamUtils.copyToString(getClass().getResourceAsStream(path), StandardCharsets.UTF_8);
    }
}
