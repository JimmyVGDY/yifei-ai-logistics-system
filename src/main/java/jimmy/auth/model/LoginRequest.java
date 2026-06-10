package jimmy.auth.model;

/**
 * 登录请求 DTO —— 接收用户名和密码。
 * <p>
 * 密码由前端明文传入，服务端通过 BCrypt 校验后升级存储。
 * </p>
 */
public class LoginRequest {

    /** 登录用户名 */
    private String username;
    /** 登录密码（明文传入，服务端BCrypt校验） */
    private String password;
    /** 图形验证码ID（可选，异常设备登录时必填） */
    private String captchaId;
    /** 图形验证码答案（可选，异常设备登录时必填） */
    private String captchaCode;

    public String getUsername() { return username; }

    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }

    public void setPassword(String password) { this.password = password; }

    public String getCaptchaId() { return captchaId; }
    public void setCaptchaId(String captchaId) { this.captchaId = captchaId; }

    public String getCaptchaCode() { return captchaCode; }
    public void setCaptchaCode(String captchaCode) { this.captchaCode = captchaCode; }
}
