insert into demo_user (username, display_name, created_at)
select 'admin', 'Administrator', current_timestamp
where not exists (select 1 from demo_user where username = 'admin');

insert into sys_role (role_code, role_name, status, create_time, update_time)
select 'ADMIN', '系统管理员', 1, current_timestamp, current_timestamp
where not exists (select 1 from sys_role where role_code = 'ADMIN');

insert into sys_role (role_code, role_name, status, create_time, update_time)
select 'DISPATCHER', '调度人员', 1, current_timestamp, current_timestamp
where not exists (select 1 from sys_role where role_code = 'DISPATCHER');

insert into sys_role (role_code, role_name, status, create_time, update_time)
select 'FINANCE', '财务人员', 1, current_timestamp, current_timestamp
where not exists (select 1 from sys_role where role_code = 'FINANCE');

insert into sys_user (username, real_name, mobile, email, password, role_id, status, create_time, update_time)
select 'admin', '系统管理员', '138963311213', 'admin@xlh-logistics.local', 'xlh963311213', r.id, 1, current_timestamp, current_timestamp
from sys_role r
where r.role_code = 'ADMIN'
  and not exists (select 1 from sys_user where username = 'admin');

insert into sys_menu (parent_id, menu_name, menu_path, permission_code, sort_no, status, create_time, update_time)
select 0, '订单管理', '/orders', 'logistics:order:list', 10, 1, current_timestamp, current_timestamp
where not exists (select 1 from sys_menu where permission_code = 'logistics:order:list');

insert into sys_menu (parent_id, menu_name, menu_path, permission_code, sort_no, status, create_time, update_time)
select 0, '调度管理', '/dispatch', 'logistics:dispatch:list', 20, 1, current_timestamp, current_timestamp
where not exists (select 1 from sys_menu where permission_code = 'logistics:dispatch:list');

insert into sys_operation_log (username, operation, request_uri, request_method, operation_status, operation_time)
select 'admin', '系统初始化', '/system/init', 'SYSTEM', 'SUCCESS', current_timestamp
where not exists (select 1 from sys_operation_log where username = 'admin' and operation = '系统初始化');

insert into logistics_customer (
    customer_code, customer_name, contact_name, contact_phone,
    province, city, address, status, created_at, updated_at
)
select 'CUST-SH-001', '上海鲜达供应链有限公司', '陈静怡', '13800010001',
       '上海市', '上海', '浦东新区张江路 88 号', 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_customer where customer_code = 'CUST-SH-001');

insert into logistics_customer (
    customer_code, customer_name, contact_name, contact_phone,
    province, city, address, status, created_at, updated_at
)
select 'CUST-BJ-002', '北京智选商贸有限公司', '李明轩', '13800010002',
       '北京市', '北京', '朝阳区光华路 66 号', 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_customer where customer_code = 'CUST-BJ-002');

insert into logistics_customer (
    customer_code, customer_name, contact_name, contact_phone,
    province, city, address, status, created_at, updated_at
)
select 'CUST-GZ-003', '广州恒越汽配有限公司', '王雅婷', '13800010003',
       '广东省', '广州', '天河区软件路 18 号', 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_customer where customer_code = 'CUST-GZ-003');

insert into logistics_customer (
    customer_code, customer_name, contact_name, contact_phone,
    province, city, address, status, created_at, updated_at
)
select 'CUST-SZ-004', '深圳跨境优选贸易有限公司', '周启航', '13800010004',
       '广东省', '深圳', '南山区科技路 9 号', 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_customer where customer_code = 'CUST-SZ-004');

insert into logistics_warehouse (
    warehouse_code, warehouse_name, province, city, address,
    manager_name, contact_phone, capacity_cubic, status, created_at, updated_at
)
select 'WH-SH-01', '上海东区云仓', '上海市', '上海', '浦东物流园 A1 库区',
       '许文博', '13900020001', 12000.00, 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_warehouse where warehouse_code = 'WH-SH-01');

insert into logistics_warehouse (
    warehouse_code, warehouse_name, province, city, address,
    manager_name, contact_phone, capacity_cubic, status, created_at, updated_at
)
select 'WH-BJ-01', '北京北区分拨仓', '北京市', '北京', '大兴物流园 B2 库区',
       '赵雨桐', '13900020002', 9000.00, 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_warehouse where warehouse_code = 'WH-BJ-01');

