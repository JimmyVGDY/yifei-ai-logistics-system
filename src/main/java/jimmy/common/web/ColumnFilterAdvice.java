package jimmy.common.web;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.common.model.ApiResponse;
import jimmy.common.model.PageResult;
import jimmy.system.config.StandardColumnRegistry;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 列级字段过滤拦截器。
 * <p>
 * 仅处理标注了 {@link ColumnScope} 的接口，并根据当前登录用户的列权限移除无权查看的字段。
 * 设计上保持“默认安全”：只要模块已经接入标准列注册表，用户没有列权限时就返回空字段，
 * 避免旧会话、权限同步失败或前端兜底逻辑导致敏感字段被展示。
 */
@RestControllerAdvice
public class ColumnFilterAdvice implements ResponseBodyAdvice<Object> {

    private static final String COLUMN_PREFIX = ":column:";

    private static final Map<String, String> MODULE_PATH_TO_CODE;

    static {
        Map<String, String> map = new LinkedHashMap<>();
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
        MODULE_PATH_TO_CODE = Collections.unmodifiableMap(map);
    }

    private final StandardColumnRegistry columnRegistry;

    public ColumnFilterAdvice(StandardColumnRegistry columnRegistry) {
        this.columnRegistry = columnRegistry;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return returnType.hasMethodAnnotation(ColumnScope.class);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        ColumnScope annotation = returnType.getMethodAnnotation(ColumnScope.class);
        if (annotation == null || body == null) {
            return body;
        }

        String module = resolveModule(annotation, request);
        if (!hasColumnPolicy(module)) {
            return body;
        }

        if (!(body instanceof ApiResponse<?> apiResponse)) {
            return body;
        }

        Object data = apiResponse.getData();
        if (data == null) {
            return body;
        }

        Set<String> allowed = getAllowedColumns(module);
        if (allowed.isEmpty() && suppressEmptyPage(apiResponse, data)) {
            return body;
        }
        String[] paths = annotation.paths();
        if (paths.length > 0) {
            filterByPaths(data, paths, allowed);
        } else {
            filterRecursive(data, allowed);
        }

        return body;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean suppressEmptyPage(ApiResponse<?> response, Object data) {
        if (!(data instanceof PageResult<?> pageResult)) {
            return false;
        }
        ((ApiResponse) response).setData(new PageResult<>(List.of(), pageResult.page(), pageResult.pageSize(), 0));
        return true;
    }

    private void filterByPaths(Object data, String[] paths, Set<String> allowed) {
        for (String path : paths) {
            Object target = resolvePath(data, path);
            if (target != null) {
                filterRecursive(target, allowed);
            }
        }
    }

    private Object resolvePath(Object data, String path) {
        if (data instanceof Map<?, ?> map) {
            return map.get(path);
        }
        try {
            String getter = "get" + Character.toUpperCase(path.charAt(0)) + path.substring(1);
            return data.getClass().getMethod(getter).invoke(data);
        } catch (Exception e) {
            return null;
        }
    }

    private void filterRecursive(Object value, Set<String> allowed) {
        if (value instanceof Map<?, ?> map) {
            List<Object> toRemove = new ArrayList<>();
            for (Object key : map.keySet()) {
                if (!allowed.contains(String.valueOf(key))) {
                    toRemove.add(key);
                }
            }
            toRemove.forEach(map::remove);
            for (Object item : map.values()) {
                filterRecursive(item, allowed);
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                filterRecursive(item, allowed);
            }
        }
    }

    private String resolveModule(ColumnScope annotation, ServerHttpRequest request) {
        String module = annotation.module();
        if (!"__from_uri__".equals(module)) {
            return module;
        }
        String path = request.getURI().getPath();
        if (!path.contains("/logistics/modules/")) {
            return "";
        }
        String[] parts = path.substring(path.indexOf("/logistics/modules/") + "/logistics/modules/".length()).split("/");
        if (parts.length == 0) {
            return "";
        }
        return MODULE_PATH_TO_CODE.getOrDefault(parts[0], parts[0]);
    }

    private Set<String> getAllowedColumns(String module) {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            return Set.of();
        }
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) StpUtil.getSessionByLoginId(loginId).get("permissions");
        if (permissions == null || permissions.isEmpty()) {
            return Set.of();
        }

        Set<String> standardColumns = standardColumnNames(module);
        String prefix = module + COLUMN_PREFIX;
        return permissions.stream()
                .filter(permission -> permission.startsWith(prefix))
                .map(permission -> permission.substring(prefix.length()))
                .filter(standardColumns::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean hasColumnPolicy(String module) {
        return module != null && !module.isEmpty() && !columnRegistry.columns(module).isEmpty();
    }

    private Set<String> standardColumnNames(String module) {
        return columnRegistry.columns(module).stream()
                .map(StandardColumnRegistry.ColumnDef::fieldName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
