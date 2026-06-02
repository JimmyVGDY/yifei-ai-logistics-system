package jimmy.model;

/**
 * 需要验证码的登录中间响应 —— 当用户从异常设备登录时返回。
 * <p>
 * 前端检测到 {@code captchaRequired=true} 后展示图形验证码输入框，
 * 用户输入正确答案后重新提交登录请求（携带 captchaId + captchaCode）。
 */
public class RequireCaptchaResponse {

    /** 标识：当前登录需要验证码 */
    private boolean captchaRequired = true;
    /** 验证码唯一标识（回传用） */
    private String captchaId;
    /** Base64 编码的验证码图片 */
    private String captchaImage;
    /** 提示信息 */
    private String message;

    public RequireCaptchaResponse() {
    }

    public RequireCaptchaResponse(String captchaId, String captchaImage, String message) {
        this.captchaId = captchaId;
        this.captchaImage = captchaImage;
        this.message = message;
    }

    public boolean isCaptchaRequired() { return captchaRequired; }
    public void setCaptchaRequired(boolean captchaRequired) { this.captchaRequired = captchaRequired; }

    public String getCaptchaId() { return captchaId; }
    public void setCaptchaId(String captchaId) { this.captchaId = captchaId; }

    public String getCaptchaImage() { return captchaImage; }
    public void setCaptchaImage(String captchaImage) { this.captchaImage = captchaImage; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
