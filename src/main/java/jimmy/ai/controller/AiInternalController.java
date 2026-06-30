package jimmy.ai.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jimmy.auth.mapper.AuthMapper;
import jimmy.ai.model.AiToolExecuteRequest;
import jimmy.ai.service.ToolExecutorService;
import jimmy.ai.service.ToolRegistryService;
import jimmy.system.service.SystemPermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 内部接口 —— 专供 Python AI 服务（127.0.0.1:8001）回调使用。
 * <p>
 * 不走 Sa-Token 认证（内部信任域），通过 X-Internal-User 请求头传递
 * 用户标识和权限信息。Python 在发起对话时将用户上下文注入该头，
 * Java 内部端点解析后设置 SSE 上下文并执行工具调用。
 */
@Slf4j
@RestController
@RequestMapping("/ai/internal")
public class AiInternalController {

    private static final String HEADER_INTERNAL_USER = "X-Internal-User";
    private static final String HEADER_INTERNAL_SECRET = "X-Internal-Secret";

    private final ToolRegistryService toolRegistryService;
    private final ToolExecutorService toolExecutorService;
    private final AuthMapper authMapper;
    private final SystemPermissionService systemPermissionService;
    private final ObjectMapper objectMapper;
    private final String internalSharedSecret;
    private final boolean prodProfileActive;
    private final boolean pythonEnabled;

