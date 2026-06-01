package jimmy.model;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 密码修改请求 —— 需要输入原密码验证身份。
 */
public class PasswordChangeRequest {

    /** 原密码（用于身份验证） */
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    /** 新密码（至少 6 位） */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, message = "新密码至少 6 位")
    private String newPassword;

    public String getOldPassword() { return oldPassword; }
    public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
