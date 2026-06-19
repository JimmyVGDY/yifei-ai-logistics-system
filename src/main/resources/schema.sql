create table if not exists demo_user (
    id bigint primary key,
    username varchar(64) not null unique,
    display_name varchar(128) not null,
    created_at timestamp not null
);

create table if not exists sys_role (
    id bigint primary key,
    role_code varchar(64) not null unique,
    role_name varchar(64) not null,
    status tinyint not null,
    create_time timestamp not null,
    update_time timestamp not null
);

create table if not exists sys_user (
    id bigint primary key,
    user_code varchar(32) not null unique,
    username varchar(64) not null unique,
    real_name varchar(64) not null,
    mobile varchar(128) not null,
    mobile_hash varchar(128) null,
    email varchar(128) null,
    password varchar(128) not null,
    role_id bigint null,
    customer_subject_type varchar(16) null comment '客户主体类型：PERSONAL个人，ENTERPRISE企业',
    customer_account_type varchar(16) null comment '客户账号类型：MAIN主账号，SUB子账号',
    customer_id bigint null comment '关联的物流客户ID，用于客户角色数据权限隔离',
    status tinyint not null,
    create_time timestamp not null,
    update_time timestamp not null,
    index idx_sys_user_role (role_id),
    index idx_sys_user_customer (customer_id),
    index idx_sys_user_mobile_hash (mobile_hash),
    index idx_sys_user_status (status)
);

create table if not exists sys_menu (
    id bigint primary key,
    parent_id bigint not null default 0,
    menu_name varchar(64) not null,
    menu_path varchar(128) not null,
    permission_code varchar(128) not null,
    sort_no int not null,
    status tinyint not null,
    create_time timestamp not null,
    update_time timestamp not null
);

create table if not exists sys_user_role (
    id bigint primary key,
    user_id bigint not null,
    role_id bigint not null,
    unique key uk_sys_user_role (user_id, role_id)
);

create table if not exists sys_role_menu (
    id bigint primary key,
    role_id bigint not null,
    menu_id bigint not null,
    unique key uk_sys_role_menu (role_id, menu_id)
);

create table if not exists sys_permission (
    id bigint primary key,
    permission_code varchar(128) not null unique,
    permission_name varchar(128) not null,
    permission_type varchar(32) not null,
    module_code varchar(64) not null,
    action_code varchar(64) not null,
    menu_id bigint null,
    sensitive_flag tinyint not null default 0 comment '敏感列标记：1敏感，0普通',
    sort_no int not null,
    status tinyint not null,
    create_time timestamp not null,
    update_time timestamp not null,
    deleted tinyint not null default 0 comment '逻辑删除：0未删除，1已删除',
    version int not null default 0 comment '乐观锁版本号',
    index idx_sys_permission_menu (menu_id),
    index idx_sys_permission_module (module_code, action_code),
    index idx_sys_permission_status (status),
    index idx_sp_deleted (deleted)
);

create table if not exists sys_role_permission (
    id bigint primary key,
    role_id bigint not null,
    permission_id bigint not null,
    unique key uk_sys_role_permission (role_id, permission_id),
    index idx_sys_role_permission_role (role_id),
    deleted tinyint not null default 0 comment '逻辑删除：0未删除，1已删除',
    version int not null default 0 comment '乐观锁版本号',
    index idx_srp_deleted (deleted)
);

create table if not exists sys_user_permission (
    id bigint primary key,
    user_id bigint not null,
    permission_id bigint not null,
    grant_type varchar(16) not null,
    create_time timestamp not null,
    update_time timestamp not null,
    unique key uk_sys_user_permission (user_id, permission_id),
    index idx_sys_user_permission_user (user_id),
    index idx_sys_user_permission_grant (grant_type),
    deleted tinyint not null default 0 comment '逻辑删除：0未删除，1已删除',
    version int not null default 0 comment '乐观锁版本号',
    index idx_sup_deleted (deleted)
);

