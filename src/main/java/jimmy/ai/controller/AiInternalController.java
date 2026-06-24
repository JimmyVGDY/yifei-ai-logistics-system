package jimmy.ai.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jimmy.ai.model.AiToolExecuteRequest;
import jimmy.ai.service.ToolExecutorService;
import jimmy.ai.service.ToolRegistryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    private final ToolRegistryService toolRegistryService;
    private final ToolExecutorService toolExecutorService;
    private final ObjectMapper objectMapper;

    public AiInternalController(ToolRegistryService toolRegistryService,
                                 ToolExecutorService toolExecutorService,
                                 ObjectMapper objectMapper) {
        this.toolRegistryService = toolRegistryService;
        this.toolExecutorService = toolExecutorService;
        this.objectMapper = objectMapper;
    }

    /**
     * 健康检查端点。
     *
     * @return {"status": "ok"}
     */
    @GetMapping("/health")
    public Map<String, Object> health(HttpServletRequest request, HttpServletResponse response) {
        if (!isLoopbackRequest(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return errorResponse("AI 内部接口仅允许本机调用");
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
            if (!isLoopbackRequest(request)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return errorResponse("AI 内部接口仅允许本机调用");
            }
            InternalUserContext context = parseInternalUser(request.getHeader(HEADER_INTERNAL_USER));
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
            return errorResponse("获取工具注册表失败：" + e.getMessage());
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
            if (!isLoopbackRequest(request)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return errorResponse("AI 内部接口仅允许本机调用");
            }
            InternalUserContext context = parseInternalUser(request.getHeader(HEADER_INTERNAL_USER));
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
            return errorResponse("工具执行失败：" + e.getMessage());
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
            String userCode = map.get("userCode") != null ? String.valueOf(map.get("userCode")) : "";
            String roleCode = map.get("roleCode") != null ? String.valueOf(map.get("roleCode")) : "";
            String customerId = map.get("customerId") != null ? String.valueOf(map.get("customerId")) : "";
            String loginSessionId = map.get("loginSessionId") != null ? String.valueOf(map.get("loginSessionId")) : "";
            String conversationId = map.get("conversationId") != null ? String.valueOf(map.get("conversationId")) : "AI_INTERNAL";
            @SuppressWarnings("unchecked")
            List<String> permissions = map.get("permissions") instanceof List<?> list
                    ? list.stream().map(String::valueOf).toList()
                    : Collections.emptyList();
            return new InternalUserContext(userId, userCode, roleCode, customerId, loginSessionId, conversationId, permissions);
        } catch (Exception e) {
            log.warn("解析 X-Internal-User 失败, headerValue={}, reason={}", headerValue, e.getMessage());
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

    private record InternalUserContext(String userId,
                                       String userCode,
                                       String roleCode,
                                       String customerId,
                                       String loginSessionId,
                                       String conversationId,
                                       List<String> permissions) {
    }
}
