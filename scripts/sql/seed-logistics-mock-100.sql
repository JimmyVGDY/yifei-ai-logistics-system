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
            when 0 then '上海'
            when 1 then '北京'
            when 2 then '广州'
            when 3 then '深圳'
            when 4 then '杭州'
            when 5 then '南京'
            when 6 then '成都'
            else '武汉'
        end;

        insert into logistics_customer (
            customer_code, customer_name, contact_name, contact_phone,
            province, city, address, status, created_at, updated_at
        )
        select concat('MOCK-CUST-', lpad(i, 3, '0')),
               concat(mock_city, elt(1 + mod(i, 12), '云帆商贸有限公司', '丰禾食品有限公司', '星河冷链供应链有限公司', '安捷汽配有限公司', '万汇家居有限公司', '瑞康医药物流有限公司', '嘉品电商有限公司', '海川制造有限公司', '天悦连锁超市有限公司', '启明电子有限公司', '盛达建材有限公司', '优鲜农产品有限公司')),
               elt(1 + mod(i, 20), '陈静怡', '李明轩', '王雅婷', '周启航', '赵雨桐', '刘思远', '孙浩然', '黄俊杰', '吴嘉宁', '徐子涵', '郑凯文', '胡佳琪', '高雨辰', '林书瑶', '郭文博', '何欣怡', '马天宇', '罗梓涵', '谢思源', '唐若琳'),
               concat('1389', lpad(i, 7, '0')),
               case mock_city
                   when '上海' then '上海市'
                   when '北京' then '北京市'
                   when '广州' then '广东省'
                   when '深圳' then '广东省'
                   when '杭州' then '浙江省'
                   when '南京' then '江苏省'
                   when '成都' then '四川省'
                   else '湖北省'
               end,
               mock_city,
               concat(mock_city, '市临港物流园', 10 + mod(i, 80), '号'),
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
               concat(mock_city, elt(1 + mod(i, 8), '东区云仓', '北区分拨中心', '南区冷链仓', '西区转运场', '综合保税仓', '快运集散中心', '城市配送仓', '智能立体仓')),
               case mock_city
                   when '上海' then '上海市'
                   when '北京' then '北京市'
                   when '广州' then '广东省'
                   when '深圳' then '广东省'
                   when '杭州' then '浙江省'
                   when '南京' then '江苏省'
                   when '成都' then '四川省'
                   else '湖北省'
               end,
               mock_city,
               concat(mock_city, '市综合物流园', 1 + mod(i, 9), '号库'),
               elt(1 + mod(i + 3, 20), '陈静怡', '李明轩', '王雅婷', '周启航', '赵雨桐', '刘思远', '孙浩然', '黄俊杰', '吴嘉宁', '徐子涵', '郑凯文', '胡佳琪', '高雨辰', '林书瑶', '郭文博', '何欣怡', '马天宇', '罗梓涵', '谢思源', '唐若琳'),
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
               elt(1 + mod(i + 6, 20), '张志强', '刘建国', '孙浩然', '周启航', '赵鹏飞', '陈远航', '吴嘉宁', '黄俊杰', '徐子涵', '高雨辰', '林志远', '马天宇', '罗梓豪', '谢思源', '唐文杰', '韩宇航', '曹明哲', '潘俊峰', '邓博文', '梁嘉诚'),
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
                    when 0 then '冷链厢式货车'
                    when 1 then '9.6米厢式货车'
                    when 2 then '城市配送面包车'
                    else '重型半挂货车'
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
                    when 0 then '上海'
                    when 1 then '北京'
                    when 2 then '广州'
                    when 3 then '深圳'
                    when 4 then '杭州'
                    when 5 then '南京'
                    when 6 then '成都'
                    else '武汉'
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
            when 1 then 'WAIT_DISPATCH'
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
                (select customer_name from logistics_customer where id = customer_id_value),
                concat(mock_city, '市发货仓', 1 + mod(i, 12), '号'),
                concat(case mod(i + 3, 8)
                    when 0 then '上海'
                    when 1 then '北京'
                    when 2 then '广州'
                    when 3 then '深圳'
                    when 4 then '杭州'
                    when 5 then '南京'
                    when 6 then '成都'
                    else '武汉'
                end, '市客户收货点', 1 + mod(i, 16), '号'),
                elt(1 + mod(i, 12), '生鲜冷链箱', '服装样品箱', '汽车配件托盘', '家电配件包裹', '医药恒温箱', '电商小件包裹', '办公设备箱', '建材五金托盘', '母婴用品箱', '电子元器件箱', '茶叶礼盒', '农产品周转箱'),
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
                concat('订单状态更新为 ', mock_status, '，当前位置：', mock_city),
                elt(1 + mod(i + 9, 20), '陈静怡', '李明轩', '王雅婷', '周启航', '赵雨桐', '刘思远', '孙浩然', '黄俊杰', '吴嘉宁', '徐子涵', '郑凯文', '胡佳琪', '高雨辰', '林书瑶', '郭文博', '何欣怡', '马天宇', '罗梓涵', '谢思源', '唐若琳'),
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
               elt(1 + mod(i, 12), '生鲜冷链周转箱', '服装样品纸箱', '汽车配件托盘', '家电配件包裹', '医药恒温包装箱', '电商小件包裹', '办公设备箱', '建材五金托盘', '母婴用品箱', '电子元器件箱', '茶叶礼盒', '农产品周转箱'),
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
