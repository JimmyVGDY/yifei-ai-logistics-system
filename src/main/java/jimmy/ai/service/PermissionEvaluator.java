package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.util.SseChatContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 权限评估器 —— 统一封装 SSE 异步线程和同步 HTTP 线程的权限判断。
 * <p>
 * 替代 8 个服务中重复的 {@code SseChatContext.getLoginId() != null ? SseChatContext.hasPermission() : StpUtil.hasPermission()} 模式。
 * <p>
 * 规则：SSE 线程有有效的 loginId 时优先读 {@link SseChatContext} 的权限快照，
 * 否则回退到 {@link StpUtil}（同步 HTTP 线程）。
 */
@Component
public class PermissionEvaluator {

    /**
     * 判断当前线程上下文中的用户是否拥有指定权限。
     *
     * @param permission 权限码，如 "order:query"
     * @return true 表示有权限
     */
    public boolean hasPermission(String permission) {
        if (!StringUtils.hasText(permission)) {
            return false;
        }
        String sseLoginId = SseChatContext.getLoginId();
        if (StringUtils.hasText(sseLoginId) && !"null".equalsIgnoreCase(sseLoginId)) {
            return SseChatContext.hasPermission(permission);
        }
        return StpUtil.hasPermission(permission);
    }
}
