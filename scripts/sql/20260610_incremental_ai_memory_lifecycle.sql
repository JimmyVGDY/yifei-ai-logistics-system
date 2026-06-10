-- ============================================
-- AI 长期记忆生命周期管理（2026-06-10）
-- 说明：可重复执行；不清空、不覆盖现有 ai_user_memory 数据。
-- ============================================

drop procedure if exists add_column_if_missing;
drop procedure if exists add_index_if_missing;

delimiter //

create procedure add_column_if_missing(
    in p_table_name varchar(64),
    in p_column_name varchar(64),
    in p_column_definition text
)
begin
    if not exists (
        select 1
        from information_schema.columns
        where table_schema = database()
          and table_name = p_table_name
          and column_name = p_column_name
    ) then
        set @ddl = concat('alter table ', p_table_name, ' add column ', p_column_name, ' ', p_column_definition);
        prepare stmt from @ddl;
        execute stmt;
        deallocate prepare stmt;
    end if;
end//

create procedure add_index_if_missing(
    in p_table_name varchar(64),
    in p_index_name varchar(64),
    in p_index_definition text
)
begin
    if not exists (
        select 1
        from information_schema.statistics
        where table_schema = database()
          and table_name = p_table_name
          and index_name = p_index_name
    ) then
        set @ddl = concat('alter table ', p_table_name, ' add index ', p_index_name, ' ', p_index_definition);
        prepare stmt from @ddl;
        execute stmt;
        deallocate prepare stmt;
    end if;
end//

delimiter ;

call add_column_if_missing('ai_user_memory', 'reinforce_count',
    'int not null default 0 comment ''强化计数'' after recall_count');

call add_column_if_missing('ai_user_memory', 'last_reinforced_at',
    'datetime null comment ''最后强化时间'' after reinforce_count');

call add_column_if_missing('ai_user_memory', 'status',
    'varchar(20) not null default ''ACTIVE'' comment ''生命周期状态'' after last_reinforced_at');

update ai_user_memory set status = 'ACTIVE' where status is null or status = '';

call add_index_if_missing('ai_user_memory', 'idx_ai_memory_status', '(status, last_reinforced_at)');

drop procedure if exists add_index_if_missing;
drop procedure if exists add_column_if_missing;
