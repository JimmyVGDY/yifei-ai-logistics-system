insert into demo_user (username, display_name, created_at)
select 'admin', 'Administrator', current_timestamp
where not exists (select 1 from demo_user where username = 'admin');

insert into logistics_customer (
    customer_code, customer_name, contact_name, contact_phone,
    province, city, address, status, created_at, updated_at
)
select 'CUST-SH-001', 'Shanghai Fresh Retail Co.', 'Alice Chen', '13800010001',
       'Shanghai', 'Shanghai', 'Pudong New Area, Zhangjiang Road 88', 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_customer where customer_code = 'CUST-SH-001');

insert into logistics_customer (
    customer_code, customer_name, contact_name, contact_phone,
    province, city, address, status, created_at, updated_at
)
select 'CUST-BJ-002', 'Beijing Smart Market Ltd.', 'Bob Li', '13800010002',
       'Beijing', 'Beijing', 'Chaoyang District, Guanghua Road 66', 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_customer where customer_code = 'CUST-BJ-002');

insert into logistics_customer (
    customer_code, customer_name, contact_name, contact_phone,
    province, city, address, status, created_at, updated_at
)
select 'CUST-GZ-003', 'Guangzhou Auto Parts Hub', 'Cindy Wang', '13800010003',
       'Guangdong', 'Guangzhou', 'Tianhe District, Software Road 18', 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_customer where customer_code = 'CUST-GZ-003');

insert into logistics_customer (
    customer_code, customer_name, contact_name, contact_phone,
    province, city, address, status, created_at, updated_at
)
select 'CUST-SZ-004', 'Shenzhen Cross Border Store', 'David Zhou', '13800010004',
       'Guangdong', 'Shenzhen', 'Nanshan District, Keji Road 9', 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_customer where customer_code = 'CUST-SZ-004');

insert into logistics_warehouse (
    warehouse_code, warehouse_name, province, city, address,
    manager_name, contact_phone, capacity_cubic, status, created_at, updated_at
)
select 'WH-SH-01', 'Shanghai East Warehouse', 'Shanghai', 'Shanghai', 'Pudong Logistics Park A1',
       'Henry Xu', '13900020001', 12000.00, 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_warehouse where warehouse_code = 'WH-SH-01');

insert into logistics_warehouse (
    warehouse_code, warehouse_name, province, city, address,
    manager_name, contact_phone, capacity_cubic, status, created_at, updated_at
)
select 'WH-BJ-01', 'Beijing North Warehouse', 'Beijing', 'Beijing', 'Daxing Logistics Zone B2',
       'Iris Zhao', '13900020002', 9000.00, 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_warehouse where warehouse_code = 'WH-BJ-01');

insert into logistics_warehouse (
    warehouse_code, warehouse_name, province, city, address,
    manager_name, contact_phone, capacity_cubic, status, created_at, updated_at
)
select 'WH-GZ-01', 'Guangzhou South Warehouse', 'Guangdong', 'Guangzhou', 'Baiyun Logistics Center C3',
       'Jack Huang', '13900020003', 15000.00, 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_warehouse where warehouse_code = 'WH-GZ-01');

insert into logistics_driver (
    driver_code, driver_name, phone, license_no, license_type, status, created_at, updated_at
)
select 'DRV-001', 'Driver Zhang', '13700030001', 'A2-310001', 'A2', 'AVAILABLE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_driver where driver_code = 'DRV-001');

insert into logistics_driver (
    driver_code, driver_name, phone, license_no, license_type, status, created_at, updated_at
)
select 'DRV-002', 'Driver Liu', '13700030002', 'A2-310002', 'A2', 'ON_ROUTE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_driver where driver_code = 'DRV-002');

insert into logistics_driver (
    driver_code, driver_name, phone, license_no, license_type, status, created_at, updated_at
)
select 'DRV-003', 'Driver Sun', '13700030003', 'B2-310003', 'B2', 'AVAILABLE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_driver where driver_code = 'DRV-003');

insert into logistics_vehicle (
    vehicle_no, vehicle_type, load_capacity_kg, volume_capacity_cubic, current_city, status, created_at, updated_at
)
select '沪A-LOG01', '9.6m Box Truck', 12000.00, 55.00, 'Shanghai', 'AVAILABLE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_vehicle where vehicle_no = '沪A-LOG01');

insert into logistics_vehicle (
    vehicle_no, vehicle_type, load_capacity_kg, volume_capacity_cubic, current_city, status, created_at, updated_at
)
select '京B-LOG02', 'Cold Chain Truck', 8000.00, 40.00, 'Beijing', 'ON_ROUTE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_vehicle where vehicle_no = '京B-LOG02');

