package jimmy.system.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
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
// 项目启用了 Spring Cloud bootstrap，父上下文不是 Web 应用；这里限定只在主 Web 上下文启动执行器，避免同一端口被重复绑定。
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class XxlJobConfig {

    @Bean(initMethod = "start", destroyMethod = "destroy")
    @ConditionalOnProperty(prefix = "xxl.job", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(XxlJobSpringExecutor.class)
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
