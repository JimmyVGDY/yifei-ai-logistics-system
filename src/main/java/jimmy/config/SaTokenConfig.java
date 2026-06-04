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

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 安全配置 —— Sa-Token 登录鉴权 + CORS 跨域 + 操作日志拦截器注册。
 * <p>
 * 三种权限校验策略：
 * <ol>
 *   <li><b>动态模块鉴权</b>（/logistics/modules/）：根据模块名 → 权限前缀 → HTTP 方法映射 action</li>
 *   <li><b>静态路径鉴权</b>（/logistics/dashboard、/system/permissions 等）：固定权限码匹配</li>
 *   <li><b>通用管理页</b>：GET→query，POST(一级)→create，DELETE→delete，其余 POST/PUT→update</li>
 * </ol>
 * <p>
 * 模块白名单在 {@link #buildModulePermissionPrefixes()} 中维护，只有登记的模块才能进入动态鉴权。
 */
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

    /**
     * CORS 跨域配置，从环境变量 app.cors.origins 读取（逗号分隔多个地址）。
     * 默认允许 http://127.0.0.1:5173（Vite 开发服务器地址）。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(corsOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 注册两条拦截器链：
     * <ol>
     *   <li>SaInterceptor：全部路径（排除登录/健康检查），校验登录态 + 路由权限</li>
     *   <li>OperationLogInterceptor：仅覆盖 /logistics、/auth、/system、/infra、/ai 等业务路径，记录操作日志</li>
     * </ol>
     */
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
                        "/auth/captcha",
                        "/auth/login-conflicts/*/status",
                        "/actuator/health"
                );
        registry.addInterceptor(operationLogInterceptor)
                .addPathPatterns(
                        "/logistics/**",
                        "/auth/**",
                        "/system/**",
                        "/infra/**",
                        "/ai/**",
                        "/demo-users/**",
                        "/bloom-filter/**",
                        "/rabbitmq/**"
                );
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
        SaRouter.match("/ai/chat", r -> StpUtil.checkPermission("ai:chat"));
        SaRouter.match("/ai/logs/analyze", r -> StpUtil.checkPermission("ai:log:analyze"));
        SaRouter.match("/ai/conversations", r -> StpUtil.checkPermission("ai:conversation:query"));
        SaRouter.match("/ai/conversations/**", r -> StpUtil.checkPermission("ai:conversation:query"));
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

    /**
     * 动态权限解析：根据请求 URI 和 HTTP 方法映射到对应权限码。
     * <p>
     * 映射规则：module → permissionPrefix + action。
     * 例如：GET /logistics/modules/orders → order:query
     *
     * @return 权限码，无法映射返回 null
     */
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
        if (uri.startsWith("/demo-users")) {
            return "GET".equalsIgnoreCase(method) ? "system:user:query" : "system:user:create";
        }
        if (uri.startsWith("/bloom-filter")) {
            return "resource:view";
        }
        if (uri.startsWith("/rabbitmq")) {
            return "resource:view";
        }
        if (uri.equals("/ai/chat")) {
            return "ai:chat";
        }
        if (uri.equals("/ai/logs/analyze")) {
            return "ai:log:analyze";
        }
        if (uri.startsWith("/ai/conversations")) {
            return "ai:conversation:query";
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
        if (parts.length >= 3 && "delete".equals(parts[2])) {
            return "delete";
        }
        return switch (method.toUpperCase()) {
            case "GET" -> "query";
            case "POST" -> parts.length == 1 ? "create" : "update";
            default -> "update";
        };
    }

    private String permissionAction(String prefix) {
        HttpServletRequest request = currentRequest();
        if (request == null || "GET".equalsIgnoreCase(request.getMethod())) {
            return prefix + ":query";
        }
        return prefix + ":update";
    }

    private HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servletRequestAttributes)) {
            return null;
        }
        return servletRequestAttributes.getRequest();
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
