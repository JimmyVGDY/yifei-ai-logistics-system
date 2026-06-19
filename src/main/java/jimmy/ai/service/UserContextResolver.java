package jimmy.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.ai.util.SseChatContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 用户上下文解析器 —— 统一封装 SSE 异步线程和同步 HTTP 线程的用户标识获取。
 * <p>
 * 兼容无 Sa-Token 上下文场景（如单元测试），异常时返回安全默认值。
 */
@Component
public class UserContextResolver {

    private static final String ANONYMOUS = "anonymous";

    public String currentUserId() {
        String sseLoginId = SseChatContext.getLoginId();
        if (sseLoginId != null && !sseLoginId.isBlank() && !"null".equals(sseLoginId)) {
            return sseLoginId;
        }
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            return loginId == null ? ANONYMOUS : String.valueOf(loginId);
        } catch (Exception e) {
            return ANONYMOUS;
        }
    }

    public String currentUserCode() {
        String sseUserId = SseChatContext.getLoginId();
        if (sseUserId != null && !sseUserId.isBlank() && !"null".equals(sseUserId)) {
            return nullToBlank(SseChatContext.getUserCode());
        }
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId == null) return "";
            return String.valueOf(StpUtil.getSessionByLoginId(loginId).get("userCode", ""));
        } catch (Exception e) {
            return "";
        }
    }

    public String currentLoginSessionId() {
        String sseUserId = SseChatContext.getLoginId();
        if (sseUserId != null && !sseUserId.isBlank() && !"null".equals(sseUserId)) {
            return nullToBlank(SseChatContext.getLoginSessionId());
        }
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId == null) return "";
            return String.valueOf(StpUtil.getSessionByLoginId(loginId).get("loginSessionId", ""));
        } catch (Exception e) {
            return "";
        }
    }

    public String currentRoleCode() {
        String sseUserId = SseChatContext.getLoginId();
        if (sseUserId != null && !sseUserId.isBlank() && !"null".equals(sseUserId)) {
            return nullToBlank(SseChatContext.getRoleCode());
        }
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId == null) return "";
            return String.valueOf(StpUtil.getSessionByLoginId(loginId).get("roleCode", ""));
        } catch (Exception e) {
            return "";
        }
    }

    public boolean isSseThread() {
        String sseLoginId = SseChatContext.getLoginId();
        return StringUtils.hasText(sseLoginId) && !"null".equalsIgnoreCase(sseLoginId);
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
