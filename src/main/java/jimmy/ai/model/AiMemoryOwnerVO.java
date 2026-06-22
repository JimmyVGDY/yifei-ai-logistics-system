package jimmy.ai.model;

/**
 * 需要重新编译 AI 用户画像的账号范围。
 */
public record AiMemoryOwnerVO(
        String userId,
        String userCode
) {
}
