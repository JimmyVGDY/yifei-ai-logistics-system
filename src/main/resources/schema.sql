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
    mobile varchar(32) not null,
    email varchar(128) null,
    password varchar(128) not null,
    role_id bigint null,
    status tinyint not null,
    create_time timestamp not null,
    update_time timestamp not null,
    index idx_sys_user_role (role_id),
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

create table if not exists sys_operation_log (
    id bigint primary key,
    operation_id varchar(32) null,
    trace_id varchar(64) null,
    user_id varchar(32) null,
    username varchar(64) not null,
    role_code varchar(64) null,
    operation varchar(128) not null,
    request_uri varchar(255) not null,
    request_method varchar(16) not null,
    operation_status varchar(32) not null,
    cost_ms bigint null,
    operation_time timestamp not null,
    index idx_operation_log_operation_id (operation_id),
    index idx_operation_log_trace_id (trace_id),
    index idx_operation_log_user (username),
    index idx_operation_log_time (operation_time)
);

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
    contact_phone varchar(32) not null,
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
    contact_phone varchar(32) not null,
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
    phone varchar(32) not null,
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
