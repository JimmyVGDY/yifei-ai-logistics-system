package jimmy.config;

import jimmy.logistics.util.ColumnExistenceChecker;
import jimmy.util.EncryptedTypeHandler;
import jimmy.util.FieldEncryptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;

/**
 * 基础设施 Bean 配置。
 */
@Configuration
public class InfrastructureConfig {

    private final FieldEncryptor fieldEncryptor;

    public InfrastructureConfig(FieldEncryptor fieldEncryptor) {
        this.fieldEncryptor = fieldEncryptor;
    }

    @PostConstruct
    public void initEncryptedTypeHandler() {
        // MyBatis TypeHandler 由 MyBatis 实例化，不经过 Spring 容器，需要手动注入加密器实例。
        EncryptedTypeHandler.setEncryptor(fieldEncryptor);
    }

    @Bean
    public ColumnExistenceChecker columnExistenceChecker(DataSource dataSource) {
        return new ColumnExistenceChecker(dataSource);
    }
}
