package jimmy.logistics.config;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.annotation.OperationLog;
import jimmy.logistics.mapper.OperationLogMapper;
import jimmy.logistics.util.ColumnExistenceChecker;
import jimmy.util.LogMaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

@Slf4j
@Component
public class OperationLogInterceptor implements HandlerInterceptor {

    private static final String OPERATION_USERNAME_ATTRIBUTE = "operationLogUsername";
    private static final String START_TIME_ATTRIBUTE = "operationLogStartTime";
    private static final String OPERATION_ID_ATTRIBUTE = "operationLogOperationId";
    private static final String TRACE_ID_ATTRIBUTE = "operationLogTraceId";
    private static final java.util.Map<String, String> MODULE_NAMES = buildModuleNames();

    private final OperationLogMapper operationLogMapper;
    private final ColumnExistenceChecker columnChecker;
    private final CompactSnowflakeIdGenerator idGenerator;

    public OperationLogInterceptor(OperationLogMapper operationLogMapper,
                                   ColumnExistenceChecker columnChecker,
                                   CompactSnowflakeIdGenerator idGenerator) {
        this.operationLogMapper = operationLogMapper;
        this.columnChecker = columnChecker;
        this.idGenerator = idGenerator;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = resolveTraceId(request);
        String operationId = String.valueOf(idGenerator.nextId());
        String username = currentUsername();
        // 每次请求生成唯一操作 ID，并与 traceId 一起写入响应头，方便前端反馈问题时反查日志。
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        request.setAttribute(OPERATION_ID_ATTRIBUTE, operationId);
        request.setAttribute(OPERATION_USERNAME_ATTRIBUTE, username);
        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
        response.setHeader("X-Trace-Id", traceId);
        response.setHeader("X-Operation-Id", operationId);

        MDC.put("traceId", traceId);
        MDC.put("operationId", operationId);
        // MDC 字段会被 logback 写入 JSON 日志，业务日志和操作日志可通过 traceId 串起来。
        MDC.put("userId", currentUserId());
        MDC.put("userCode", currentUserCode());
        MDC.put("usernameMasked", LogMaskUtils.maskAccount(username));
        MDC.put("roleCode", currentRoleCode());
        MDC.put("requestUri", request.getRequestURI());
        MDC.put("requestMethod", request.getMethod());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception exception) {
        try {
            if (!(handler instanceof HandlerMethod)) {
                return;
            }

            HandlerMethod handlerMethod = (HandlerMethod) handler;
            String operation = resolveOperation(handlerMethod, request);
            if (operation == null && !isBusinessWrite(request)) {
                return;
            }
            if (operation == null) {
                // 未显式标注 @OperationLog 的写接口仍然记录，避免关键变更没有审计痕迹。
                operation = "业务接口调用";
            }

            String username = String.valueOf(request.getAttribute(OPERATION_USERNAME_ATTRIBUTE));
            if ("anonymous".equals(username)) {
                username = currentUsername();
            }
            String traceId = String.valueOf(request.getAttribute(TRACE_ID_ATTRIBUTE));
            String operationId = String.valueOf(request.getAttribute(OPERATION_ID_ATTRIBUTE));
            String status = exception == null && response.getStatus() < 400 ? "SUCCESS" : "FAILED";
            long costMs = System.currentTimeMillis() - (Long) request.getAttribute(START_TIME_ATTRIBUTE);
            String errorMessage = exception == null ? null : exception.getMessage();
            MDC.put("module", firstPath(request.getRequestURI()));
            MDC.put("operation", operation);
            MDC.put("costMs", String.valueOf(costMs));
            MDC.put("result", status);

            try {
                // 优先写入包含 traceId、operationId、耗时、异常信息等扩展字段的新日志结构。
                insertCompatibleOperationLog(username, operation, request, status, costMs, traceId, operationId, errorMessage);
                log.info("操作日志已记录，operationId={}, traceId={}, userId={}, username={}, operation={}, uri={}, method={}, status={}, costMs={}",
                        operationId, traceId, currentUserId(), LogMaskUtils.maskAccount(username), operation,
                        request.getRequestURI(), request.getMethod(), status, costMs);
            } catch (RuntimeException logException) {
                // 如果本地库还没执行增量脚本，回退到旧字段写法，保证业务接口不因审计字段缺失而失败。
                insertLegacyLog(username, operation, request, status);
                log.warn("操作日志扩展字段写入失败，已回退基础日志，operation={}, reason={}", operation, logException.getMessage());
            }
        } finally {
            clearRequestMdc();
        }
    }

    private void insertCompatibleOperationLog(String username, String operation, HttpServletRequest request,
                                              String status, long costMs, String traceId, String operationId,
                                              String errorMessage) {
        if (!columnChecker.hasColumn("sys_operation_log", "operation_id")) {
            insertLegacyLog(username, operation, request, status);
            return;
        }
        if (columnChecker.hasColumn("sys_operation_log", "error_message")) {
            operationLogMapper.insertOperationLog(
                    idGenerator.nextId(), operationId, traceId, currentUserId(), currentUserCode(), username,
                    currentRoleCode(), operation, request.getRequestURI(), request.getMethod(), status, costMs, errorMessage
            );
            return;
        }
        operationLogMapper.insertOperationLogWithoutErrorMessage(
                idGenerator.nextId(), operationId, traceId, currentUserId(), currentUserCode(), username,
                currentRoleCode(), operation, request.getRequestURI(), request.getMethod(), status, costMs
        );
    }