insert into logistics_warehouse (
    warehouse_code, warehouse_name, province, city, address,
    manager_name, contact_phone, capacity_cubic, status, created_at, updated_at
)
select 'WH-GZ-01', '广州南区冷链仓', '广东省', '广州', '白云物流中心 C3 库区',
       '黄俊杰', '13900020003', 15000.00, 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_warehouse where warehouse_code = 'WH-GZ-01');

insert into logistics_driver (
    driver_code, driver_name, phone, license_no, license_type, status, created_at, updated_at
)
select 'DRV-001', '张志强', '13700030001', 'A2-310001', 'A2', 'AVAILABLE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_driver where driver_code = 'DRV-001');

insert into logistics_driver (
    driver_code, driver_name, phone, license_no, license_type, status, created_at, updated_at
)
select 'DRV-002', '刘建国', '13700030002', 'A2-310002', 'A2', 'ON_ROUTE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_driver where driver_code = 'DRV-002');

insert into logistics_driver (
    driver_code, driver_name, phone, license_no, license_type, status, created_at, updated_at
)
select 'DRV-003', '孙浩然', '13700030003', 'B2-310003', 'B2', 'AVAILABLE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_driver where driver_code = 'DRV-003');

insert into logistics_vehicle (
    vehicle_no, vehicle_type, load_capacity_kg, volume_capacity_cubic, current_city, status, created_at, updated_at
)
select '沪A-LOG01', '9.6米厢式货车', 12000.00, 55.00, '上海', 'AVAILABLE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_vehicle where vehicle_no = '沪A-LOG01');

insert into logistics_vehicle (
    vehicle_no, vehicle_type, load_capacity_kg, volume_capacity_cubic, current_city, status, created_at, updated_at
)
select '京B-LOG02', '冷链厢式货车', 8000.00, 40.00, '北京', 'ON_ROUTE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_vehicle where vehicle_no = '京B-LOG02');

insert into logistics_vehicle (
    vehicle_no, vehicle_type, load_capacity_kg, volume_capacity_cubic, current_city, status, created_at, updated_at
)
select '粤C-LOG03', '城市配送面包车', 3000.00, 18.00, '广州', 'AVAILABLE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_vehicle where vehicle_no = '粤C-LOG03');

insert into logistics_route (
    route_code, origin_city, destination_city, distance_km, estimated_hours, status, created_at, updated_at
)
select 'RT-SH-BJ', '上海', '北京', 1210.50, 20, 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_route where route_code = 'RT-SH-BJ');

insert into logistics_route (
    route_code, origin_city, destination_city, distance_km, estimated_hours, status, created_at, updated_at
)
select 'RT-SH-GZ', '上海', '广州', 1435.20, 24, 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_route where route_code = 'RT-SH-GZ');

insert into logistics_route (
    route_code, origin_city, destination_city, distance_km, estimated_hours, status, created_at, updated_at
)
select 'RT-GZ-SZ', '广州', '深圳', 136.80, 3, 'ACTIVE', current_timestamp, current_timestamp
where not exists (select 1 from logistics_route where route_code = 'RT-GZ-SZ');

insert into logistics_order (
    order_no, customer_id, route_id, warehouse_id, vehicle_id, driver_id,
    customer_name, sender_address, receiver_address, cargo_name,
    cargo_weight, cargo_volume, status, planned_pickup_time, planned_delivery_time,
    created_at, updated_at
)
select 'LO-DEMO-0001', c.id, r.id, w.id, v.id, d.id,
       c.customer_name, '上海市浦东新区发货仓', '北京市朝阳区收货点', '服装样品箱',
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
       c.customer_name, '广州市白云区发货仓', '深圳市南山区收货点', '汽车配件托盘',
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
       c.customer_name, '上海东区云仓 A1 库区', '广州市天河区客户现场', '生鲜零售周转箱',
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
select 'LO-DEMO-0001', 'CREATED', '上海东区云仓', '订单已创建，等待调度揽收', '系统',
       timestampadd(hour, -1, current_timestamp), current_timestamp
where not exists (select 1 from logistics_order_tracking where order_no = 'LO-DEMO-0001' and tracking_status = 'CREATED');

insert into logistics_order_tracking (
    order_no, tracking_status, location, description, operator_name, occurred_at, created_at
)
select 'LO-DEMO-0002', 'PICKED_UP', '广州南区冷链仓', '司机已完成货物揽收', '孙浩然',
       timestampadd(hour, -4, current_timestamp), current_timestamp