    public AiInternalController(ToolRegistryService toolRegistryService,
                                 ToolExecutorService toolExecutorService,
                                 AuthMapper authMapper,
                                 SystemPermissionService systemPermissionService,
                                 ObjectMapper objectMapper,
                                 Environment environment,
                                 @Value("${app.ai.python.enabled:false}") boolean pythonEnabled) {
        this.toolRegistryService = toolRegistryService;
        this.toolExecutorService = toolExecutorService;
        this.authMapper = authMapper;
        this.systemPermissionService = systemPermissionService;
        this.objectMapper = objectMapper;
        this.internalSharedSecret = environment.getProperty("app.ai.internal.shared-secret", "").trim();
        this.prodProfileActive = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile));
        this.pythonEnabled = pythonEnabled;
        if ((this.prodProfileActive || this.pythonEnabled) && !StringUtils.hasText(this.internalSharedSecret)) {
            throw new IllegalStateException("启用 Python AI 或 prod 环境时必须配置 app.ai.internal.shared-secret / AI_INTERNAL_SHARED_SECRET");
        }
    }

    /**
     * 健康检查端点。
     *
     * @return {"status": "ok"}
     */
    @GetMapping("/health")
    public Map<String, Object> health(HttpServletRequest request, HttpServletResponse response) {
        if (!authorizeInternalRequest(request, response)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return errorResponse("AI 内部接口认证失败");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        return result;
    }

    /**
     * 获取当前用户可用的工具注册表。
     * <p>
     * 从 X-Internal-User 头解析用户标识和权限，构建 OpenAI function-calling 格式的工具列表。
     *
     * @param request HTTP 请求（读取 X-Internal-User 头）
     * @return 工具定义列表
     */
    @GetMapping("/tools/registry")
    public Map<String, Object> toolsRegistry(HttpServletRequest request, HttpServletResponse response) {
        try {
            if (!authorizeInternalRequest(request, response)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return errorResponse("AI 内部接口认证失败");
            }
            InternalUserContext context = resolveInternalUser(request.getHeader(HEADER_INTERNAL_USER));
            if (context == null || !StringUtils.hasText(context.userId())) {
                return errorResponse("缺少或无法解析 X-Internal-User 请求头");
            }

            List<Map<String, Object>> tools = toolRegistryService.buildRegistry(context.userId(), context.permissions());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("tools", tools);
            result.put("count", tools.size());
            return result;
        } catch (Exception e) {
            log.error("获取工具注册表失败, reason={}", e.getMessage(), e);
            return errorResponse("获取工具注册表失败，请稍后重试");
        }
    }

    /**
     * 执行工具调用。
     * <p>
     * 从 X-Internal-User 头解析用户上下文，执行指定的工具并返回结果。
     *
     * @param requestBody 工具执行请求（toolName + arguments）
     * @param request     HTTP 请求（读取 X-Internal-User 头）
     * @return 标准化的工具执行结果 Map
     */
    @PostMapping("/tool/execute")
    public Map<String, Object> toolExecute(@RequestBody AiToolExecuteRequest requestBody,
                                            HttpServletRequest request,
                                            HttpServletResponse response) {
        try {
            if (!authorizeInternalRequest(request, response)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return errorResponse("AI 内部接口认证失败");
            }
            InternalUserContext context = resolveInternalUser(request.getHeader(HEADER_INTERNAL_USER));
            if (context == null || !StringUtils.hasText(context.userId())) {
                return errorResponse("缺少或无法解析 X-Internal-User 请求头");
            }

            if (requestBody == null || !StringUtils.hasText(requestBody.toolName())) {
                return errorResponse("缺少 toolName 参数");
            }

            return toolExecutorService.execute(
                    context.userId(),
                    context.permissions(),
                    context.userCode(),
                    context.roleCode(),
                    context.customerId(),
                    context.loginSessionId(),
                    context.conversationId(),
                    requestBody.toolName(),
                    requestBody.arguments()
            );
        } catch (Exception e) {
            log.error("工具执行失败, reason={}", e.getMessage(), e);
            return errorResponse("工具执行失败，请稍后重试");
        }
    }

    /**
     * 解析 X-Internal-User 请求头中的 JSON。
     * <p>
     * 格式：{"userId": "...", "permissions": ["...", "..."], "conversationId": "..."}
     */
    private InternalUserContext parseInternalUser(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            log.warn("X-Internal-User 请求头为空");
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(headerValue, new TypeReference<>() {});
            String userId = map.get("userId") != null ? String.valueOf(map.get("userId")) : null;
            String loginSessionId = map.get("loginSessionId") != null ? String.valueOf(map.get("loginSessionId")) : "";
            String conversationId = map.get("conversationId") != null ? String.valueOf(map.get("conversationId")) : "AI_INTERNAL";
            return new InternalUserContext(userId, "", "", "", loginSessionId, conversationId, Collections.emptyList());
        } catch (Exception e) {
            log.warn("解析 X-Internal-User 失败, headerValue={}, reason={}", headerValue, e.getMessage());
            return null;
        }
    }

    private InternalUserContext resolveInternalUser(String headerValue) {
        InternalUserContext parsed = parseInternalUser(headerValue);
        if (parsed == null || !StringUtils.hasText(parsed.userId())) {
            return parsed;
        }
        try {
            Long userId = Long.valueOf(parsed.userId());
            Map<String, Object> user = authMapper.findLoginUserById(userId);
            if (user == null || user.isEmpty()) {
                log.warn("AI internal request rejected: user not found, userId={}", parsed.userId());
                return null;
            }
            Integer status = toInteger(user.get("status"));
            if (status == null || status != 1) {
                log.warn("AI internal request rejected: disabled user, userId={}", parsed.userId());
                return null;
            }
            Long roleId = toLong(user.get("roleId"));
            List<String> permissions = systemPermissionService.effectivePermissionCodes(userId, roleId);
            String userCode = stringValue(user.get("userCode"));
            String roleCode = stringValue(user.get("roleCode"));
            String customerId = stringValue(user.get("customerId"));
            return new InternalUserContext(parsed.userId(), userCode, roleCode, customerId,
                    parsed.loginSessionId(), parsed.conversationId(), permissions == null ? List.of() : permissions);
        } catch (Exception e) {
            log.warn("AI internal user context resolve failed, userId={}, reason={}", parsed.userId(), e.getMessage());
            return null;
        }
    }

    private Map<String, Object> errorResponse(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("error", message);
        return result;
    }

    private boolean isLoopbackRequest(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return "127.0.0.1".equals(remoteAddr)
                || "0:0:0:0:0:0:0:1".equals(remoteAddr)
                || "::1".equals(remoteAddr)
                || "localhost".equalsIgnoreCase(remoteAddr);
    }

    private boolean authorizeInternalRequest(HttpServletRequest request, HttpServletResponse response) {
        if (!StringUtils.hasText(internalSharedSecret)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            log.warn("AI internal request rejected: shared secret missing, remoteAddr={}, prodProfile={}, pythonEnabled={}",
                    request.getRemoteAddr(), prodProfileActive, pythonEnabled);
            return false;
        }

        String providedSecret = request.getHeader(HEADER_INTERNAL_SECRET);
        if (!constantTimeEquals(internalSharedSecret, providedSecret)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            log.warn("AI internal request rejected: invalid shared secret, remoteAddr={}", request.getRemoteAddr());
            return false;
        }
        return true;
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (!StringUtils.hasText(actual)) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Long toLong(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Integer toInteger(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private record InternalUserContext(String userId,
                                       String userCode,
                                       String roleCode,
                                       String customerId,
                                       String loginSessionId,
                                       String conversationId,
                                       List<String> permissions) {
    }
}
