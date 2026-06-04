package jimmy.logistics.config;

import jimmy.logistics.annotation.OperationLog;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 操作名称解析器。
 * <p>
 * 将“接口路径 + HTTP 方法 + 场景参数”转换为可读的中文操作名，
 * 避免操作日志拦截器承担大量路径分支判断。
 */
@Component
public class OperationNameResolver {

    private static final Map<String, String> MODULE_NAMES = buildModuleNames();

    public String resolve(HandlerMethod handlerMethod, HttpServletRequest request) {
        String requestOperation = resolveRequestOperation(request);
        if (requestOperation != null) {
            return requestOperation;
        }
        OperationLog methodLog = handlerMethod.getMethodAnnotation(OperationLog.class);
        if (methodLog != null) {
            return methodLog.value();
        }
        OperationLog typeLog = handlerMethod.getBeanType().getAnnotation(OperationLog.class);
        return typeLog == null ? null : typeLog.value();
    }

    private String resolveRequestOperation(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String usage = request.getParameter("usage");
        String method = request.getMethod();
        if ("relationOptions".equals(usage)) {
            return null;
        }
        String authOperation = resolveAuthOperation(uri, method);
        if (authOperation != null) {
            return authOperation;
        }
        String infrastructureOperation = resolveInfrastructureOperation(uri);
        if (infrastructureOperation != null) {
            return infrastructureOperation;
        }
        String toolOperation = resolveToolOperation(uri, method);
        if (toolOperation != null) {
            return toolOperation;
        }
        if (uri.startsWith("/logistics/modules/")) {
            String[] parts = uri.substring("/logistics/modules/".length()).split("/");
            String module = parts.length == 0 ? "" : parts[0];
            if ("permissionConfig".equals(usage)) {
                return switch (module) {
                    case "users" -> "权限配置-加载用户候选列表";
                    case "roles" -> "权限配置-加载角色候选列表";
                    default -> null;
                };
            }
            return moduleName(module) + "-" + moduleActionName(method, parts);
        }
        if (uri.startsWith("/logistics/excel/export/")) {
            String module = uri.substring("/logistics/excel/export/".length()).split("/")[0];
            return moduleName(module) + "-导出Excel";
        }
        return switch (uri) {
            case "/logistics/excel/import/customers" -> "客户管理-导入Excel";
            case "/logistics/dashboard" -> "运营看板-查询统计";
            case "/logistics/statistics/order-trend" -> "运营看板-查询订单趋势";
            case "/logistics/statistics/income-trend" -> "运营看板-查询收入趋势";
            case "/logistics/orders/search" -> "运单管理-搜索订单";
            case "/logistics/orders" -> "GET".equalsIgnoreCase(method) ? "运单管理-查询近期订单" : null;
            default -> uri.matches("/logistics/orders/[^/]+") && "GET".equalsIgnoreCase(method)
                    ? "运单管理-查看订单详情"
                    : null;
        };
    }

    private String resolveAuthOperation(String uri, String method) {
        if (uri.equals("/auth/login") && "POST".equalsIgnoreCase(method)) {
            return "用户登录";
        }
        if (uri.equals("/auth/logout") && "POST".equalsIgnoreCase(method)) {
            return "用户退出";
        }
        if (uri.equals("/auth/profile") && "PUT".equalsIgnoreCase(method)) {
            return "个人设置-修改资料";
        }
        if (uri.equals("/auth/password") && "PUT".equalsIgnoreCase(method)) {
            return "个人设置-修改密码";
        }
        if (uri.matches("/auth/login-conflicts/[^/]+/reject") && "POST".equalsIgnoreCase(method)) {
            return "登录冲突-保持当前会话";
        }
        if (uri.matches("/auth/login-conflicts/[^/]+/accept") && "POST".equalsIgnoreCase(method)) {
            return "登录冲突-允许新会话";
        }
        return null;
    }

    private String resolveInfrastructureOperation(String uri) {
        return switch (uri) {
            case "/infra/status" -> "资源中心-查看中间件状态";
            case "/infra/nacos/services" -> "资源中心-查看Nacos服务";
            case "/infra/nacos/instances" -> "资源中心-查看Nacos实例";
            case "/infra/sentinel/ping" -> "资源中心-测试Sentinel";
            case "/infra/elasticsearch/client" -> "资源中心-测试Elasticsearch";
            case "/infra/redis/client" -> "资源中心-测试Redis";
            case "/infra/rabbitmq/client" -> "资源中心-测试RabbitMQ";
            default -> null;
        };
    }

    private String resolveToolOperation(String uri, String method) {
        if (uri.equals("/demo-users") && "GET".equalsIgnoreCase(method)) {
            return "练习用户-查询列表";
        }
        if (uri.equals("/demo-users/detail") && "GET".equalsIgnoreCase(method)) {
            return "练习用户-查看详情";
        }
        if (uri.equals("/demo-users") && "POST".equalsIgnoreCase(method)) {
            return "练习用户-新增记录";
        }
        if (uri.equals("/bloom-filter/items") && "GET".equalsIgnoreCase(method)) {
            return "布隆过滤器-检查元素";
        }
        if (uri.equals("/bloom-filter/items") && "POST".equalsIgnoreCase(method)) {
            return "布隆过滤器-写入元素";
        }
        if (uri.equals("/rabbitmq/messages") && "POST".equalsIgnoreCase(method)) {
            return "RabbitMQ-发送测试消息";
        }
        return null;
    }

    private String moduleActionName(String method, String[] parts) {
        if ("GET".equalsIgnoreCase(method)) {
            return "查询列表";
        }
        if (parts.length >= 3 && "delete".equals(parts[2])) {
            return "删除记录";
        }
        if ("POST".equalsIgnoreCase(method) && parts.length == 1) {
            return "新增记录";
        }
        return "编辑记录";
    }

    private String moduleName(String module) {
        return MODULE_NAMES.getOrDefault(module, module);
    }

    private static Map<String, String> buildModuleNames() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("orders", "运单管理");
        map.put("customers", "客户管理");
        map.put("waybills", "运单中心");
        map.put("dispatches", "调度管理");
        map.put("tasks", "运输任务");
        map.put("tracks", "物流轨迹");
        map.put("drivers", "司机管理");
        map.put("vehicles", "车辆管理");
        map.put("exceptions", "异常管理");
        map.put("fees", "费用结算");
        map.put("users", "用户管理");
        map.put("roles", "角色管理");
        map.put("operationLogs", "操作日志");
        map.put("files", "上传文件");
        return map;
    }
}
