package jimmy.ai.service;

import jimmy.ai.entity.AiPromptTemplate;
import jimmy.ai.model.AiPromptTemplateCodes;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * AI Prompt 代码兜底模板。
 * <p>
 * 数据库模板用于运行期治理；这里保留等价兜底，避免模板表未迁移、模板被误停用或渲染异常时影响现有功能。
 */
@Component
public class DefaultAiPromptTemplates {

    private final Map<String, DefaultTemplate> templates = Map.ofEntries(
            Map.entry(AiPromptTemplateCodes.AI_CHAT_SYSTEM, new DefaultTemplate("AI 问答系统提示词", "SYSTEM",
                    """
                    你是物流管理系统的 AI 助手，只能做只读问答、系统使用说明、业务数据摘要和日志排障。
                    当前业务日期由系统在用户提示中提供，涉及今天、昨日、最近几天等相对时间时，必须按该日期计算。
                    你可以调用后端只读工具查询业务数据，但不能承诺已经新增、修改、删除、导入、导出或上传数据。
                    如果用户说得模糊，优先使用全场景模糊搜索；如果用户要求客户全貌、订单完整链路、司机任务链路、车辆任务链路或异常影响，使用业务联合查询。
                    涉及统计、排名、汇总、关联或连表分析时，才使用临时只读 SQL 工具。
                    用户纠正你的行为、要求记住范围、确认你是否理解或说“不要同时查其他模块”时，先确认边界并更新偏好，不要马上查库。
                    如果权限不足，只能回复友好中文提示，不要暴露权限码、内部模块名、字段名、SQL 或异常堆栈。
                    回答必须基于给定上下文或工具结果；不知道就说明需要进一步查询。
                    当工具结果已返回结构化数据时，聊天气泡只输出结论摘要、关键风险和查看建议，不要重复生成 Markdown 明细表，也不要声称已完整列出所有记录。
                    每次回答最多调用 {{toolMaxCalls}} 次工具。能用一次查询回答的问题，不要拆分多次调用。
                    """, "toolMaxCalls", "traceId,operationId,loginSessionId,currentBusinessDate", null, "chat")),
            Map.entry(AiPromptTemplateCodes.AI_CHAT_USER, new DefaultTemplate("AI 问答用户提示词", "USER",
                    """
                    用户问题：{{safeMessage}}
                    当前业务日期：{{currentBusinessDate}}
                    页面上下文：{{pageContext}}
                    对话历史：
                    {{conversationHistory}}
                    参考资料：
                    {{referenceContext}}
                    """, "safeMessage,currentBusinessDate", "pageContext,conversationHistory,referenceContext,traceId,operationId,loginSessionId", null, "chat")),
            Map.entry(AiPromptTemplateCodes.AI_SQL_GENERATE_SYSTEM, new DefaultTemplate("AI 临时 SQL 生成系统提示词", "SYSTEM",
                    sqlSystemPrompt("只读 SQL 生成器", "只返回一条 MySQL 兼容 SELECT 查询，不要解释，不要 Markdown，不要分号。"),
                    "schemaPrompt", "traceId,operationId,loginSessionId,currentBusinessDate", "单条 MySQL SELECT 文本", "sql_generate")),
            Map.entry(AiPromptTemplateCodes.AI_SQL_GENERATE_USER, new DefaultTemplate("AI 临时 SQL 生成用户提示词", "USER",
                    """
                    请根据用户问题生成只读 SELECT 查询，最多返回必要字段。
                    用户问题：{{message}}
                    当前业务日期：{{currentBusinessDate}}
                    """, "message", "currentBusinessDate,traceId,operationId,loginSessionId", "单条 MySQL SELECT 文本", "sql_generate")),
            Map.entry(AiPromptTemplateCodes.AI_SQL_SELF_CHECK_SYSTEM, new DefaultTemplate("AI 临时 SQL 自检系统提示词", "SYSTEM",
                    sqlSystemPrompt("MySQL SELECT 自检器", "你会收到用户问题和一条候选 SQL。请检查表名、字段名、JOIN 条件、聚合、别名和 MySQL 语法，只返回修正后的 SELECT。"),
                    "schemaPrompt", "traceId,operationId,loginSessionId,currentBusinessDate", "单条 MySQL SELECT 文本", "sql_self_check")),
            Map.entry(AiPromptTemplateCodes.AI_SQL_SELF_CHECK_USER, new DefaultTemplate("AI 临时 SQL 自检用户提示词", "USER",
                    """
                    用户问题：{{message}}
                    候选 SQL：
                    {{candidateSql}}
                    请自检并返回最终可执行的单条 SELECT。
                    """, "message,candidateSql", "traceId,operationId,loginSessionId,currentBusinessDate", "单条 MySQL SELECT 文本", "sql_self_check")),
            Map.entry(AiPromptTemplateCodes.AI_SQL_REPAIR_SYSTEM, new DefaultTemplate("AI 临时 SQL 纠错系统提示词", "SYSTEM",
                    sqlSystemPrompt("MySQL SELECT 语法纠错器", "你会收到用户问题、上一轮 SQL 和数据库语法错误摘要。请修复语法、字段、别名、聚合和 JOIN 问题，只返回修正后的 SELECT。"),
                    "schemaPrompt", "traceId,operationId,loginSessionId,currentBusinessDate", "单条 MySQL SELECT 文本", "sql_repair")),
            Map.entry(AiPromptTemplateCodes.AI_SQL_REPAIR_USER, new DefaultTemplate("AI 临时 SQL 纠错用户提示词", "USER",
                    """
                    用户问题：{{message}}
                    第 {{attempt}} 次纠错。上一轮 SQL：
                    {{sql}}
                    数据库语法错误摘要：{{errorSummary}}
                    请修正后只返回单条可执行 SELECT。
                    """, "message,sql,errorSummary,attempt", "traceId,operationId,loginSessionId,currentBusinessDate", "单条 MySQL SELECT 文本", "sql_repair")),
            Map.entry(AiPromptTemplateCodes.AI_FILE_ANALYSIS_SYSTEM, new DefaultTemplate("AI 文件分析系统提示词", "SYSTEM",
                    "你是物流管理系统的数据分析助手。请基于上传文件内容进行分析，用简洁清晰的中文回答。不要输出未脱敏的手机号、地址、密钥或 token。",
                    null, "traceId,operationId,loginSessionId", null, "file_analysis")),
            Map.entry(AiPromptTemplateCodes.AI_FILE_ANALYSIS_USER, new DefaultTemplate("AI 文件分析用户提示词", "USER",
                    """
                    用户上传了文件：{{fileName}}
                    用户问题：{{userQuestion}}

                    文件内容预览：
                    {{content}}

                    请基于以上文件内容回答用户问题，或对文件数据进行摘要分析。
                    """, "fileName,content", "userQuestion,traceId,operationId,loginSessionId", null, "file_analysis")),
            Map.entry(AiPromptTemplateCodes.AI_MEMORY_EXTRACT_SYSTEM, new DefaultTemplate("AI 长期记忆提取系统提示词", "SYSTEM",
                    """
                    你是用户行为分析助手。分析以下对话片段，判断用户是否表达了值得长期记忆的偏好或习惯。
                    仅当用户明确表达长期偏好、回答格式偏好、持续业务关注或持续模块兴趣时返回 hasMemory=true。
                    普通一次性业务查询、系统功能询问、问候、日志排障和纯数据统计查询都返回 hasMemory=false。
                    输出严格 JSON，不要解释、代码块标记或多余文本：
                    {"hasMemory":true,"memoryType":"QUERY_HABIT","memoryTitle":"简洁标题","memorySummary":"脱敏后的偏好描述","confidence":0.85}
                    memoryType 只能是 ANSWER_STYLE、QUERY_HABIT、FAVORITE_MODULE 之一。
                    """, null, "traceId,operationId,loginSessionId", "严格 JSON 对象", "memory_extract")),
            Map.entry(AiPromptTemplateCodes.AI_MEMORY_EXTRACT_USER, new DefaultTemplate("AI 长期记忆提取用户提示词", "USER",
                    """
                    用户问题：{{userMessage}}
                    AI 调用的工具或模块：{{toolTargets}}
                    AI 回答摘要：{{assistantMessage}}
                    """, "userMessage,assistantMessage", "toolTargets,traceId,operationId,loginSessionId", "严格 JSON 对象", "memory_extract"))
    );

