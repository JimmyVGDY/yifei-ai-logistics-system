package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.util.SseChatContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 用户上下文解析器 —— 统一封装 SSE 异步线程和同步 HTTP 线程的用户标识获取。
 * <p>
 * 替代多处重复的 {@code SseChatContext.getLoginId() != null ? ... : StpUtil.getLoginIdDefaultNull()} 模式。
 */
@Component
public class UserContextResolver {

    /** 未登录或无法解析时使用的匿名标识 */
    private static final String ANONYMOUS = "anonymous";

    /**
     * 获取当前用户的登录 ID（SSE 优先，HTTP 回退）。
     */
    public String currentUserId() {
        String sseLoginId = SseChatContext.getLoginId();
        if (sseLoginId != null && !sseLoginId.isBlank() && !"null".equals(sseLoginId)) {
            return sseLoginId;
        }
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId == null ? ANONYMOUS : String.valueOf(loginId);
    }

    /**
     * 获取当前用户的 UserCode。
     */
    public String currentUserCode() {
        String sseUserId = SseChatContext.getLoginId();
        if (sseUserId != null && !sseUserId.isBlank() && !"null".equals(sseUserId)) {
            return SseChatContext.getUserCode();
        }
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) return "";
        return String.valueOf(StpUtil.getSessionByLoginId(loginId).get("userCode", ""));
    }

    /**
     * 获取当前用户的登录会话 ID。
     */
    public String currentLoginSessionId() {
        String sseUserId = SseChatContext.getLoginId();
        if (sseUserId != null && !sseUserId.isBlank() && !"null".equals(sseUserId)) {
            return SseChatContext.getLoginSessionId();
        }
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) return "";
        return String.valueOf(StpUtil.getSessionByLoginId(loginId).get("loginSessionId", ""));
    }

    /**
     * 获取当前用户的角色码。
     */
    public String currentRoleCode() {
        String sseUserId = SseChatContext.getLoginId();
        if (sseUserId != null && !sseUserId.isBlank() && !"null".equals(sseUserId)) {
            return SseChatContext.getRoleCode();
        }
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) return "";
        return String.valueOf(StpUtil.getSessionByLoginId(loginId).get("roleCode", ""));
    }

    /**
     * 当前是否在 SSE 异步线程中执行。
     */
    public boolean isSseThread() {
        String sseLoginId = SseChatContext.getLoginId();
        return StringUtils.hasText(sseLoginId) && !"null".equalsIgnoreCase(sseLoginId);
    }

    /**
     * null 安全字符串。
     */
    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
