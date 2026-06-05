package jimmy.ai.util;

/**
 * SSE 异步线程中的登录上下文传递。
 * <p>
 * SSE 端点使用独立线程池处理，Sa-Token 的 ThreadLocal 上下文不会自动跨线程传递。
 * Controller 在分发异步任务前捕获当前登录用户标识，异步线程通过本类写入，
 * 下游服务（如 {@code AiMemoryService}、{@code AiAuditLogService}）优先读取此值。
 * <p>
 * 同步接口（POST /ai/chat）在 HTTP 线程执行，无需使用本类，仍直接调用 StpUtil。
 */
public final class SseChatContext {

    private static final ThreadLocal<String> LOGIN_ID = new ThreadLocal<>();

    private SseChatContext() {
    }

    /**
     * 在异步任务开始时设置当前线程的登录用户标识。
     */
    public static void setLoginId(String loginId) {
        LOGIN_ID.set(loginId);
    }

    /**
     * 获取由 Controller 传递过来的登录用户标识，可能为 null。
     */
    public static String getLoginId() {
        return LOGIN_ID.get();
    }

    /**
     * 异步任务结束时清理，避免线程池复用时的数据串扰。
     */
    public static void clear() {
        LOGIN_ID.remove();
    }
}
