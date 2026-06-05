package jimmy;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 物流管理平台 Spring Boot 启动入口。
 * <p>
 * MyBatis Mapper 分布在 {@code jimmy.mapper} 和 {@code jimmy.logistics.mapper}
 * 两个包中，通过 {@code @MapperScan} 统一扫描。
 * </p>
 */
@SpringBootApplication
@MapperScan({"jimmy.mapper", "jimmy.logistics.mapper", "jimmy.ai.mapper"})
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