create table if not exists sys_operation_log (
    id bigint primary key,
    operation_id varchar(32) null,
    trace_id varchar(64) null,
    login_session_id varchar(32) null comment '登录会话审计ID，串联一次登录期间的所有操作',
    user_id varchar(32) null,
    user_code varchar(32) null,
    username varchar(64) not null,
    role_code varchar(64) null,
    operation varchar(128) not null,
    request_uri varchar(255) not null,
    request_method varchar(16) not null,
    operation_status varchar(32) not null,
    cost_ms bigint null,
    error_message text null comment '异常信息，操作失败时记录异常原因便于排障',
    client_ip varchar(45) null comment '客户端 IP，优先取代理头中的真实 IP',
    user_agent varchar(255) null comment '浏览器或客户端标识',
    request_params text null comment '安全请求参数摘要，不记录密码和 token',
    target_id varchar(64) null comment '操作对象 ID，例如记录 ID、角色 ID、订单号',
    change_summary text null comment '脱敏后的操作前后变化摘要',
    operation_source varchar(32) null comment '操作来源：USER、USER_TO_AI、AI_TOOL、AI_RESPONSE、SYSTEM',
    executor_type varchar(32) null comment '执行者类型：USER、AI、SYSTEM',
    ai_conversation_id varchar(64) null comment 'AI 会话 ID，用于串联一次 AI 对话',
    ai_message_id varchar(64) null comment 'AI 单条消息 ID，预留给后续消息级审计',
    ai_tool_name varchar(64) null comment 'AI 工具名称，例如业务数据查询、全局只读查找、日志排障',
    ai_tool_target varchar(128) null comment 'AI 工具目标，例如客户管理、运单管理',
    ai_readonly tinyint null comment 'AI 工具是否只读，1=只读，0=非只读',
    ai_prompt_summary text null comment '脱敏后的用户问题摘要',
    ai_result_summary text null comment '脱敏后的 AI 工具或回答结果摘要',
    operation_time timestamp not null,
    index idx_operation_log_operation_id (operation_id),
    index idx_operation_log_trace_id (trace_id),
    index idx_operation_log_login_session_id (login_session_id),
    index idx_operation_log_user_code (user_code),
    index idx_operation_log_target (target_id),
    index idx_operation_log_ai_source (operation_source, executor_type),
    index idx_operation_log_ai_conversation (ai_conversation_id),
    index idx_operation_log_user (username),
    index idx_operation_log_time (operation_time)
);

