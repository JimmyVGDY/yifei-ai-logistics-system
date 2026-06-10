package jimmy.auth.model;

/**
 * 需要验证码的登录中间响应 —— 当用户从异常设备登录时返回。
 * <p>
 * 前端检测到 {@code captchaRequired=true} 后展示图形验证码输入框，
 * 用户输入正确答案后重新提交登录请求（携带 captchaId + captchaCode）。
 */
public record RequireCaptchaResponse(
        /** 标识：当前登录需要验证码 */
        boolean captchaRequired,
        /** 验证码唯一标识（回传用） */
        String captchaId,
        /** Base64 编码的验证码图片 */
        String captchaImage,
        /** 提示信息 */
        String message) {

    public RequireCaptchaResponse(String captchaId, String captchaImage, String message) {
        this(true, captchaId, captchaImage, message);
    }

    public boolean isCaptchaRequired() { return captchaRequired; }

    public String getCaptchaId() { return captchaId; }

    public String getCaptchaImage() { return captchaImage; }

    public String getMessage() { return message; }
}
