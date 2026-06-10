package jimmy.ai.model;

/**
 * AI 只读查询意图 —— 由自然语言解析得到，后续只能进入白名单查询服务。
 */
public record AiQueryIntent(
        String module,
        String moduleName,
        String permission,
        String keyword,
        String startTime,
        String endTime,
        boolean dashboard,
        boolean forbiddenWrite,
        boolean matched) {

    public static AiQueryIntent unmatched() {
        return new AiQueryIntent(null, null, null, null, null, null, false, false, false);
    }

    public static AiQueryIntent forbiddenWriteIntent() {
        return new AiQueryIntent(null, null, null, null, null, null, false, true, true);
    }
}