create table if not exists ai_conversation (
    id bigint primary key comment '短位随机主键',
    conversation_id varchar(64) not null comment 'AI 会话 ID',
    user_id varchar(64) not null comment '登录用户ID，保持原值用于审计追踪',
    user_code varchar(64) null comment '登录用户业务编号，保持原值用于审计追踪',
    title varchar(255) not null comment '脱敏后的会话标题',
    status varchar(32) not null default 'ACTIVE' comment 'ACTIVE/ARCHIVED/DELETED',
    context_snapshot text null comment '脱敏后的会话上下文快照',
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

create table if not exists sys_uploaded_file (
    id bigint primary key,
    original_name varchar(255) not null,
    stored_name varchar(255) not null,
    relative_path varchar(255) not null,
    file_size bigint not null,
    content_type varchar(128) null,
    upload_user varchar(64) not null,
    upload_time timestamp not null,
    index idx_uploaded_file_time (upload_time)
);

create table if not exists logistics_customer (
    id bigint primary key,
    customer_code varchar(32) not null unique,
    customer_name varchar(128) not null,
    contact_name varchar(64) not null,
    contact_phone varchar(128) not null,
    province varchar(64) not null,
    city varchar(64) not null,
    address varchar(255) not null,
    status varchar(32) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    index idx_logistics_customer_city (city),
    index idx_logistics_customer_status (status)
);

create table if not exists logistics_warehouse (
    id bigint primary key,
    warehouse_code varchar(32) not null unique,
    warehouse_name varchar(128) not null,
    province varchar(64) not null,
    city varchar(64) not null,
    address varchar(255) not null,
    manager_name varchar(64) not null,
    contact_phone varchar(128) not null,
    capacity_cubic decimal(12, 2) not null,
    status varchar(32) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    index idx_logistics_warehouse_city (city),
    index idx_logistics_warehouse_status (status)
);

create table if not exists logistics_driver (
    id bigint primary key,
    driver_code varchar(32) not null unique,
    driver_name varchar(64) not null,
    phone varchar(128) not null,
    license_no varchar(64) not null,
    license_type varchar(32) not null,
    status varchar(32) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    index idx_logistics_driver_status (status)
);

create table if not exists logistics_vehicle (
    id bigint primary key,
    vehicle_no varchar(32) not null unique,
    vehicle_type varchar(64) not null,
    load_capacity_kg decimal(12, 2) not null,
    volume_capacity_cubic decimal(12, 2) not null,
    current_city varchar(64) not null,
    status varchar(32) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    index idx_logistics_vehicle_city (current_city),
    index idx_logistics_vehicle_status (status)
);

create table if not exists logistics_route (
    id bigint primary key,
    route_code varchar(32) not null unique,
    origin_city varchar(64) not null,
    destination_city varchar(64) not null,
    distance_km decimal(12, 2) not null,
    estimated_hours int not null,
    status varchar(32) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    index idx_logistics_route_city (origin_city, destination_city)
);

create table if not exists logistics_order (
    id bigint primary key,
    order_no varchar(64) not null unique,
    customer_id bigint null,
    route_id bigint null,
    warehouse_id bigint null,
    vehicle_id bigint null,
    driver_id bigint null,
    customer_name varchar(128) not null,
    sender_address varchar(255) not null,
    receiver_address varchar(255) not null,
    cargo_name varchar(128),
    cargo_weight decimal(12, 3),
    cargo_volume decimal(12, 3) default 0,
    status varchar(32) not null,
    planned_pickup_time timestamp null,
    planned_delivery_time timestamp null,
    created_at timestamp not null,
    updated_at timestamp not null,
    index idx_logistics_order_customer (customer_id),
    index idx_logistics_order_route (route_id),
    index idx_logistics_order_status (status),
    index idx_logistics_order_created_at (created_at)
);

create table if not exists logistics_waybill (
    id bigint primary key,
    waybill_no varchar(64) not null unique,
    order_id bigint not null,
    start_site varchar(128) not null,
    target_site varchar(128) not null,
    current_location varchar(128) not null,
    transport_status varchar(32) not null,
    create_time timestamp not null,
    update_time timestamp not null,
    index idx_waybill_order (order_id),
    index idx_waybill_status (transport_status)
);

create table if not exists logistics_dispatch (
    id bigint primary key,
    order_id bigint not null,
    waybill_id bigint not null,
    driver_id bigint not null,
    vehicle_id bigint not null,
    start_site varchar(128) not null,
    target_site varchar(128) not null,
    planned_departure_time timestamp null,
    planned_arrival_time timestamp null,
    dispatch_status varchar(32) not null,
    create_time timestamp not null,
    update_time timestamp not null,
    index idx_dispatch_order (order_id),
    index idx_dispatch_driver (driver_id),
    index idx_dispatch_vehicle (vehicle_id),
    index idx_dispatch_status (dispatch_status)
);

create table if not exists logistics_task (
    id bigint primary key,
    task_no varchar(64) not null unique,
    order_id bigint not null,
    waybill_id bigint not null,
    dispatch_id bigint not null,
    driver_id bigint not null,
    vehicle_id bigint not null,
    task_status varchar(32) not null,
    proof_url varchar(255) null,
    create_time timestamp not null,
    update_time timestamp not null,
    index idx_task_order (order_id),
    index idx_task_driver (driver_id),
    index idx_task_status (task_status)
);

create table if not exists logistics_track (
    id bigint primary key,
    order_id bigint not null,
    waybill_id bigint not null,
    current_status varchar(32) not null,
    current_location varchar(128) not null,
    operator_name varchar(64) not null,
    operation_desc varchar(255) not null,
    operation_time timestamp not null,
    index idx_track_order (order_id),
    index idx_track_waybill (waybill_id),
    index idx_track_time (operation_time)
);

create table if not exists logistics_exception (
    id bigint primary key,
    order_id bigint not null,
    task_id bigint null,
    exception_type varchar(64) not null,
    exception_desc varchar(255) not null,
    exception_status varchar(32) not null,
    report_user varchar(64) not null,
    report_time timestamp not null,
    handle_user varchar(64) null,
    handle_time timestamp null,
    index idx_exception_order (order_id),
    index idx_exception_status (exception_status)
);

create table if not exists logistics_fee (
    id bigint primary key,
    order_id bigint not null,
    base_fee decimal(12, 2) not null,
    weight_fee decimal(12, 2) not null,
    distance_fee decimal(12, 2) not null,
    additional_fee decimal(12, 2) not null,
    discount_fee decimal(12, 2) not null,
    payable_fee decimal(12, 2) not null,
    actual_fee decimal(12, 2) not null,
    payment_status varchar(32) not null,
    create_time timestamp not null,
    update_time timestamp not null,
    index idx_fee_order (order_id),
    index idx_fee_payment_status (payment_status)
);

create table if not exists logistics_order_tracking (
    id bigint primary key,
    order_no varchar(64) not null,
    tracking_status varchar(32) not null,
    location varchar(128) not null,
    description varchar(255) not null,
    operator_name varchar(64) not null,
    occurred_at timestamp not null,
    created_at timestamp not null,
    index idx_tracking_order_no (order_no),
    index idx_tracking_occurred_at (occurred_at)
);

create table if not exists logistics_inventory (
    id bigint primary key,
    warehouse_id bigint not null,
    sku_code varchar(64) not null,
    sku_name varchar(128) not null,
    quantity int not null,
    locked_quantity int not null,
    updated_at timestamp not null,
    unique key uk_inventory_warehouse_sku (warehouse_id, sku_code),
    index idx_inventory_sku (sku_code)
);

create table if not exists logistics_freight_bill (
    id bigint primary key,
    bill_no varchar(64) not null unique,
    order_no varchar(64) not null,
    base_amount decimal(12, 2) not null,
    fuel_surcharge decimal(12, 2) not null,
    discount_amount decimal(12, 2) not null,
    payable_amount decimal(12, 2) not null,
    pay_status varchar(32) not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    index idx_freight_order_no (order_no),
    index idx_freight_pay_status (pay_status)
);

-- 登录历史表 — 记录每次登录尝试的 IP/UA，支撑异常设备检测和验证码触发
create table if not exists sys_login_history (
    id bigint primary key,
    user_id bigint not null comment '登录用户ID',
    username varchar(64) not null comment '登录用户名',
    login_ip varchar(64) not null comment '客户端IP地址',
    user_agent varchar(512) null comment '客户端User-Agent标识',
    login_result varchar(20) not null comment '登录结果：SUCCESS/FAIL/CAPTCHA_REQUIRED',
    fail_reason varchar(128) null comment '失败原因',
    require_captcha tinyint default 0 comment '是否需要验证码',
    login_time timestamp not null comment '登录时间',
    deleted tinyint not null default 0 comment '逻辑删除：0未删除，1已删除',
    version int not null default 0 comment '乐观锁版本号',
    index idx_login_history_user_time (user_id, login_time),
    index idx_login_history_ip (login_ip),
    index idx_slh_deleted (deleted)
);
