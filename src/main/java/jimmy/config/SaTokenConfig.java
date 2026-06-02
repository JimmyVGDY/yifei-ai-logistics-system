package jimmy.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import jimmy.logistics.config.OperationLogInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    private static final Map<String, String> MODULE_PERMISSION_PREFIXES = buildModulePermissionPrefixes();

    private final OperationLogInterceptor operationLogInterceptor;
    /** 允许跨域的前端地址，可配环境变量 CORS_ORIGINS=地址1,地址2 */
    private final String corsOrigins;

    public SaTokenConfig(OperationLogInterceptor operationLogInterceptor,
                         @Value("${app.cors.origins:http://127.0.0.1:5173}") String corsOrigins) {
        this.operationLogInterceptor = operationLogInterceptor;
        this.corsOrigins = corsOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(corsOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
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
                        "/auth/login-conflicts/*/status",
                        "/actuator/health"
                );
        registry.addInterceptor(operationLogInterceptor)
                .addPathPatterns("/logistics/**", "/auth/**");
    }

    private void checkRoutePermission() {
        if (checkDynamicLogisticsPermission()) {
            return;
        }
        // 非通用模块接口在这里集中声明权限，便于和前端路由 meta.permission 对照维护。
        SaRouter.match("/logistics/dashboard", r -> StpUtil.checkPermission("dashboard:view"));
        SaRouter.match("/logistics/statistics/**", r -> StpUtil.checkPermission("dashboard:view"));
        SaRouter.match("/system/permissions/**", r -> StpUtil.checkPermission(permissionAction("system:permission")));
        SaRouter.match("/infra", r -> StpUtil.checkPermission("resource:view"));
        SaRouter.match("/infra/**", r -> StpUtil.checkPermission("resource:view"));
    }

    private boolean checkDynamicLogisticsPermission() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return false;
        }
        String uri = request.getRequestURI();
        String method = request.getMethod();
        String permission = resolveDynamicPermission(uri, method);
        if (permission != null) {
            StpUtil.checkPermission(permission);
            return true;
        }
        return false;
    }

    String resolveDynamicPermission(String uri, String method) {
        if (uri.startsWith("/logistics/modules/")) {
            // 通用管理页接口按模块名映射权限前缀，再按 HTTP 方法解析查询/新增/修改/删除动作。
            String[] parts = uri.substring("/logistics/modules/".length()).split("/");
            String module = parts.length == 0 ? "" : parts[0];
            String prefix = MODULE_PERMISSION_PREFIXES.get(module);
            if (prefix == null) {
                return null;
            }
            return prefix + ":" + resolveModuleAction(method, parts);
        }
        if (uri.startsWith("/logistics/excel/export/")) {
            String module = uri.substring("/logistics/excel/export/".length()).split("/")[0];
            String prefix = MODULE_PERMISSION_PREFIXES.get(module);
            if (prefix != null) {
                return prefix + ":export";
            }
        }
        if (uri.equals("/logistics/excel/import/customers")) {
            return "customer:import";
        }
        if (uri.equals("/logistics/files/upload")) {
            return "file:create";
        }
        if (uri.equals("/logistics/customer-accounts")) {
            return "system:user:create";
        }
        if (uri.startsWith("/logistics/orders")) {
            return "GET".equalsIgnoreCase(method) ? "order:query" : "order:create";
        }
        if (uri.startsWith("/logistics/exceptions")) {
            return uri.endsWith("/handle") ? "exception:update" : "exception:create";
        }
        if (uri.startsWith("/logistics/fees")) {
            return uri.startsWith("/logistics/fees/generate/") ? "fee:create" : "fee:update";
        }
        return null;
    }

    private String resolveModuleAction(String method, String[] parts) {
        if ("GET".equalsIgnoreCase(method)) {
            return "query";
        }
        if (parts.length >= 3 && "delete".equals(parts[2])) {
            return "delete";
        }
        if ("POST".equalsIgnoreCase(method) && parts.length == 1) {
            return "create";
        }
        // 其余 POST/PUT/PATCH 统一按更新权限处理，例如状态处理、编辑表单保存等。
        return "update";
    }

    private String permissionAction(String prefix) {
        HttpServletRequest request = currentRequest();
        if (request == null || "GET".equalsIgnoreCase(request.getMethod())) {
            return prefix + ":query";
        }
        return prefix + ":update";
    }

    private HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
            return null;
        }
        return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
    }

    private static Map<String, String> buildModulePermissionPrefixes() {
        Map<String, String> map = new HashMap<>();
        // 这里只允许已登记模块进入动态鉴权，避免前端传任意 module 绕过权限控制。
        map.put("orders", "order");
        map.put("customers", "customer");
        map.put("waybills", "waybill");
        map.put("dispatches", "dispatch");
        map.put("tasks", "task");
        map.put("tracks", "track");
        map.put("drivers", "driver");
        map.put("vehicles", "vehicle");
        map.put("exceptions", "exception");
        map.put("fees", "fee");
        map.put("users", "system:user");
        map.put("roles", "system:role");
        map.put("operationLogs", "system:log");
        map.put("files", "file");
        return map;
    }
}
