package jimmy.config;

import jimmy.logistics.util.ColumnExistenceChecker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 基础设施 Bean 配置。
 */
@Configuration
public class InfrastructureConfig {

    @Bean
    public ColumnExistenceChecker columnExistenceChecker(DataSource dataSource) {
        return new ColumnExistenceChecker(dataSource);
    }
}
