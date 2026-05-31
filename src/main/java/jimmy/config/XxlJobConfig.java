package jimmy.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL-Job 分布式定时任务执行器配置。
 * <p>
 * 调度中心地址通过环境变量 {@code XXL_JOB_ADMIN_ADDRESSES} 配置，
 * 本地开发或无需定时任务时默认关闭，避免无调度中心时额外占用执行器端口。
 * </p>
 */
@Configuration
public class XxlJobConfig {

    @Bean(initMethod = "start", destroyMethod = "destroy")
    @ConditionalOnProperty(prefix = "xxl.job", name = "enabled", havingValue = "true")
    public XxlJobSpringExecutor xxlJobExecutor(
            @Value("${xxl.job.admin.addresses:}") String adminAddresses,
            @Value("${xxl.job.executor.appname:logistics-app}") String appname,
            @Value("${xxl.job.executor.port:9999}") int port,
            @Value("${xxl.job.executor.logpath:/app/logs/xxl-job}") String logpath,
            @Value("${xxl.job.accessToken:}") String accessToken) {
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAppname(appname);
        executor.setPort(port);
        executor.setLogPath(logpath);
        executor.setAccessToken(accessToken);
        return executor;
    }
}
