create table if not exists demo_user (
    id bigint primary key auto_increment,
    username varchar(64) not null unique,
    display_name varchar(128) not null,
    created_at timestamp not null
);

create table if not exists logistics_customer (
    id bigint primary key auto_increment,
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
    id bigint primary key auto_increment,
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
    id bigint primary key auto_increment,
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
    id bigint primary key auto_increment,
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
    id bigint primary key auto_increment,
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
    id bigint primary key auto_increment,
    order_no varchar(64) not null unique,
    customer_id bigint null,
    route_id bigint null,
    warehouse_id bigint null,
    vehicle_id bigint null,
    driver_id bigint null,
    customer_name varchar(128) not null,
    sender_address varchar(255) not null,
    receiver_address varchar(255) not null,
    cargo_name varchar(128) not null,
    cargo_weight decimal(12, 3) not null,
    cargo_volume decimal(12, 3) not null default 0,
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

create table if not exists logistics_order_tracking (
    id bigint primary key auto_increment,
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
    id bigint primary key auto_increment,
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
    id bigint primary key auto_increment,
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
