package jimmy.auth.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 密码修改请求 —— 需要输入原密码验证身份。
 */
public class PasswordChangeRequest {

    /** 原密码（用于身份验证） */
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    /** 新密码（至少 8 位，业务层校验字母和数字组合） */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, message = "新密码至少 8 位")
    private String newPassword;

    public String getOldPassword() { return oldPassword; }
    public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
