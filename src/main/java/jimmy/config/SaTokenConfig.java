package jimmy.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import jimmy.logistics.config.OperationLogInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    private final OperationLogInterceptor operationLogInterceptor;

    public SaTokenConfig(OperationLogInterceptor operationLogInterceptor) {
        this.operationLogInterceptor = operationLogInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
                    StpUtil.checkLogin();
                    checkRoutePermission();
                }))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/",
                        "/error",
                        "/favicon.ico",
                        "/auth/login",
                        "/actuator/health"
                );
        registry.addInterceptor(operationLogInterceptor)
                .addPathPatterns("/logistics/**", "/auth/**");
    }

    private void checkRoutePermission() {
        SaRouter.match("/logistics/dashboard", r -> StpUtil.checkPermission("dashboard:view"));
        SaRouter.match("/logistics/modules/orders", r -> StpUtil.checkPermission("order:manage"));
        SaRouter.match("/logistics/modules/orders/**", r -> StpUtil.checkPermission("order:manage"));
        SaRouter.match("/logistics/orders", r -> StpUtil.checkPermission("order:manage"));
        SaRouter.match("/logistics/orders/**", r -> StpUtil.checkPermission("order:manage"));
        SaRouter.match("/logistics/modules/customers", r -> StpUtil.checkPermission("customer:manage"));
        SaRouter.match("/logistics/modules/customers/**", r -> StpUtil.checkPermission("customer:manage"));
        SaRouter.match("/logistics/modules/waybills", r -> StpUtil.checkPermission("waybill:manage"));
        SaRouter.match("/logistics/modules/waybills/**", r -> StpUtil.checkPermission("waybill:manage"));
        SaRouter.match("/logistics/modules/dispatches", r -> StpUtil.checkPermission("dispatch:manage"));
        SaRouter.match("/logistics/modules/dispatches/**", r -> StpUtil.checkPermission("dispatch:manage"));
        SaRouter.match("/logistics/modules/tasks", r -> StpUtil.checkPermission("task:manage"));
        SaRouter.match("/logistics/modules/tasks/**", r -> StpUtil.checkPermission("task:manage"));
        SaRouter.match("/logistics/modules/tracks", r -> StpUtil.checkPermission("track:view"));
        SaRouter.match("/logistics/modules/tracks/**", r -> StpUtil.checkPermission("track:view"));
        SaRouter.match("/logistics/modules/drivers", r -> StpUtil.checkPermission("driver:manage"));
        SaRouter.match("/logistics/modules/drivers/**", r -> StpUtil.checkPermission("driver:manage"));
        SaRouter.match("/logistics/modules/vehicles", r -> StpUtil.checkPermission("vehicle:manage"));
        SaRouter.match("/logistics/modules/vehicles/**", r -> StpUtil.checkPermission("vehicle:manage"));
        SaRouter.match("/logistics/modules/exceptions", r -> StpUtil.checkPermission("exception:manage"));
        SaRouter.match("/logistics/modules/exceptions/**", r -> StpUtil.checkPermission("exception:manage"));
        SaRouter.match("/logistics/exceptions", r -> StpUtil.checkPermission("exception:manage"));
        SaRouter.match("/logistics/exceptions/**", r -> StpUtil.checkPermission("exception:manage"));
        SaRouter.match("/logistics/modules/fees", r -> StpUtil.checkPermission("fee:manage"));
        SaRouter.match("/logistics/modules/fees/**", r -> StpUtil.checkPermission("fee:manage"));
        SaRouter.match("/logistics/fees", r -> StpUtil.checkPermission("fee:manage"));
        SaRouter.match("/logistics/fees/**", r -> StpUtil.checkPermission("fee:manage"));
        SaRouter.match("/logistics/modules/users", r -> StpUtil.checkPermission("system:user:manage"));
        SaRouter.match("/logistics/modules/users/**", r -> StpUtil.checkPermission("system:user:manage"));
        SaRouter.match("/logistics/modules/roles", r -> StpUtil.checkPermission("system:role:manage"));
        SaRouter.match("/logistics/modules/roles/**", r -> StpUtil.checkPermission("system:role:manage"));
        SaRouter.match("/logistics/modules/operationLogs", r -> StpUtil.checkPermission("system:log:view"));
        SaRouter.match("/logistics/modules/operationLogs/**", r -> StpUtil.checkPermission("system:log:view"));
        SaRouter.match("/logistics/modules/files", r -> StpUtil.checkPermission("file:manage"));
        SaRouter.match("/logistics/modules/files/**", r -> StpUtil.checkPermission("file:manage"));
        SaRouter.match("/logistics/files", r -> StpUtil.checkPermission("file:manage"));
        SaRouter.match("/logistics/files/**", r -> StpUtil.checkPermission("file:manage"));
        SaRouter.match("/infra", r -> StpUtil.checkPermission("resource:view"));
        SaRouter.match("/infra/**", r -> StpUtil.checkPermission("resource:view"));
    }
}