insert into logistics_vehicle (
    vehicle_no, vehicle_type, load_capacity_kg, volume_capacity_cubic, current_city, status, created_at, updated_at
)
select '粤C-LOG03', 'Van', 3000.00, 18.00, 'Guangzhou', 'AVAILABLE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_vehicle where vehicle_no = '粤C-LOG03');

insert into logistics_route (
    route_code, origin_city, destination_city, distance_km, estimated_hours, status, created_at, updated_at
)
select 'RT-SH-BJ', 'Shanghai', 'Beijing', 1210.50, 20, 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_route where route_code = 'RT-SH-BJ');

insert into logistics_route (
    route_code, origin_city, destination_city, distance_km, estimated_hours, status, created_at, updated_at
)
select 'RT-SH-GZ', 'Shanghai', 'Guangzhou', 1435.20, 24, 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_route where route_code = 'RT-SH-GZ');

insert into logistics_route (
    route_code, origin_city, destination_city, distance_km, estimated_hours, status, created_at, updated_at
)
select 'RT-GZ-SZ', 'Guangzhou', 'Shenzhen', 136.80, 3, 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_route where route_code = 'RT-GZ-SZ');

insert into logistics_order (
    order_no, customer_id, route_id, warehouse_id, vehicle_id, driver_id,
    customer_name, sender_address, receiver_address, cargo_name,
    cargo_weight, cargo_volume, status, planned_pickup_time, planned_delivery_time,
    created_at, updated_at
)
select 'LO-DEMO-0001', c.id, r.id, w.id, v.id, d.id,
       c.customer_name, 'Pudong New Area, Shanghai', 'Chaoyang District, Beijing', 'Apparel Samples',
       12.500, 1.200, 'CREATED',
       timestampadd(hour, 2, current_timestamp), timestampadd(hour, 28, current_timestamp),
       current_timestamp, current_timestamp
from logistics_customer c
join logistics_route r on r.route_code = 'RT-SH-BJ'
join logistics_warehouse w on w.warehouse_code = 'WH-SH-01'
join logistics_vehicle v on v.vehicle_no = '沪A-LOG01'
join logistics_driver d on d.driver_code = 'DRV-001'
where c.customer_code = 'CUST-SH-001'
  and not exists (select 1 from logistics_order where order_no = 'LO-DEMO-0001');

insert into logistics_order (
    order_no, customer_id, route_id, warehouse_id, vehicle_id, driver_id,
    customer_name, sender_address, receiver_address, cargo_name,
    cargo_weight, cargo_volume, status, planned_pickup_time, planned_delivery_time,
    created_at, updated_at
)
select 'LO-DEMO-0002', c.id, r.id, w.id, v.id, d.id,
       c.customer_name, 'Baiyun District, Guangzhou', 'Nanshan District, Shenzhen', 'Auto Parts',
       860.000, 6.800, 'IN_TRANSIT',
       timestampadd(hour, -5, current_timestamp), timestampadd(hour, 4, current_timestamp),
       current_timestamp, current_timestamp
from logistics_customer c
join logistics_route r on r.route_code = 'RT-GZ-SZ'
join logistics_warehouse w on w.warehouse_code = 'WH-GZ-01'
join logistics_vehicle v on v.vehicle_no = '粤C-LOG03'
join logistics_driver d on d.driver_code = 'DRV-003'
where c.customer_code = 'CUST-GZ-003'
  and not exists (select 1 from logistics_order where order_no = 'LO-DEMO-0002');

insert into logistics_order (
    order_no, customer_id, route_id, warehouse_id, vehicle_id, driver_id,
    customer_name, sender_address, receiver_address, cargo_name,
    cargo_weight, cargo_volume, status, planned_pickup_time, planned_delivery_time,
    created_at, updated_at
)
select 'LO-DEMO-0003', c.id, r.id, w.id, v.id, d.id,
       c.customer_name, 'Shanghai Warehouse A1', 'Tianhe District, Guangzhou', 'Fresh Retail Goods',
       2450.000, 18.600, 'DELIVERED',
       timestampadd(day, -2, current_timestamp), timestampadd(day, -1, current_timestamp),
       current_timestamp, current_timestamp
from logistics_customer c
join logistics_route r on r.route_code = 'RT-SH-GZ'
join logistics_warehouse w on w.warehouse_code = 'WH-SH-01'
join logistics_vehicle v on v.vehicle_no = '京B-LOG02'
join logistics_driver d on d.driver_code = 'DRV-002'
where c.customer_code = 'CUST-SH-001'
  and not exists (select 1 from logistics_order where order_no = 'LO-DEMO-0003');

