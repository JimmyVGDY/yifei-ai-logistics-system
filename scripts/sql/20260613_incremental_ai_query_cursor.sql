-- 用途：AI 查询结果游标，用于“继续看、查看剩余、下一页”等多轮追问。
-- 说明：可重复执行；不清空、不重建现有业务数据。
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

create table if not exists ai_query_cursor (
    id bigint primary key comment '短位随机主键',
    cursor_id varchar(64) not null comment 'AI 查询游标 ID',
    conversation_id varchar(64) not null comment 'AI 会话 ID',
    user_id varchar(64) not null comment '登录用户 ID，保留原值用于审计',
    user_code varchar(64) null comment '用户业务编号，保留原值用于审计',
    tool_type varchar(64) not null comment '工具类型：MODULE/GLOBAL/JOINED/SQL/DASHBOARD',
    tool_name varchar(128) null comment '工具中文名称',
    module_code varchar(64) null comment '业务模块编码',
    module_name varchar(128) null comment '业务模块中文名称',
    keyword varchar(255) null comment '脱敏后的关键词',
    start_time varchar(32) null comment '查询开始时间',
    end_time varchar(32) null comment '查询结束时间',
    status_filter varchar(64) null comment '状态筛选',
    page int not null default 1 comment '当前页',
    page_size int not null default 10 comment '每页条数',
    total bigint not null default 0 comment '命中总数',
    returned_count int not null default 0 comment '已返回条数',
    query_summary varchar(1000) null comment '脱敏查询摘要',
    expires_at datetime not null comment '过期时间',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0 comment '逻辑删除标记',
    unique key uk_ai_query_cursor_id (cursor_id),
    key idx_ai_query_cursor_conversation (conversation_id, user_id, user_code, deleted, expires_at),
    key idx_ai_query_cursor_user_time (user_id, user_code, created_at)
) comment='AI查询结果游标表';

call add_index_if_missing('ai_conversation', 'idx_ai_conversation_context_time', '(conversation_id, user_id, user_code, last_message_at)');

drop procedure if exists add_index_if_missing;
