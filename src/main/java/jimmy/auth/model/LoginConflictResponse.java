package jimmy.auth.model;

/**
 * 登录冲突响应 —— 包含冲突状态(PENDING/ACCEPTED/REJECTED/EXPIRED)和等待时长。
 */
public class LoginConflictResponse {

    /** 登录冲突状态（PENDING/ACCEPTED/REJECTED/EXPIRED） */
    private String loginStatus;
    /** 冲突记录ID */
    private String conflictId;
    /** 脱敏后的用户名 */
    private String usernameMasked;
    /** 冲突过期时间戳（秒） */
    private long expireAt;
    /** 剩余等待秒数 */
    private long remainingSeconds;
    /** 提示消息 */
    private String message;
    /** 登录成功响应（ACCEPTED时返回） */
    private LoginResponse loginResponse;

    public String getLoginStatus() { return loginStatus; }

    public void setLoginStatus(String loginStatus) { this.loginStatus = loginStatus; }

    public String getConflictId() { return conflictId; }

    public void setConflictId(String conflictId) { this.conflictId = conflictId; }

    public String getUsernameMasked() { return usernameMasked; }

    public void setUsernameMasked(String usernameMasked) { this.usernameMasked = usernameMasked; }

    public long getExpireAt() { return expireAt; }

    public void setExpireAt(long expireAt) { this.expireAt = expireAt; }

    public long getRemainingSeconds() { return remainingSeconds; }

    public void setRemainingSeconds(long remainingSeconds) { this.remainingSeconds = remainingSeconds; }

    public String getMessage() { return message; }

    public void setMessage(String message) { this.message = message; }

    public LoginResponse getLoginResponse() { return loginResponse; }

    public void setLoginResponse(LoginResponse loginResponse) { this.loginResponse = loginResponse; }
}
