use logistics_management;

drop procedure if exists seed_logistics_mock_100;

delimiter //

create procedure seed_logistics_mock_100()
begin
    declare i int default 1;
    declare mock_city varchar(64);
    declare mock_status varchar(32);
    declare customer_id_value bigint;
    declare warehouse_id_value bigint;
    declare driver_id_value bigint;
    declare vehicle_id_value bigint;
    declare route_id_value bigint;
    declare order_no_value varchar(64);

    while i <= 100 do
        set mock_city = case mod(i, 8)
            when 0 then 'Shanghai'
            when 1 then 'Beijing'
            when 2 then 'Guangzhou'
            when 3 then 'Shenzhen'
            when 4 then 'Hangzhou'
            when 5 then 'Nanjing'
            when 6 then 'Chengdu'
            else 'Wuhan'
        end;

        insert into logistics_customer (
            customer_code, customer_name, contact_name, contact_phone,
            province, city, address, status, created_at, updated_at
        )
        select concat('MOCK-CUST-', lpad(i, 3, '0')),
               concat('Mock Customer ', lpad(i, 3, '0')),
               concat('Contact ', lpad(i, 3, '0')),
               concat('1389', lpad(i, 7, '0')),
               'Mock Province',
               mock_city,
               concat(mock_city, ' Logistics Street ', i),
               if(mod(i, 10) = 0, 'PAUSED', 'ACTIVE'),
               current_timestamp,
               current_timestamp
        where not exists (
            select 1 from logistics_customer
            where customer_code = concat('MOCK-CUST-', lpad(i, 3, '0'))
        );

        insert into logistics_warehouse (
            warehouse_code, warehouse_name, province, city, address,
            manager_name, contact_phone, capacity_cubic, status, created_at, updated_at
        )
        select concat('MOCK-WH-', lpad(i, 3, '0')),
               concat(mock_city, ' Mock Warehouse ', lpad(i, 3, '0')),
               'Mock Province',
               mock_city,
               concat(mock_city, ' Warehouse Park ', i),
               concat('Manager ', lpad(i, 3, '0')),
               concat('1398', lpad(i, 7, '0')),
               5000 + i * 25,
               if(mod(i, 15) = 0, 'MAINTENANCE', 'ACTIVE'),
               current_timestamp,
               current_timestamp
        where not exists (
            select 1 from logistics_warehouse
            where warehouse_code = concat('MOCK-WH-', lpad(i, 3, '0'))
        );

        insert into logistics_driver (
            driver_code, driver_name, phone, license_no, license_type, status, created_at, updated_at
        )
        select concat('MOCK-DRV-', lpad(i, 3, '0')),
               concat('Mock Driver ', lpad(i, 3, '0')),
               concat('1378', lpad(i, 7, '0')),
               concat('LIC-MOCK-', lpad(i, 5, '0')),
               if(mod(i, 3) = 0, 'B2', 'A2'),
               case mod(i, 4)
                   when 0 then 'ON_ROUTE'
                   when 1 then 'AVAILABLE'
                   when 2 then 'AVAILABLE'
                   else 'RESTING'
               end,
               current_timestamp,
               current_timestamp
        where not exists (
            select 1 from logistics_driver
            where driver_code = concat('MOCK-DRV-', lpad(i, 3, '0'))
        );

        insert into logistics_vehicle (
            vehicle_no, vehicle_type, load_capacity_kg, volume_capacity_cubic,
            current_city, status, created_at, updated_at
        )
        select concat('MOCK-VEH-', lpad(i, 3, '0')),
               case mod(i, 4)
                   when 0 then 'Cold Chain Truck'
                   when 1 then '9.6m Box Truck'
                   when 2 then 'Van'
                   else 'Heavy Truck'
               end,
               3000 + i * 120,
               18 + mod(i, 50),
               mock_city,
               case mod(i, 5)
                   when 0 then 'MAINTENANCE'
                   when 1 then 'AVAILABLE'
                   when 2 then 'AVAILABLE'
                   else 'ON_ROUTE'
               end,
               current_timestamp,
               current_timestamp
        where not exists (
            select 1 from logistics_vehicle
            where vehicle_no = concat('MOCK-VEH-', lpad(i, 3, '0'))
        );

        insert into logistics_route (
            route_code, origin_city, destination_city, distance_km,
            estimated_hours, status, created_at, updated_at
        )
        select concat('MOCK-RT-', lpad(i, 3, '0')),
               mock_city,
               case mod(i + 3, 8)
                   when 0 then 'Shanghai'
                   when 1 then 'Beijing'
                   when 2 then 'Guangzhou'
                   when 3 then 'Shenzhen'
                   when 4 then 'Hangzhou'
                   when 5 then 'Nanjing'
                   when 6 then 'Chengdu'
                   else 'Wuhan'
               end,
               120 + i * 13.5,
               2 + mod(i, 36),
               if(mod(i, 20) = 0, 'PAUSED', 'ACTIVE'),
               current_timestamp,
               current_timestamp
        where not exists (
            select 1 from logistics_route
            where route_code = concat('MOCK-RT-', lpad(i, 3, '0'))
        );

        set @ref_index = 1 + mod(i - 1, 100);
        select id into customer_id_value
        from logistics_customer
        where customer_code = concat('MOCK-CUST-', lpad(@ref_index, 3, '0'));

        select id into warehouse_id_value
        from logistics_warehouse
        where warehouse_code = concat('MOCK-WH-', lpad(@ref_index, 3, '0'));

        select id into driver_id_value
        from logistics_driver
        where driver_code = concat('MOCK-DRV-', lpad(@ref_index, 3, '0'));

        select id into vehicle_id_value
        from logistics_vehicle
        where vehicle_no = concat('MOCK-VEH-', lpad(@ref_index, 3, '0'));

        select id into route_id_value
        from logistics_route
        where route_code = concat('MOCK-RT-', lpad(@ref_index, 3, '0'));

        set order_no_value = concat('MOCK-ORDER-', lpad(i, 4, '0'));
        set mock_status = case mod(i, 5)
            when 0 then 'DELIVERED'
            when 1 then 'CREATED'
            when 2 then 'PICKED_UP'
            when 3 then 'IN_TRANSIT'
            else 'SIGNED'
        end;

        insert into logistics_order (
            order_no, customer_id, route_id, warehouse_id, vehicle_id, driver_id,
            customer_name, sender_address, receiver_address, cargo_name,
            cargo_weight, cargo_volume, status, planned_pickup_time, planned_delivery_time,
            created_at, updated_at
        )
        select order_no_value,
               customer_id_value,
               route_id_value,
               warehouse_id_value,
               vehicle_id_value,
               driver_id_value,
               concat('Mock Customer ', lpad(@ref_index, 3, '0')),
               concat(mock_city, ' Sender Site ', i),
               concat('Destination Site ', i),
               concat('Mock Cargo ', lpad(i, 3, '0')),
               10 + i * 8.75,
               1 + mod(i, 20) * 0.65,
               mock_status,
               timestampadd(hour, mod(i, 24), current_timestamp),
               timestampadd(hour, mod(i, 24) + 12 + mod(i, 48), current_timestamp),
               current_timestamp,
               current_timestamp
        where not exists (
            select 1 from logistics_order
            where order_no = order_no_value
        );

        insert into logistics_order_tracking (
            order_no, tracking_status, location, description, operator_name, occurred_at, created_at
        )
        select order_no_value,
               mock_status,
               mock_city,
               concat('Mock tracking update for ', order_no_value),
               concat('Operator ', lpad(i, 3, '0')),
               timestampadd(minute, i * -10, current_timestamp),
               current_timestamp
        where not exists (
            select 1 from logistics_order_tracking
            where order_no = order_no_value
              and tracking_status = mock_status
        );

        insert into logistics_inventory (
            warehouse_id, sku_code, sku_name, quantity, locked_quantity, updated_at
        )
        select warehouse_id_value,
               concat('MOCK-SKU-', lpad(i, 4, '0')),
               concat('Mock SKU ', lpad(i, 4, '0')),
               100 + i * 3,
               mod(i, 30),
               current_timestamp
        where not exists (
            select 1 from logistics_inventory
            where warehouse_id = warehouse_id_value
              and sku_code = concat('MOCK-SKU-', lpad(i, 4, '0'))
        );

        insert into logistics_freight_bill (
            bill_no, order_no, base_amount, fuel_surcharge, discount_amount,
            payable_amount, pay_status, created_at, updated_at
        )
        select concat('MOCK-BILL-', lpad(i, 4, '0')),
               order_no_value,
               120 + i * 9.5,
               10 + mod(i, 20) * 2.25,
               mod(i, 7) * 5,
               120 + i * 9.5 + 10 + mod(i, 20) * 2.25 - mod(i, 7) * 5,
               if(mod(i, 3) = 0, 'PAID', 'UNPAID'),
               current_timestamp,
               current_timestamp
        where not exists (
            select 1 from logistics_freight_bill
            where bill_no = concat('MOCK-BILL-', lpad(i, 4, '0'))
        );

        set i = i + 1;
    end while;
end//

delimiter ;

call seed_logistics_mock_100();

drop procedure if exists seed_logistics_mock_100;
