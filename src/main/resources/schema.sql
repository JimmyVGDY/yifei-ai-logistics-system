create table if not exists demo_user (
    id bigint primary key auto_increment,
    username varchar(64) not null unique,
    display_name varchar(128) not null,
    created_at timestamp not null
);
