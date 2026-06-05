package jimmy.ai.util;

import java.util.Collections;
import java.util.List;

/**
 * SSE 异步线程中的登录上下文传递。
 * <p>
 * SSE 端点使用独立线程池处理，Sa-Token 的 ThreadLocal 上下文不会自动跨线程传递。
 * Controller 在分发异步任务前捕获当前登录用户标识和权限列表，异步线程通过本类写入，
 * 下游服务（如 {@code AiMemoryService}、{@code AiAuditLogService}、{@code AiReadonlyQueryService}）优先读取此值。
 * <p>
 * 同步接口（POST /ai/chat）在 HTTP 线程执行，无需使用本类，仍直接调用 StpUtil。
 */
public final class SseChatContext {

    private static final ThreadLocal<String> LOGIN_ID = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> PERMISSIONS = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE_CODE = new ThreadLocal<>();
    private static final ThreadLocal<String> CUSTOMER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_CODE = new ThreadLocal<>();

    private SseChatContext() {
    }

    /**
     * 在异步任务开始时设置当前线程的登录用户标识。
     * 如果还需要权限/角色/客户等信息，请使用 {@link #setLoginIdAndPermissions(String, List)} 配合各独立 setter。
     */
    public static void setLoginId(String loginId) {
        LOGIN_ID.set(loginId);
    }

    /**
     * 在异步任务开始时设置登录用户标识和权限列表，
     * 供下游服务在 SSE 异步线程中做权限校验（替代 StpUtil.hasPermission）。
     */
    public static void setLoginIdAndPermissions(String loginId, List<String> permissions) {
        LOGIN_ID.set(loginId);
        PERMISSIONS.set(permissions != null ? permissions : Collections.emptyList());
    }

    // ---- Session 属性（异步线程中 StpUtil.getSession() 不可用，需由 Controller 预捕获） ----

    public static void setRoleCode(String roleCode) {
        ROLE_CODE.set(roleCode);
    }

    public static void setCustomerId(String customerId) {
        CUSTOMER_ID.set(customerId);
    }

    public static void setUsername(String username) {
        USERNAME.set(username);
    }

    public static void setUserCode(String userCode) {
        USER_CODE.set(userCode);
    }

    public static String getLoginId() {
        return LOGIN_ID.get();
    }

    public static String getRoleCode() {
        return ROLE_CODE.get();
    }

    public static String getCustomerId() {
        return CUSTOMER_ID.get();
    }

    public static String getUsername() {
        return USERNAME.get();
    }

    public static String getUserCode() {
        return USER_CODE.get();
    }

    public static boolean hasPermission(String permission) {
        List<String> perms = PERMISSIONS.get();
        return perms != null && perms.contains(permission);
    }

    /**
     * 异步任务结束时清理，避免线程池复用时的数据串扰。
     */
    public static void clear() {
        LOGIN_ID.remove();
        PERMISSIONS.remove();
        ROLE_CODE.remove();
        CUSTOMER_ID.remove();
        USERNAME.remove();
        USER_CODE.remove();
    }
}
