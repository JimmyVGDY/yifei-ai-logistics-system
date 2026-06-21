-- AI Prompt 模板治理增量脚本
-- 说明：
-- 1. 可重复执行，不清库、不重建旧表。
-- 2. 默认模板只在 template_code 不存在时插入，避免覆盖数据库中的人工优化版本。
-- 3. ai_token_usage 扩展字段用于追踪每次模型调用使用的模板编码和版本。

set @schema_name = database();

drop procedure if exists add_column_if_missing;
delimiter $$
create procedure add_column_if_missing(
    in table_name_param varchar(128),
    in column_name_param varchar(128),
    in ddl_param text
)
begin
    if not exists (
        select 1
        from information_schema.columns
        where table_schema = @schema_name
          and table_name = table_name_param
          and column_name = column_name_param
    ) then
        set @ddl = ddl_param;
        prepare stmt from @ddl;
        execute stmt;
        deallocate prepare stmt;
    end if;
end$$
delimiter ;

drop procedure if exists add_index_if_missing;
delimiter $$
create procedure add_index_if_missing(
    in table_name_param varchar(128),
    in index_name_param varchar(128),
    in ddl_param text
)
begin
    if not exists (
        select 1
        from information_schema.statistics
        where table_schema = @schema_name
          and table_name = table_name_param
          and index_name = index_name_param
    ) then
        set @ddl = ddl_param;
        prepare stmt from @ddl;
        execute stmt;
        deallocate prepare stmt;
    end if;
end$$
delimiter ;

create table if not exists ai_prompt_template (
    id bigint not null primary key comment '模板主键',
    template_code varchar(80) not null comment '模板编码',
    template_name varchar(120) not null comment '模板名称',
    template_version int not null default 1 comment '模板版本',
    template_type varchar(20) not null default 'USER' comment '模板类型：SYSTEM/USER',
    template_content longtext not null comment 'Mustache 模板内容',
    required_variables varchar(1000) null comment '必填变量，英文逗号分隔',
    optional_variables varchar(1000) null comment '可选变量，英文逗号分隔',
    output_schema longtext null comment '期望输出结构说明',
    model_purpose varchar(80) null comment '模型调用用途',
    status varchar(20) not null default 'ACTIVE' comment '状态：ACTIVE/PAUSED',
    remark varchar(500) null comment '备注',
    created_at datetime not null default current_timestamp comment '创建时间',
    updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    deleted tinyint not null default 0 comment '逻辑删除：0 否，1 是'
) engine=InnoDB default charset=utf8mb4 comment='AI Prompt 模板表';

call add_index_if_missing(
    'ai_prompt_template',
    'uk_ai_prompt_template_code_version',
    'alter table ai_prompt_template add unique key uk_ai_prompt_template_code_version(template_code, template_version)'
);

call add_index_if_missing(
    'ai_prompt_template',
    'idx_ai_prompt_template_active',
    'alter table ai_prompt_template add index idx_ai_prompt_template_active(template_code, status, deleted, template_version)'
);

call add_column_if_missing(
    'ai_token_usage',
    'template_code',
    'alter table ai_token_usage add column template_code varchar(80) null comment ''Prompt 模板编码'' after purpose'
);

call add_column_if_missing(
    'ai_token_usage',
    'template_version',
    'alter table ai_token_usage add column template_version int null comment ''Prompt 模板版本'' after template_code'
);

call add_index_if_missing(
    'ai_token_usage',
    'idx_ai_token_usage_template',
    'alter table ai_token_usage add index idx_ai_token_usage_template(template_code, template_version, created_at)'
);

