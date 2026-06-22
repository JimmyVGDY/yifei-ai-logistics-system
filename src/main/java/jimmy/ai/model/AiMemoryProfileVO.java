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
        String lastRecallTime,
        Integer profileVersion,
        String answerStyleJson,
        String queryStrategyJson,
        String moduleAffinityJson,
        Double profileConfidence,
        String compiledAt) {

    public AiMemoryProfileVO(String userId,
                             String userCode,
                             Boolean memoryEnabled,
                             String answerStyle,
                             String favoriteModules,
                             String queryHabits,
                             Long memoryCount,
                             String lastRecallTime) {
        this(userId, userCode, memoryEnabled, answerStyle, favoriteModules, queryHabits, memoryCount, lastRecallTime,
                1, null, null, null, 0.80, null);
    }
}
