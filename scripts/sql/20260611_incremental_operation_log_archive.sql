-- Operation log archive table and archiving procedure
-- Move records older than retention period from sys_operation_log to archive table.
-- Called monthly by XXL-Job handler operationLogArchive.

delimiter //

drop procedure if exists create_archive_table//
create procedure create_archive_table()
begin
    if not exists (
        select 1 from information_schema.tables
        where table_schema = database() and table_name = 'sys_operation_log_archive'
    ) then
        create table sys_operation_log_archive like sys_operation_log;
        alter table sys_operation_log_archive
            add column archived_at timestamp not null default current_timestamp comment 'archive_time',
            add index idx_archive_archived_at (archived_at);
    end if;
end//

drop procedure if exists archive_operation_logs//
create procedure archive_operation_logs(in p_retention_days int)
begin
    declare v_batch_size int default 5000;
    declare v_cutoff datetime;
    declare v_affected int default 0;
    declare v_total int default 0;

    set v_cutoff = date_sub(now(), interval p_retention_days day);

    -- Create temp table to hold batch IDs
    drop temporary table if exists tmp_archive_ids;
    create temporary table tmp_archive_ids (id bigint primary key);

    archive_loop: loop
        truncate table tmp_archive_ids;

        start transaction;

        insert into tmp_archive_ids
        select id from sys_operation_log
        where operation_time < v_cutoff
        order by operation_time
        limit v_batch_size;

        set v_affected = row_count();
        if v_affected = 0 then
            rollback;
            leave archive_loop;
        end if;

        insert into sys_operation_log_archive
        select t.*, now() from sys_operation_log t
        inner join tmp_archive_ids a on t.id = a.id;

        delete t from sys_operation_log t
        inner join tmp_archive_ids a on t.id = a.id;

        set v_total = v_total + v_affected;
        commit;
    end loop archive_loop;

    drop temporary table if exists tmp_archive_ids;
    select v_total as rows_archived;
end//

delimiter ;

call create_archive_table();
drop procedure if exists create_archive_table;