insert into ai_prompt_template (
    id, template_code, template_name, template_version, template_type, template_content,
    required_variables, optional_variables, output_schema, model_purpose, remark
)
select 260621100000001, 'AI_CHAT_SYSTEM', 'AI 问答系统提示词', 1, 'SYSTEM',
'你是物流管理系统的 AI 助手，只能做只读问答、系统使用说明、业务数据摘要和日志排障。
当前业务日期由系统在用户提示中提供，涉及今天、昨日、最近几天等相对时间时，必须按该日期计算。
你可以调用后端只读工具查询业务数据，但不能承诺已经新增、修改、删除、导入、导出或上传数据。
如果用户说得模糊，优先使用全场景模糊搜索；如果用户要求客户全貌、订单完整链路、司机任务链路、车辆任务链路或异常影响，使用业务联合查询。
涉及统计、排名、汇总、关联或连表分析时，才使用临时只读 SQL 工具。
如果权限不足，只能回复友好中文提示，不要暴露权限码、内部模块名、字段名、SQL 或异常堆栈。
回答必须基于给定上下文或工具结果；不知道就说明需要进一步查询。
当工具结果已返回结构化数据时，聊天气泡只输出结论摘要、关键风险和查看建议，不要重复生成 Markdown 明细表，也不要声称已完整列出所有记录。
每次回答最多调用 {{toolMaxCalls}} 次工具。能用一次查询回答的问题，不要拆分多次调用。',
'toolMaxCalls',
'traceId,operationId,loginSessionId,currentBusinessDate',
null, 'chat', '默认 AI 问答系统提示词'
where not exists (select 1 from ai_prompt_template where template_code = 'AI_CHAT_SYSTEM' and deleted = 0);

insert into ai_prompt_template (
    id, template_code, template_name, template_version, template_type, template_content,
    required_variables, optional_variables, output_schema, model_purpose, remark
)
select 260621100000002, 'AI_CHAT_USER', 'AI 问答用户提示词', 1, 'USER',
'用户问题：{{safeMessage}}
当前业务日期：{{currentBusinessDate}}
页面上下文：{{pageContext}}
对话历史：
{{conversationHistory}}
参考资料：
{{referenceContext}}',
'safeMessage,currentBusinessDate',
'pageContext,conversationHistory,referenceContext,traceId,operationId,loginSessionId',
null, 'chat', '默认 AI 问答用户提示词'
where not exists (select 1 from ai_prompt_template where template_code = 'AI_CHAT_USER' and deleted = 0);

insert into ai_prompt_template (
    id, template_code, template_name, template_version, template_type, template_content,
    required_variables, optional_variables, output_schema, model_purpose, remark
)
select 260621100000003, 'AI_SQL_GENERATE_SYSTEM', 'AI 临时 SQL 生成系统提示词', 1, 'SYSTEM',
'你是物流管理系统的只读 SQL 生成器。
只返回一条 MySQL 兼容 SELECT 查询，不要解释，不要 Markdown，不要分号。
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
{{schemaPrompt}}',
'schemaPrompt',
'traceId,operationId,loginSessionId,currentBusinessDate',
'单条 MySQL SELECT 文本', 'sql_generate', '默认临时 SQL 生成系统提示词'
where not exists (select 1 from ai_prompt_template where template_code = 'AI_SQL_GENERATE_SYSTEM' and deleted = 0);

insert into ai_prompt_template (
    id, template_code, template_name, template_version, template_type, template_content,
    required_variables, optional_variables, output_schema, model_purpose, remark
)
select 260621100000004, 'AI_SQL_GENERATE_USER', 'AI 临时 SQL 生成用户提示词', 1, 'USER',
'请根据用户问题生成只读 SELECT 查询，最多返回必要字段。
用户问题：{{message}}
当前业务日期：{{currentBusinessDate}}',
'message',
'currentBusinessDate,traceId,operationId,loginSessionId',
'单条 MySQL SELECT 文本', 'sql_generate', '默认临时 SQL 生成用户提示词'
where not exists (select 1 from ai_prompt_template where template_code = 'AI_SQL_GENERATE_USER' and deleted = 0);