    private void insertLegacyLog(String username, String operation, HttpServletRequest request, String status) {
        try {
            operationLogMapper.insertLegacyOperationLog(
                    idGenerator.nextId(),
                    username,
                    operation,
                    request.getRequestURI(),
                    request.getMethod(),
                    status
            );
        } catch (RuntimeException ignored) {
            log.warn("基础操作日志写入失败，operation={}", operation);
        }
    }

    private String resolveOperation(HandlerMethod handlerMethod, HttpServletRequest request) {
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
                if ("users".equals(module)) {
                    return "权限配置-加载用户候选列表";
                }
                if ("roles".equals(module)) {
                    return "权限配置-加载角色候选列表";
                }
            }
            return moduleName(module) + "-" + moduleActionName(method, parts);
        }
        if (uri.startsWith("/logistics/excel/export/")) {
            String module = uri.substring("/logistics/excel/export/".length()).split("/")[0];
            return moduleName(module) + "-导出Excel";
        }
        if (uri.equals("/logistics/excel/import/customers")) {
            return "客户管理-导入Excel";
        }
        if (uri.equals("/logistics/dashboard")) {
            return "运营看板-查询统计";
        }
        if (uri.equals("/logistics/statistics/order-trend")) {
            return "运营看板-查询订单趋势";
        }
        if (uri.equals("/logistics/statistics/income-trend")) {
            return "运营看板-查询收入趋势";
        }
        if (uri.equals("/logistics/orders/search")) {
            return "运单管理-搜索订单";
        }
        if (uri.matches("/logistics/orders/[^/]+") && "GET".equalsIgnoreCase(method)) {
            return "运单管理-查看订单详情";
        }
        if (uri.equals("/logistics/orders") && "GET".equalsIgnoreCase(method)) {
            return "运单管理-查询近期订单";
        }
        return null;
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
        if (uri.equals("/infra/status")) {
            return "资源中心-查看中间件状态";
        }
        if (uri.equals("/infra/nacos/services")) {
            return "资源中心-查看Nacos服务";
        }
        if (uri.equals("/infra/nacos/instances")) {
            return "资源中心-查看Nacos实例";
        }
        if (uri.equals("/infra/sentinel/ping")) {
            return "资源中心-测试Sentinel";
        }
        if (uri.equals("/infra/elasticsearch/client")) {
            return "资源中心-测试Elasticsearch";
        }
        if (uri.equals("/infra/redis/client")) {
            return "资源中心-测试Redis";
        }
        if (uri.equals("/infra/rabbitmq/client")) {
            return "资源中心-测试RabbitMQ";
        }
        return null;
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

    private static java.util.Map<String, String> buildModuleNames() {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
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

    private boolean isBusinessWrite(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        return isAuditedBusinessPath(uri)
                && ("POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method));
    }

    private boolean isAuditedBusinessPath(String uri) {
        return uri.startsWith("/logistics/")
                || uri.startsWith("/auth/")
                || uri.startsWith("/system/")
                || uri.startsWith("/infra/")
                || uri.startsWith("/demo-users")
                || uri.startsWith("/bloom-filter/")
                || uri.startsWith("/rabbitmq/");
    }

    private String currentUsername() {
        Object loginId = currentLoginId();
        if (loginId == null) {
            return "anonymous";
        }
        return String.valueOf(StpUtil.getSession().get("username", loginId));
    }

    private String currentUserId() {
        Object loginId = currentLoginId();
        return loginId == null ? "" : String.valueOf(loginId);
    }

    private String currentUserCode() {
        Object loginId = currentLoginId();
        if (loginId == null) {
            return "";
        }
        return String.valueOf(StpUtil.getSession().get("userCode", ""));
    }

    private String currentRoleCode() {
        Object loginId = currentLoginId();
        if (loginId == null) {
            return "";
        }
        return String.valueOf(StpUtil.getSession().get("roleCode", ""));
    }

    private Object currentLoginId() {
        try {
            return StpUtil.getLoginIdDefaultNull();
        } catch (RuntimeException exception) {
            // 单元测试或极端非 Web 调用场景下可能没有 Sa-Token 上下文，日志按匿名请求兜底。
            return null;
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId != null && !traceId.trim().isEmpty()) {
            return traceId.trim();
        }
        String currentTraceId = MDC.get("traceId");
        // 没有上游 traceId 时本服务生成一个，保证一次请求内日志可以被统一检索。
        return currentTraceId == null || currentTraceId.trim().isEmpty() ? UUID.randomUUID().toString().replace("-", "") : currentTraceId;
    }

    private void clearRequestMdc() {
        MDC.remove("traceId");
        MDC.remove("operationId");
        MDC.remove("userId");
        MDC.remove("userCode");
        MDC.remove("usernameMasked");
        MDC.remove("roleCode");
        MDC.remove("requestUri");
        MDC.remove("requestMethod");
        MDC.remove("module");
        MDC.remove("operation");
        MDC.remove("costMs");
        MDC.remove("result");
    }

    private String firstPath(String uri) {
        if (uri == null || uri.length() <= 1) {
            return "system";
        }
        String[] parts = uri.substring(1).split("/");
        return parts.length == 0 ? "system" : parts[0];
    }
}
