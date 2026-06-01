-- 客户账号绑定增量脚本：保留现有数据，为客户角色账号补充主账号/子账号标记。
-- 该脚本可重复执行；应用启动不会自动执行。

set @schema_name = database();

set @column_exists = (
    select count(1)
    from information_schema.columns
    where table_schema = @schema_name
      and table_name = 'sys_user'
      and column_name = 'customer_subject_type'
);
set @ddl = if(@column_exists = 0,
    'alter table sys_user add column customer_subject_type varchar(16) null comment ''客户主体类型：PERSONAL个人，ENTERPRISE企业'' after customer_id',
    'select 1'
);
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

set @column_exists = (
    select count(1)
    from information_schema.columns
    where table_schema = @schema_name
      and table_name = 'sys_user'
      and column_name = 'customer_account_type'
);
set @ddl = if(@column_exists = 0,
    'alter table sys_user add column customer_account_type varchar(16) null comment ''客户账号类型：MAIN主账号，SUB子账号'' after customer_id',
    'select 1'
);
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

set @index_exists = (
    select count(1)
    from information_schema.statistics
    where table_schema = @schema_name
      and table_name = 'sys_user'
      and index_name = 'idx_sys_user_customer'
);
set @ddl = if(@index_exists = 0,
    'create index idx_sys_user_customer on sys_user(customer_id)',
    'select 1'
);
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

-- 兼容早期本地演示账号：旧库里 customer 账号可能绑定到已经不存在的旧角色 ID，并且姓名曾出现编码误写。
update sys_user u
set u.role_id = (select r.id from sys_role r where r.role_code = 'CUSTOMER' limit 1)
where u.username = 'customer'
  and not exists (select 1 from sys_role r where r.id = u.role_id);

update sys_user
set real_name = '测试客户'
where username = 'customer'
  and real_name = '娴嬭瘯瀹㈡埛';

update sys_user u
join sys_role r on r.id = u.role_id
set u.customer_subject_type = 'ENTERPRISE'
where r.role_code = 'CUSTOMER'
  and u.customer_id is not null
  and u.customer_subject_type is null;

-- 已有客户角色账号按同一客户下最早创建的账号标记主账号，其余标记子账号。
update sys_user u
join sys_role r on r.id = u.role_id
left join (
    select customer_id, min(id) as main_user_id
    from (
        select ux.id, ux.customer_id
        from sys_user ux
        join sys_role rx on rx.id = ux.role_id
        where rx.role_code = 'CUSTOMER'
          and ux.customer_id is not null
    ) s
    group by customer_id
) m on m.customer_id = u.customer_id
set u.customer_account_type = case
    when u.customer_id is null then null
    when u.id = m.main_user_id then 'MAIN'
    else 'SUB'
end
where r.role_code = 'CUSTOMER'
  and u.customer_account_type is null;
