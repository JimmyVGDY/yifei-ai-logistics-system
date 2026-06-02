package jimmy.model;

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

    public String getUsername() { return username; }

    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }

    public void setPassword(String password) { this.password = password; }
}
