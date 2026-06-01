package jimmy.model;

public class LoginConflictResponse {

    private String loginStatus;
    private String conflictId;
    private String usernameMasked;
    private long expireAt;
    private long remainingSeconds;
    private String message;
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
