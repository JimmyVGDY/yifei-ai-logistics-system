package jimmy.ai.model;

/**
 * AI 问答执行模式。
 * <p>
 * 模型可以参与意图判断，但后端始终以这里的模式作为可审计、可校验的执行计划描述。
 */
public enum AiExecutionMode {
    MODULE_QUERY("单模块查询"),
    GLOBAL_SEARCH("全场景模糊搜索"),
    JOINED_QUERY("业务联合查询"),
    LOG_ANALYSIS("日志排障"),
    READONLY_SQL("临时只读 SQL"),
    RAG_SEARCH("文档知识检索"),
    GENERAL_CHAT("普通问答");

    private final String label;

    AiExecutionMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
