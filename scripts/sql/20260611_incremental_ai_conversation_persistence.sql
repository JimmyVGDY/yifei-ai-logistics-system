-- 用途：AI 会话持久化、归档删除和会话级上下文快照。
-- 说明：可重复执行；不清空、不覆盖现有业务数据。
set @schema_name = database();

drop procedure if exists add_index_if_missing;
delimiter //
create procedure add_index_if_missing(in p_table varchar(64), in p_index varchar(64), in p_definition text)
begin
    if not exists (
        select 1 from information_schema.statistics
        where table_schema = @schema_name and table_name = p_table and index_name = p_index
    ) then
        set @ddl = concat('alter table ', p_table, ' add index ', p_index, ' ', p_definition);
        prepare stmt from @ddl;
        execute stmt;
        deallocate prepare stmt;
    end if;
end //
delimiter ;

create table if not exists ai_conversation (
    id bigint primary key comment '短位随机主键',
    conversation_id varchar(64) not null comment 'AI 会话 ID，前后端和审计链路统一使用',
    user_id varchar(64) not null comment '登录用户ID，保持原值用于审计追踪',
    user_code varchar(64) null comment '登录用户业务编号，保持原值用于审计追踪',
    title varchar(255) not null comment '脱敏后的会话标题',
    status varchar(32) not null default 'ACTIVE' comment 'ACTIVE/ARCHIVED/DELETED',
    context_snapshot text null comment '脱敏后的会话上下文快照，用于连续追问',
    message_count int not null default 0 comment '有效消息数量',
    last_message_at datetime null comment '最近消息时间',
    archived_at datetime null comment '归档时间',
    deleted_at datetime null comment '删除时间',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0 comment '逻辑删除标记',
    unique key uk_ai_conversation_id (conversation_id),
    key idx_ai_conversation_user_status (user_id, user_code, status, deleted, last_message_at),
    key idx_ai_conversation_trace_time (last_message_at, updated_at)
) comment='AI会话主表';

create table if not exists ai_conversation_message (
    id bigint primary key comment '短位随机主键',
    message_id varchar(64) not null comment 'AI 单条消息 ID',
    conversation_id varchar(64) not null comment 'AI 会话 ID',
    user_id varchar(64) not null comment '登录用户ID，保持原值用于审计追踪',
    user_code varchar(64) null comment '登录用户业务编号，保持原值用于审计追踪',
    role varchar(32) not null comment 'user/assistant/system/tool',
    content text not null comment '脱敏后的消息内容',
    status varchar(32) not null default 'SUCCESS' comment 'SUCCESS/FAILED',
    trace_id varchar(64) null,
    operation_id varchar(64) null,
    login_session_id varchar(64) null,
    tool_summary text null comment '脱敏后的工具调用摘要',
    citation_summary text null comment '脱敏后的引用来源摘要',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0 comment '逻辑删除标记',
    unique key uk_ai_message_id (message_id),
    key idx_ai_message_conversation_time (conversation_id, deleted, created_at),
    key idx_ai_message_user_time (user_id, user_code, created_at),
    key idx_ai_message_trace (trace_id, operation_id, login_session_id)
) comment='AI会话消息表';

call add_index_if_missing('sys_operation_log', 'idx_operation_log_ai_message', '(ai_conversation_id, ai_message_id)');

insert ignore into sys_permission (
    id, permission_code, permission_name, permission_type,
    menu_id, module_code, action_code, status, create_time, update_time
)
select 260611000500000 + t.seq, t.permission_code, t.permission_name, 'BUTTON',
       m.id, 'ai', t.action_code, 1, current_timestamp, current_timestamp
from (
    select 1 seq, 'ai:conversation:archive' permission_code, 'AI助手-会话归档' permission_name, 'conversation:archive' action_code union all
    select 2, 'ai:conversation:delete', 'AI助手-会话删除', 'conversation:delete'
) t
join sys_menu m on m.permission_code = 'ai:chat'
where not exists (select 1 from sys_permission p where p.permission_code = t.permission_code);

insert ignore into sys_role_permission (id, role_id, permission_id)
select 260611000600000 + row_number() over (order by source.role_id, source.permission_id) as id,
       source.role_id,
       source.permission_id
from (
    select distinct rp.role_id, p.id as permission_id
    from sys_role_permission rp
    join sys_permission base on base.id = rp.permission_id and base.permission_code = 'ai:chat'
    join sys_permission p on p.permission_code in ('ai:conversation:archive', 'ai:conversation:delete')
) source;

drop procedure if exists add_index_if_missing;
