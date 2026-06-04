package jimmy.logistics.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 客户账号创建请求 —— 支持个人(PERSONAL)和企业(ENTERPRISE)两种主体类型。
 * <p>
 * 参数校验由 JSR-303 Bean Validation 注解完成，Controller 层统一拦截返回 400。
 */
public class CreateCustomerAccountRequest {

    /** 客户主体类型：PERSONAL=个人 / ENTERPRISE=企业 */
    @NotBlank(message = "客户账号类型不能为空")
    @Pattern(regexp = "PERSONAL|ENTERPRISE", message = "客户账号类型只能是个人账号或企业账号")
    private String customerSubjectType;

    /** 登录账号（唯一） */
    @NotBlank(message = "登录账号不能为空")
    @Size(max = 64, message = "登录账号不能超过64个字符")
    private String username;

    /** 真实姓名 */
    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名不能超过64个字符")
    private String realName;

    /** 手机号（AES加密存储，11位中国大陆手机号） */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号必须是11位中国大陆手机号")
    private String mobile;

    /** 邮箱 */
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱不能超过128个字符")
    private String email;

    /** 登录密码（明文传入，服务端BCrypt加密存储） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度必须在6到32位之间")
    private String password;

    /** 客户ID（可选，已有客户时关联） */
    private Long customerId;

    /** 客户名称/企业名称 */
    @Size(max = 128, message = "客户名称不能超过128个字符")
    private String customerName;

    public String getCustomerSubjectType() {
        return customerSubjectType;
    }

    public void setCustomerSubjectType(String customerSubjectType) {
        this.customerSubjectType = customerSubjectType;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }
}