insert into logistics_order_tracking (
    order_no, tracking_status, location, description, operator_name, occurred_at, created_at
)
select 'LO-DEMO-0001', 'CREATED', 'Shanghai East Warehouse', 'Order created and waiting for pickup', 'System',
       timestampadd(hour, -1, current_timestamp), current_timestamp
where not exists (select 1 from logistics_order_tracking where order_no = 'LO-DEMO-0001' and tracking_status = 'CREATED');

insert into logistics_order_tracking (
    order_no, tracking_status, location, description, operator_name, occurred_at, created_at
)
select 'LO-DEMO-0002', 'PICKED_UP', 'Guangzhou South Warehouse', 'Cargo picked up by driver', 'Driver Sun',
       timestampadd(hour, -4, current_timestamp), current_timestamp
where not exists (select 1 from logistics_order_tracking where order_no = 'LO-DEMO-0002' and tracking_status = 'PICKED_UP');

insert into logistics_order_tracking (
    order_no, tracking_status, location, description, operator_name, occurred_at, created_at
)
select 'LO-DEMO-0002', 'IN_TRANSIT', 'Guangshen Expressway', 'Vehicle is on route to destination', 'Dispatch Center',
       timestampadd(hour, -2, current_timestamp), current_timestamp
where not exists (select 1 from logistics_order_tracking where order_no = 'LO-DEMO-0002' and tracking_status = 'IN_TRANSIT');

insert into logistics_order_tracking (
    order_no, tracking_status, location, description, operator_name, occurred_at, created_at
)
select 'LO-DEMO-0003', 'DELIVERED', 'Guangzhou Customer Site', 'Order delivered and signed', 'Driver Liu',
       timestampadd(hour, -20, current_timestamp), current_timestamp
where not exists (select 1 from logistics_order_tracking where order_no = 'LO-DEMO-0003' and tracking_status = 'DELIVERED');

insert into logistics_inventory (
    warehouse_id, sku_code, sku_name, quantity, locked_quantity, updated_at
)
select w.id, 'SKU-APPAREL-001', 'Apparel Sample Box', 320, 20, current_timestamp
from logistics_warehouse w
where w.warehouse_code = 'WH-SH-01'
  and not exists (select 1 from logistics_inventory i where i.warehouse_id = w.id and i.sku_code = 'SKU-APPAREL-001');

insert into logistics_inventory (
    warehouse_id, sku_code, sku_name, quantity, locked_quantity, updated_at
)
select w.id, 'SKU-FRESH-002', 'Fresh Retail Goods', 180, 45, current_timestamp
from logistics_warehouse w
where w.warehouse_code = 'WH-SH-01'
  and not exists (select 1 from logistics_inventory i where i.warehouse_id = w.id and i.sku_code = 'SKU-FRESH-002');

insert into logistics_inventory (
    warehouse_id, sku_code, sku_name, quantity, locked_quantity, updated_at
)
select w.id, 'SKU-AUTO-003', 'Auto Parts Package', 560, 80, current_timestamp
from logistics_warehouse w
where w.warehouse_code = 'WH-GZ-01'
  and not exists (select 1 from logistics_inventory i where i.warehouse_id = w.id and i.sku_code = 'SKU-AUTO-003');

insert into logistics_freight_bill (
    bill_no, order_no, base_amount, fuel_surcharge, discount_amount, payable_amount, pay_status, created_at, updated_at
)
select 'FB-DEMO-0001', 'LO-DEMO-0001', 280.00, 15.00, 0.00, 295.00, 'UNPAID', current_timestamp, current_timestamp
where not exists (select 1 from logistics_freight_bill where bill_no = 'FB-DEMO-0001');

insert into logistics_freight_bill (
    bill_no, order_no, base_amount, fuel_surcharge, discount_amount, payable_amount, pay_status, created_at, updated_at
)
select 'FB-DEMO-0002', 'LO-DEMO-0002', 680.00, 45.00, 20.00, 705.00, 'UNPAID', current_timestamp, current_timestamp
where not exists (select 1 from logistics_freight_bill where bill_no = 'FB-DEMO-0002');

insert into logistics_freight_bill (
    bill_no, order_no, base_amount, fuel_surcharge, discount_amount, payable_amount, pay_status, created_at, updated_at
)
select 'FB-DEMO-0003', 'LO-DEMO-0003', 1680.00, 120.00, 80.00, 1720.00, 'PAID', current_timestamp, current_timestamp
where not exists (select 1 from logistics_freight_bill where bill_no = 'FB-DEMO-0003');
