insert into demo_user (username, display_name, created_at)
select 'admin', 'Administrator', current_timestamp
where not exists (select 1 from demo_user where username = 'admin');