where not exists (select 1 from logistics_order_tracking where order_no = 'LO-DEMO-0002' and tracking_status = 'PICKED_UP');

insert into logistics_order_tracking (
    order_no, tracking_status, location, description, operator_name, occurred_at, created_at
)
select 'LO-DEMO-0002', 'IN_TRANSIT', '广深高速', '车辆正在前往目的网点', '调度中心',
       timestampadd(hour, -2, current_timestamp), current_timestamp
where not exists (select 1 from logistics_order_tracking where order_no = 'LO-DEMO-0002' and tracking_status = 'IN_TRANSIT');

insert into logistics_order_tracking (
    order_no, tracking_status, location, description, operator_name, occurred_at, created_at
)
select 'LO-DEMO-0003', 'DELIVERED', '广州客户现场', '客户已签收，订单完成配送', '刘建国',
       timestampadd(hour, -20, current_timestamp), current_timestamp
where not exists (select 1 from logistics_order_tracking where order_no = 'LO-DEMO-0003' and tracking_status = 'DELIVERED');

insert into logistics_inventory (
    warehouse_id, sku_code, sku_name, quantity, locked_quantity, updated_at
)
select w.id, 'SKU-APPAREL-001', '服装样品纸箱', 320, 20, current_timestamp
from logistics_warehouse w
where w.warehouse_code = 'WH-SH-01'
  and not exists (select 1 from logistics_inventory i where i.warehouse_id = w.id and i.sku_code = 'SKU-APPAREL-001');

insert into logistics_inventory (
    warehouse_id, sku_code, sku_name, quantity, locked_quantity, updated_at
)
select w.id, 'SKU-FRESH-002', '生鲜零售周转箱', 180, 45, current_timestamp
from logistics_warehouse w
where w.warehouse_code = 'WH-SH-01'
  and not exists (select 1 from logistics_inventory i where i.warehouse_id = w.id and i.sku_code = 'SKU-FRESH-002');

insert into logistics_inventory (
    warehouse_id, sku_code, sku_name, quantity, locked_quantity, updated_at
)
select w.id, 'SKU-AUTO-003', '汽车配件托盘', 560, 80, current_timestamp
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

insert into logistics_waybill (
    waybill_no, order_id, start_site, target_site, current_location, transport_status, create_time, update_time
)
select concat('WB-', o.order_no), o.id, '上海东仓网点', '北京朝阳网点', '上海东仓网点', 'WAIT_DISPATCH', current_timestamp, current_timestamp
from logistics_order o
where o.order_no = 'LO-DEMO-0001'
  and not exists (select 1 from logistics_waybill where waybill_no = concat('WB-', o.order_no));

insert into logistics_waybill (
    waybill_no, order_id, start_site, target_site, current_location, transport_status, create_time, update_time
)
select concat('WB-', o.order_no), o.id, '广州南仓网点', '深圳南山网点', '广深高速', 'IN_TRANSIT', current_timestamp, current_timestamp
from logistics_order o
where o.order_no = 'LO-DEMO-0002'
  and not exists (select 1 from logistics_waybill where waybill_no = concat('WB-', o.order_no));

insert into logistics_waybill (
    waybill_no, order_id, start_site, target_site, current_location, transport_status, create_time, update_time
)
select concat('WB-', o.order_no), o.id, '上海东仓网点', '广州天河网点', '广州客户现场', 'SIGNED', current_timestamp, current_timestamp
from logistics_order o
where o.order_no = 'LO-DEMO-0003'
  and not exists (select 1 from logistics_waybill where waybill_no = concat('WB-', o.order_no));

insert into logistics_dispatch (
    order_id, waybill_id, driver_id, vehicle_id, start_site, target_site,
    planned_departure_time, planned_arrival_time, dispatch_status, create_time, update_time
)
select o.id, w.id, d.id, v.id, w.start_site, w.target_site,
       timestampadd(hour, 1, current_timestamp), timestampadd(hour, 28, current_timestamp),
       'WAIT_DISPATCH', current_timestamp, current_timestamp
from logistics_order o
join logistics_waybill w on w.order_id = o.id
join logistics_driver d on d.driver_code = 'DRV-001'
join logistics_vehicle v on v.vehicle_no = '沪A-LOG01'
where o.order_no = 'LO-DEMO-0001'
  and not exists (select 1 from logistics_dispatch where order_id = o.id);

