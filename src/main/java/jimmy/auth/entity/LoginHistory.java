package jimmy.auth.entity;

import java.sql.Timestamp;

/**
 * 登录历史实体 —— 记录每次登录尝试的 IP 地址和客户端标识。
 * <p>
 * 用于异常登录检测：当用户从不常用 IP 或不常用客户端登录时触发图形验证码。
 */
public class LoginHistory {

    /** 主键ID（Snowflake生成） */
    private Long id;
    /** 登录用户ID */
    private Long userId;
    /** 登录用户名 */
    private String username;
    /** 客户端IP地址 */
    private String loginIp;
    /** 客户端User-Agent标识（浏览器/设备指纹） */
    private String userAgent;
    /** 登录结果：SUCCESS / FAIL / CAPTCHA_REQUIRED */
    private String loginResult;
    /** 失败原因（如：密码错误、验证码错误、账号停用） */
    private String failReason;
    /** 是否需要图形验证码（1=需要，0=不需要） */
    private Integer requireCaptcha;
    /** 登录时间 */
    private Timestamp loginTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getLoginIp() { return loginIp; }
    public void setLoginIp(String loginIp) { this.loginIp = loginIp; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String getLoginResult() { return loginResult; }
    public void setLoginResult(String loginResult) { this.loginResult = loginResult; }

    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }

    public Integer getRequireCaptcha() { return requireCaptcha; }
    public void setRequireCaptcha(Integer requireCaptcha) { this.requireCaptcha = requireCaptcha; }

    public Timestamp getLoginTime() { return loginTime; }
    public void setLoginTime(Timestamp loginTime) { this.loginTime = loginTime; }
}