insert into ai_prompt_template (
    id, template_code, template_name, template_version, template_type, template_content,
    required_variables, optional_variables, output_schema, model_purpose, remark
)
select 260621100000005, 'AI_SQL_SELF_CHECK_SYSTEM', 'AI 临时 SQL 自检系统提示词', 1, 'SYSTEM',
'你是物流管理系统的 MySQL SELECT 自检器。
你会收到用户问题和一条候选 SQL。请检查表名、字段名、JOIN 条件、聚合、别名和 MySQL 语法。
只允许返回一条修正后的 MySQL SELECT 查询，不要解释，不要 Markdown，不要分号。
如果候选 SQL 已经正确，也原样返回规范化后的 SELECT。
禁止子查询、UNION 和逗号连表；多表查询必须使用显式 JOIN。
普通字段不要使用中文别名，保持数据库字段名作为返回列名；聚合字段可使用英文别名。
禁止 INSERT、UPDATE、DELETE、DROP、ALTER、CREATE、TRUNCATE、REPLACE、CALL、EXECUTE。
查询必须使用下面的白名单表和字段：
{{schemaPrompt}}',
'schemaPrompt',
'traceId,operationId,loginSessionId,currentBusinessDate',
'单条 MySQL SELECT 文本', 'sql_self_check', '默认临时 SQL 自检系统提示词'
where not exists (select 1 from ai_prompt_template where template_code = 'AI_SQL_SELF_CHECK_SYSTEM' and deleted = 0);

insert into ai_prompt_template (
    id, template_code, template_name, template_version, template_type, template_content,
    required_variables, optional_variables, output_schema, model_purpose, remark
)
select 260621100000006, 'AI_SQL_SELF_CHECK_USER', 'AI 临时 SQL 自检用户提示词', 1, 'USER',
'用户问题：{{message}}
候选 SQL：
{{candidateSql}}
请自检并返回最终可执行的单条 SELECT。',
'message,candidateSql',
'traceId,operationId,loginSessionId,currentBusinessDate',
'单条 MySQL SELECT 文本', 'sql_self_check', '默认临时 SQL 自检用户提示词'
where not exists (select 1 from ai_prompt_template where template_code = 'AI_SQL_SELF_CHECK_USER' and deleted = 0);

insert into ai_prompt_template (
    id, template_code, template_name, template_version, template_type, template_content,
    required_variables, optional_variables, output_schema, model_purpose, remark
)
select 260621100000007, 'AI_SQL_REPAIR_SYSTEM', 'AI 临时 SQL 纠错系统提示词', 1, 'SYSTEM',
'你是物流管理系统的 MySQL SELECT 语法纠错器。
你会收到用户问题、上一轮 SQL 和数据库语法错误摘要。请修复语法、字段、别名、聚合和 JOIN 问题。
只允许返回一条修正后的 MySQL SELECT 查询，不要解释，不要 Markdown，不要分号。
不要扩大查询范围，不要新增写操作，不要使用 select *。
禁止子查询、UNION 和逗号连表；多表查询必须使用显式 JOIN。
如果错误原因是 Unknown column，请只使用白名单里的真实字段；需要订单号时通过 order_id JOIN logistics_order。
查询必须使用下面的白名单表和字段：
{{schemaPrompt}}',
'schemaPrompt',
'traceId,operationId,loginSessionId,currentBusinessDate',
'单条 MySQL SELECT 文本', 'sql_repair', '默认临时 SQL 纠错系统提示词'
where not exists (select 1 from ai_prompt_template where template_code = 'AI_SQL_REPAIR_SYSTEM' and deleted = 0);

insert into ai_prompt_template (
    id, template_code, template_name, template_version, template_type, template_content,
    required_variables, optional_variables, output_schema, model_purpose, remark
)
select 260621100000008, 'AI_SQL_REPAIR_USER', 'AI 临时 SQL 纠错用户提示词', 1, 'USER',
'用户问题：{{message}}
第 {{attempt}} 次纠错。上一轮 SQL：
{{sql}}
数据库语法错误摘要：{{errorSummary}}
请修正后只返回单条可执行 SELECT。',
'message,sql,errorSummary,attempt',
'traceId,operationId,loginSessionId,currentBusinessDate',
'单条 MySQL SELECT 文本', 'sql_repair', '默认临时 SQL 纠错用户提示词'
where not exists (select 1 from ai_prompt_template where template_code = 'AI_SQL_REPAIR_USER' and deleted = 0);