insert into logistics_dispatch (
    order_id, waybill_id, driver_id, vehicle_id, start_site, target_site,
    planned_departure_time, planned_arrival_time, dispatch_status, create_time, update_time
)
select o.id, w.id, d.id, v.id, w.start_site, w.target_site,
       timestampadd(hour, -5, current_timestamp), timestampadd(hour, 4, current_timestamp),
       'DISPATCHED', current_timestamp, current_timestamp
from logistics_order o
join logistics_waybill w on w.order_id = o.id
join logistics_driver d on d.driver_code = 'DRV-003'
join logistics_vehicle v on v.vehicle_no = '粤C-LOG03'
where o.order_no = 'LO-DEMO-0002'
  and not exists (select 1 from logistics_dispatch where order_id = o.id);

insert into logistics_task (
    task_no, order_id, waybill_id, dispatch_id, driver_id, vehicle_id, task_status, proof_url, create_time, update_time
)
select concat('TASK-', o.order_no), o.id, w.id, dp.id, dp.driver_id, dp.vehicle_id, '待接单', null, current_timestamp, current_timestamp
from logistics_order o
join logistics_waybill w on w.order_id = o.id
join logistics_dispatch dp on dp.order_id = o.id
where o.order_no = 'LO-DEMO-0001'
  and not exists (select 1 from logistics_task where task_no = concat('TASK-', o.order_no));

insert into logistics_task (
    task_no, order_id, waybill_id, dispatch_id, driver_id, vehicle_id, task_status, proof_url, create_time, update_time
)
select concat('TASK-', o.order_no), o.id, w.id, dp.id, dp.driver_id, dp.vehicle_id, '运输中', null, current_timestamp, current_timestamp
from logistics_order o
join logistics_waybill w on w.order_id = o.id
join logistics_dispatch dp on dp.order_id = o.id
where o.order_no = 'LO-DEMO-0002'
  and not exists (select 1 from logistics_task where task_no = concat('TASK-', o.order_no));

insert into logistics_track (
    order_id, waybill_id, current_status, current_location, operator_name, operation_desc, operation_time
)
select o.id, w.id, 'WAIT_DISPATCH', '上海东仓网点', '系统', '订单已创建，等待调度', timestampadd(hour, -1, current_timestamp)
from logistics_order o
join logistics_waybill w on w.order_id = o.id
where o.order_no = 'LO-DEMO-0001'
  and not exists (select 1 from logistics_track where order_id = o.id and current_status = 'WAIT_DISPATCH');

insert into logistics_track (
    order_id, waybill_id, current_status, current_location, operator_name, operation_desc, operation_time
)
select o.id, w.id, 'IN_TRANSIT', '广深高速', '调度中心', '车辆正在前往目的网点', timestampadd(hour, -2, current_timestamp)
from logistics_order o
join logistics_waybill w on w.order_id = o.id
where o.order_no = 'LO-DEMO-0002'
  and not exists (select 1 from logistics_track where order_id = o.id and current_status = 'IN_TRANSIT');

insert into logistics_exception (
    order_id, task_id, exception_type, exception_desc, exception_status, report_user, report_time, handle_user, handle_time
)
select o.id, t.id, '地址错误', '客户收件地址门牌号缺失，已联系客户确认', 'PROCESSING', '孙浩然',
       timestampadd(hour, -1, current_timestamp), '李文涛', null
from logistics_order o
join logistics_task t on t.order_id = o.id
where o.order_no = 'LO-DEMO-0002'
  and not exists (select 1 from logistics_exception where order_id = o.id and exception_type = '地址错误');

insert into logistics_fee (
    order_id, base_fee, weight_fee, distance_fee, additional_fee, discount_fee,
    payable_fee, actual_fee, payment_status, create_time, update_time
)
select o.id, 280.00, 25.00, 120.00, 15.00, 0.00, 440.00, 0.00, 'UNPAID', current_timestamp, current_timestamp
from logistics_order o
where o.order_no = 'LO-DEMO-0001'
  and not exists (select 1 from logistics_fee where order_id = o.id);

insert into logistics_fee (
    order_id, base_fee, weight_fee, distance_fee, additional_fee, discount_fee,
    payable_fee, actual_fee, payment_status, create_time, update_time
)
select o.id, 680.00, 172.00, 30.00, 45.00, 20.00, 907.00, 0.00, 'UNPAID', current_timestamp, current_timestamp
from logistics_order o
where o.order_no = 'LO-DEMO-0002'
  and not exists (select 1 from logistics_fee where order_id = o.id);
