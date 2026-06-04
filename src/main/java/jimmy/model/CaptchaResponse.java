package jimmy.model;

/**
 * 图形验证码响应 —— 包含验证码 ID 和 Base64 编码的图片。
 * <p>
 * 验证码答案存储在 Redis 中（5 分钟 TTL），前端需回传 captchaId + captchaCode。
 */
public record CaptchaResponse(
        /** 验证码唯一标识（关联 Redis 中的正确答案） */
        String captchaId,
        /** Base64 编码的验证码图片（data:image/png;base64,...） */
        String captchaImage) {

    public String getCaptchaId() { return captchaId; }

    public String getCaptchaImage() { return captchaImage; }
}
