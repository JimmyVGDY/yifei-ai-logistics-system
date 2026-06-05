package jimmy.ai.model;

/**
 * 当前登录账号的 AI 长期记忆画像。
 */
public record AiMemoryProfileVO(
        String userId,
        String userCode,
        Boolean memoryEnabled,
        String answerStyle,
        String favoriteModules,
        String queryHabits,
        Long memoryCount,
        String lastRecallTime) {
}
