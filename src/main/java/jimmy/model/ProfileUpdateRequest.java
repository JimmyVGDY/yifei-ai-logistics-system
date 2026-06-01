package jimmy.model;

/**
 * 个人资料修改请求 —— 当前登录用户可自行修改的字段。
 */
public class ProfileUpdateRequest {

    /** 真实姓名 */
    private String realName;

    /** 手机号 */
    private String mobile;

    /** 邮箱 */
    private String email;

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