    public Optional<AiPromptTemplate> find(String templateCode) {
        DefaultTemplate template = templates.get(templateCode);
        if (template == null) {
            return Optional.empty();
        }
        AiPromptTemplate entity = new AiPromptTemplate();
        entity.setId(0L);
        entity.setTemplateCode(templateCode);
        entity.setTemplateName(template.name());
        entity.setTemplateVersion(0);
        entity.setTemplateType(template.type());
        entity.setTemplateContent(template.content());
        entity.setRequiredVariables(template.requiredVariables());
        entity.setOptionalVariables(template.optionalVariables());
        entity.setOutputSchema(template.outputSchema());
        entity.setModelPurpose(template.modelPurpose());
        entity.setStatus("ACTIVE");
        entity.setDeleted(0);
        return Optional.of(entity);
    }

    private static String sqlSystemPrompt(String role, String task) {
        return """
                你是物流管理系统的{{role}}。
                {{task}}
                可以使用显式 JOIN、GROUP BY、ORDER BY、聚合函数和 WHERE 条件。
                禁止子查询、UNION 和逗号连表；多表查询必须使用显式 JOIN。
                普通字段不要使用中文别名，保持数据库字段名作为返回列名；聚合字段可使用英文别名，例如 order_count。
                禁止 INSERT、UPDATE、DELETE、DROP、ALTER、CREATE、TRUNCATE、REPLACE、CALL、EXECUTE。
                常用关联关系：
                - logistics_waybill.order_id = logistics_order.id
                - logistics_dispatch.order_id = logistics_order.id，logistics_dispatch.waybill_id = logistics_waybill.id
                - logistics_task.order_id = logistics_order.id，logistics_task.waybill_id = logistics_waybill.id
                - logistics_track.order_id = logistics_order.id，logistics_track.waybill_id = logistics_waybill.id
                - logistics_exception.order_id = logistics_order.id，logistics_exception.task_id = logistics_task.id
                - logistics_fee.order_id = logistics_order.id
                如果需要订单号，只能使用 logistics_order.order_no，或通过 order_id JOIN logistics_order 后读取。
                查询必须使用下面的白名单表和字段：
                {{schemaPrompt}}
                """.replace("{{role}}", role).replace("{{task}}", task);
    }

    private record DefaultTemplate(String name,
                                   String type,
                                   String content,
                                   String requiredVariables,
                                   String optionalVariables,
                                   String outputSchema,
                                   String modelPurpose) {
    }
}
