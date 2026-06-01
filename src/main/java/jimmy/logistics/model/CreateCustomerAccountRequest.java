package jimmy.logistics.model;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class CreateCustomerAccountRequest {

    @NotBlank(message = "客户账号类型不能为空")
    @Pattern(regexp = "PERSONAL|ENTERPRISE", message = "客户账号类型只能是个人账号或企业账号")
    private String customerSubjectType;

    @NotBlank(message = "登录账号不能为空")
    @Size(max = 64, message = "登录账号不能超过64个字符")
    private String username;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名不能超过64个字符")
    private String realName;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号必须是11位中国大陆手机号")
    private String mobile;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱不能超过128个字符")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度必须在6到32位之间")
    private String password;

    private Long customerId;

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
