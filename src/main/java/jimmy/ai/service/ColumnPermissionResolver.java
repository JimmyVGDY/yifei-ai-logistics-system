package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.util.SseChatContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 列权限解析器 —— 从用户扁平权限码列表中提取模块的可见列名。
 * <p>
 * 兼容两种线程环境：
 * <ol>
 *   <li>SSE 异步线程：从 {@link SseChatContext#getPermissions()} 读取</li>
 *   <li>同步 HTTP 线程：从 {@code StpUtil.getSession().get("permissions")} 读取</li>
 * </ol>
 * <p>
 * 列权限码格式：{@code {module}:column:{fieldName}}，如 {@code order:column:total_amount}。
 * 本工具提取 {@code :column:} 后的字段名（snake_case），与 {@code StandardColumnRegistry} 的 fieldName 一致。
 */
@Component
public class ColumnPermissionResolver {

    private static final String COLUMN_PERMISSION_MARKER = ":column:";

    /**
     * 获取当前用户对指定模块的可见列集合（snake_case 字段名）。
     *
     * @param module 后端模块码，如 "order", "fee", "system:log"
     * @return 可见列名集合，无权限或无法获取时返回空 Set
     */
    public Set<String> allowedColumns(String module) {
        List<String> permissions = resolvePermissions();
        if (permissions == null || permissions.isEmpty()) {
            return Collections.emptySet();
        }
        String prefix = module + COLUMN_PERMISSION_MARKER;
        Set<String> result = new LinkedHashSet<>();
        for (String perm : permissions) {
            if (perm.startsWith(prefix)) {
                result.add(perm.substring(prefix.length()));
            }
        }
        return result;
    }

    /**
     * 获取当前用户所有模块列权限的全局并集。
     * 用于无法确定结果集属于哪个模块的场景（如 AI 自定义 SQL 跨表查询）。
     *
     * @return 所有模块可见列名的并集
     */
    public Set<String> allGlobalColumns() {
        List<String> permissions = resolvePermissions();
        if (permissions == null || permissions.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String perm : permissions) {
            int idx = perm.indexOf(COLUMN_PERMISSION_MARKER);
            if (idx > 0) {
                result.add(perm.substring(idx + COLUMN_PERMISSION_MARKER.length()));
            }
        }
        return result;
    }

    /**
     * 解析当前线程环境下的权限码列表。
     * SSE 线程优先，同步 HTTP 线程 fallback。
     */
    private List<String> resolvePermissions() {
        // 1. SSE 异步线程：从 SseChatContext 读取（Controller 在 spawn 线程前预捕获）
        List<String> ssePermissions = SseChatContext.getPermissions();
        if (ssePermissions != null && !ssePermissions.isEmpty()) {
            return ssePermissions;
        }

        // 2. 同步 HTTP 线程：从 Sa-Token 会话读取
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId == null) {
                return Collections.emptyList();
            }
            @SuppressWarnings("unchecked")
            List<String> permissions = (List<String>) StpUtil.getSessionByLoginId(loginId).get("permissions");
            return permissions != null ? permissions : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