insert into ai_prompt_template (
    id, template_code, template_name, template_version, template_type, template_content,
    required_variables, optional_variables, output_schema, model_purpose, remark
)
select 260621100000009, 'AI_FILE_ANALYSIS_SYSTEM', 'AI 文件分析系统提示词', 1, 'SYSTEM',
'你是物流管理系统的数据分析助手。请基于上传文件内容进行分析，用简洁清晰的中文回答。不要输出未脱敏的手机号、地址、密钥或 token。',
null,
'traceId,operationId,loginSessionId',
null, 'file_analysis', '默认文件分析系统提示词'
where not exists (select 1 from ai_prompt_template where template_code = 'AI_FILE_ANALYSIS_SYSTEM' and deleted = 0);

insert into ai_prompt_template (
    id, template_code, template_name, template_version, template_type, template_content,
    required_variables, optional_variables, output_schema, model_purpose, remark
)
select 260621100000010, 'AI_FILE_ANALYSIS_USER', 'AI 文件分析用户提示词', 1, 'USER',
'用户上传了文件：{{fileName}}
{{#userQuestion}}用户问题：{{userQuestion}}{{/userQuestion}}

文件内容预览：
{{content}}

请基于以上文件内容回答用户问题，或对文件数据进行摘要分析。',
'fileName,content',
'userQuestion,traceId,operationId,loginSessionId',
null, 'file_analysis', '默认文件分析用户提示词'
where not exists (select 1 from ai_prompt_template where template_code = 'AI_FILE_ANALYSIS_USER' and deleted = 0);

insert into ai_prompt_template (
    id, template_code, template_name, template_version, template_type, template_content,
    required_variables, optional_variables, output_schema, model_purpose, remark
)
select 260621100000011, 'AI_MEMORY_EXTRACT_SYSTEM', 'AI 长期记忆提取系统提示词', 1, 'SYSTEM',
'你是用户行为分析助手。分析以下对话片段，判断用户是否表达了值得长期记忆的偏好或习惯。

仅当以下情况返回 YES：
- 用户明确说以后、默认、习惯、希望、喜欢、记住、常用、一般先等表达偏好的词；
- 用户指定回答格式偏好，例如简短点、详细说明、用表格、先给结论；
- 用户表达持续性的业务关注，例如我主要看异常、我经常需要查费用；
- 用户对某个业务模块表现出持续兴趣。

以下情况返回 NO：
- 普通的一次性业务查询；
- 简单的系统功能询问；
- 测试性质的问候；
- 日志排障类问题；
- 纯数据统计查询。

输出严格 JSON，不要解释、代码块标记或多余文本：
{"hasMemory":true,"memoryType":"QUERY_HABIT","memoryTitle":"简洁标题","memorySummary":"脱敏后的偏好描述","confidence":0.85}
memoryType 只能是 ANSWER_STYLE、QUERY_HABIT、FAVORITE_MODULE 之一。',
null,
'traceId,operationId,loginSessionId',
'严格 JSON 对象', 'memory_extract', '默认长期记忆提取系统提示词'
where not exists (select 1 from ai_prompt_template where template_code = 'AI_MEMORY_EXTRACT_SYSTEM' and deleted = 0);

insert into ai_prompt_template (
    id, template_code, template_name, template_version, template_type, template_content,
    required_variables, optional_variables, output_schema, model_purpose, remark
)
select 260621100000012, 'AI_MEMORY_EXTRACT_USER', 'AI 长期记忆提取用户提示词', 1, 'USER',
'用户问题：{{userMessage}}
{{#toolTargets}}AI 调用的工具或模块：{{toolTargets}}
{{/toolTargets}}AI 回答摘要：{{assistantMessage}}',
'userMessage,assistantMessage',
'toolTargets,traceId,operationId,loginSessionId',
'严格 JSON 对象', 'memory_extract', '默认长期记忆提取用户提示词'
where not exists (select 1 from ai_prompt_template where template_code = 'AI_MEMORY_EXTRACT_USER' and deleted = 0);

drop procedure if exists add_column_if_missing;
drop procedure if exists add_index_if_missing;
