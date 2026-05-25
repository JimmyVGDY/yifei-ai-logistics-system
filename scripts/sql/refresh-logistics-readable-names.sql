use logistics_management;

update sys_user
set email = 'admin@xlh-logistics.local',
    real_name = '系统管理员',
    update_time = current_timestamp
where username = 'admin';

update logistics_customer
set customer_name = case customer_code
        when 'CUST-SH-001' then '上海鲜达供应链有限公司'
        when 'CUST-BJ-002' then '北京智选商贸有限公司'
        when 'CUST-GZ-003' then '广州恒越汽配有限公司'
        when 'CUST-SZ-004' then '深圳跨境优选贸易有限公司'
        else customer_name
    end,
    contact_name = case customer_code
        when 'CUST-SH-001' then '陈静怡'
        when 'CUST-BJ-002' then '李明轩'
        when 'CUST-GZ-003' then '王雅婷'
        when 'CUST-SZ-004' then '周启航'
        else contact_name
    end,
    province = case customer_code
        when 'CUST-SH-001' then '上海市'
        when 'CUST-BJ-002' then '北京市'
        when 'CUST-GZ-003' then '广东省'
        when 'CUST-SZ-004' then '广东省'
        else province
    end,
    address = case customer_code
        when 'CUST-SH-001' then '浦东新区张江路 88 号'
        when 'CUST-BJ-002' then '朝阳区光华路 66 号'
        when 'CUST-GZ-003' then '天河区软件路 18 号'
        when 'CUST-SZ-004' then '南山区科技路 9 号'
        else address
    end,
    city = case customer_code
        when 'CUST-SH-001' then '上海'
        when 'CUST-BJ-002' then '北京'
        when 'CUST-GZ-003' then '广州'
        when 'CUST-SZ-004' then '深圳'
        else city
    end,
    updated_at = current_timestamp
where customer_code in ('CUST-SH-001', 'CUST-BJ-002', 'CUST-GZ-003', 'CUST-SZ-004');

update logistics_customer
set customer_name = concat(case city
        when 'Shanghai' then '上海'
        when 'Beijing' then '北京'
        when 'Guangzhou' then '广州'
        when 'Shenzhen' then '深圳'
        when 'Hangzhou' then '杭州'
        when 'Nanjing' then '南京'
        when 'Chengdu' then '成都'
        when 'Wuhan' then '武汉'
        else city
    end, elt(1 + mod(cast(substring(customer_code, 11) as unsigned), 12), '云帆商贸有限公司', '丰禾食品有限公司', '星河冷链供应链有限公司', '安捷汽配有限公司', '万汇家居有限公司', '瑞康医药物流有限公司', '嘉品电商有限公司', '海川制造有限公司', '天悦连锁超市有限公司', '启明电子有限公司', '盛达建材有限公司', '优鲜农产品有限公司')),
    contact_name = elt(1 + mod(cast(substring(customer_code, 11) as unsigned), 20), '陈静怡', '李明轩', '王雅婷', '周启航', '赵雨桐', '刘思远', '孙浩然', '黄俊杰', '吴嘉宁', '徐子涵', '郑凯文', '胡佳琪', '高雨辰', '林书瑶', '郭文博', '何欣怡', '马天宇', '罗梓涵', '谢思源', '唐若琳'),
    province = case city
        when 'Shanghai' then '上海市'
        when 'Beijing' then '北京市'
        when 'Guangzhou' then '广东省'
        when 'Shenzhen' then '广东省'
        when 'Hangzhou' then '浙江省'
        when 'Nanjing' then '江苏省'
        when 'Chengdu' then '四川省'
        when 'Wuhan' then '湖北省'
        when '上海' then '上海市'
        when '北京' then '北京市'
        when '广州' then '广东省'
        when '深圳' then '广东省'
        when '杭州' then '浙江省'
        when '南京' then '江苏省'
        when '成都' then '四川省'
        when '武汉' then '湖北省'
        else province
    end,
    city = case city
        when 'Shanghai' then '上海'
        when 'Beijing' then '北京'
        when 'Guangzhou' then '广州'
        when 'Shenzhen' then '深圳'
        when 'Hangzhou' then '杭州'
        when 'Nanjing' then '南京'
        when 'Chengdu' then '成都'
        when 'Wuhan' then '武汉'
        else city
    end,
    address = concat(case city
        when 'Shanghai' then '上海'
        when 'Beijing' then '北京'
        when 'Guangzhou' then '广州'
        when 'Shenzhen' then '深圳'
        when 'Hangzhou' then '杭州'
        when 'Nanjing' then '南京'
        when 'Chengdu' then '成都'
        when 'Wuhan' then '武汉'
        else city
    end, '市临港物流园', 10 + mod(cast(substring(customer_code, 11) as unsigned), 80), '号'),
    updated_at = current_timestamp
where customer_code like 'MOCK-CUST-%';

update logistics_warehouse
set warehouse_name = case warehouse_code
        when 'WH-SH-01' then '上海东区云仓'
        when 'WH-BJ-01' then '北京北区分拨仓'
        when 'WH-GZ-01' then '广州南区冷链仓'
        else warehouse_name
    end,
    province = case warehouse_code
        when 'WH-SH-01' then '上海市'
        when 'WH-BJ-01' then '北京市'
        when 'WH-GZ-01' then '广东省'
        else province
    end,
    address = case warehouse_code
        when 'WH-SH-01' then '浦东物流园 A1 库区'
        when 'WH-BJ-01' then '大兴物流园 B2 库区'
        when 'WH-GZ-01' then '白云物流中心 C3 库区'
        else address
    end,
    city = case warehouse_code
        when 'WH-SH-01' then '上海'
        when 'WH-BJ-01' then '北京'
        when 'WH-GZ-01' then '广州'
        else city
    end,
    manager_name = case warehouse_code
        when 'WH-SH-01' then '许文博'
        when 'WH-BJ-01' then '赵雨桐'
        when 'WH-GZ-01' then '黄俊杰'
        else manager_name
    end,
    updated_at = current_timestamp
where warehouse_code in ('WH-SH-01', 'WH-BJ-01', 'WH-GZ-01');

update logistics_vehicle
set vehicle_type = case vehicle_no
        when '沪A-LOG01' then '9.6米厢式货车'
        when '京B-LOG02' then '冷链厢式货车'
        when '粤C-LOG03' then '城市配送面包车'
        else vehicle_type
    end,
    current_city = case vehicle_no
        when '沪A-LOG01' then '上海'
        when '京B-LOG02' then '北京'
        when '粤C-LOG03' then '广州'
        else current_city
    end,
    updated_at = current_timestamp
where vehicle_no in ('沪A-LOG01', '京B-LOG02', '粤C-LOG03');

delete from logistics_vehicle
where vehicle_no in ('娌狝-LOG01', '浜珺-LOG02', '绮-LOG03');

update logistics_route
set origin_city = case route_code
        when 'RT-SH-BJ' then '上海'
        when 'RT-SH-GZ' then '上海'
        when 'RT-GZ-SZ' then '广州'
        else origin_city
    end,
    destination_city = case route_code
        when 'RT-SH-BJ' then '北京'
        when 'RT-SH-GZ' then '广州'
        when 'RT-GZ-SZ' then '深圳'
        else destination_city
    end,
    updated_at = current_timestamp
where route_code in ('RT-SH-BJ', 'RT-SH-GZ', 'RT-GZ-SZ');

update logistics_warehouse
set warehouse_name = concat(case city
        when 'Shanghai' then '上海'
        when 'Beijing' then '北京'
        when 'Guangzhou' then '广州'
        when 'Shenzhen' then '深圳'
        when 'Hangzhou' then '杭州'
        when 'Nanjing' then '南京'
        when 'Chengdu' then '成都'
        when 'Wuhan' then '武汉'
        else city
    end, elt(1 + mod(cast(substring(warehouse_code, 9) as unsigned), 8), '东区云仓', '北区分拨中心', '南区冷链仓', '西区转运场', '综合保税仓', '快运集散中心', '城市配送仓', '智能立体仓')),
    province = case city
        when 'Shanghai' then '上海市'
        when 'Beijing' then '北京市'
        when 'Guangzhou' then '广东省'
        when 'Shenzhen' then '广东省'
        when 'Hangzhou' then '浙江省'
        when 'Nanjing' then '江苏省'
        when 'Chengdu' then '四川省'
        when 'Wuhan' then '湖北省'
        when '上海' then '上海市'
        when '北京' then '北京市'
        when '广州' then '广东省'
        when '深圳' then '广东省'
        when '杭州' then '浙江省'
        when '南京' then '江苏省'
        when '成都' then '四川省'
        when '武汉' then '湖北省'
        else province
    end,
    city = case city
        when 'Shanghai' then '上海'
        when 'Beijing' then '北京'
        when 'Guangzhou' then '广州'
        when 'Shenzhen' then '深圳'
        when 'Hangzhou' then '杭州'
        when 'Nanjing' then '南京'
        when 'Chengdu' then '成都'
        when 'Wuhan' then '武汉'
        else city
    end,
    address = concat(case city
        when 'Shanghai' then '上海'
        when 'Beijing' then '北京'
        when 'Guangzhou' then '广州'
        when 'Shenzhen' then '深圳'
        when 'Hangzhou' then '杭州'
        when 'Nanjing' then '南京'
        when 'Chengdu' then '成都'
        when 'Wuhan' then '武汉'
        else city
    end, '市综合物流园', 1 + mod(cast(substring(warehouse_code, 9) as unsigned), 9), '号库'),
    manager_name = elt(1 + mod(cast(substring(warehouse_code, 9) as unsigned) + 3, 20), '陈静怡', '李明轩', '王雅婷', '周启航', '赵雨桐', '刘思远', '孙浩然', '黄俊杰', '吴嘉宁', '徐子涵', '郑凯文', '胡佳琪', '高雨辰', '林书瑶', '郭文博', '何欣怡', '马天宇', '罗梓涵', '谢思源', '唐若琳'),
    updated_at = current_timestamp
where warehouse_code like 'MOCK-WH-%';

update logistics_vehicle
set current_city = case current_city
    when 'Shanghai' then '上海'
    when 'Beijing' then '北京'
    when 'Guangzhou' then '广州'
    when 'Shenzhen' then '深圳'
    when 'Hangzhou' then '杭州'
    when 'Nanjing' then '南京'
    when 'Chengdu' then '成都'
    when 'Wuhan' then '武汉'
    else current_city
end,
vehicle_type = case vehicle_type
    when 'Cold Chain Truck' then '冷链厢式货车'
    when '9.6m Box Truck' then '9.6米厢式货车'
    when 'Van' then '城市配送面包车'
    when 'Heavy Truck' then '重型半挂货车'
    else vehicle_type
end
where vehicle_no like 'MOCK-VEH-%';

update logistics_route
set origin_city = case origin_city
        when 'Shanghai' then '上海'
        when 'Beijing' then '北京'
        when 'Guangzhou' then '广州'
        when 'Shenzhen' then '深圳'
        when 'Hangzhou' then '杭州'
        when 'Nanjing' then '南京'
        when 'Chengdu' then '成都'
        when 'Wuhan' then '武汉'
        else origin_city
    end,
    destination_city = case destination_city
        when 'Shanghai' then '上海'
        when 'Beijing' then '北京'
        when 'Guangzhou' then '广州'
        when 'Shenzhen' then '深圳'
        when 'Hangzhou' then '杭州'
        when 'Nanjing' then '南京'
        when 'Chengdu' then '成都'
        when 'Wuhan' then '武汉'
        else destination_city
    end,
    updated_at = current_timestamp
where route_code like 'MOCK-RT-%';

update logistics_driver
set driver_name = case driver_code
        when 'DRV-001' then '张志强'
        when 'DRV-002' then '刘建国'
        when 'DRV-003' then '孙浩然'
        else driver_name
    end,
    updated_at = current_timestamp
where driver_code in ('DRV-001', 'DRV-002', 'DRV-003');

update logistics_driver
set driver_name = elt(1 + mod(cast(substring(driver_code, 10) as unsigned) + 6, 20), '张志强', '刘建国', '孙浩然', '周启航', '赵鹏飞', '陈远航', '吴嘉宁', '黄俊杰', '徐子涵', '高雨辰', '林志远', '马天宇', '罗梓豪', '谢思源', '唐文杰', '韩宇航', '曹明哲', '潘俊峰', '邓博文', '梁嘉诚'),
    updated_at = current_timestamp
where driver_code like 'MOCK-DRV-%';

update logistics_order o
join logistics_customer c on c.id = o.customer_id
set o.customer_name = c.customer_name,
    o.updated_at = current_timestamp;

update logistics_order
set sender_address = case order_no
        when 'LO-DEMO-0001' then '上海市浦东新区发货仓'
        when 'LO-DEMO-0002' then '广州市白云区发货仓'
        when 'LO-DEMO-0003' then '上海东区云仓 A1 库区'
        else sender_address
    end,
    receiver_address = case order_no
        when 'LO-DEMO-0001' then '北京市朝阳区收货点'
        when 'LO-DEMO-0002' then '深圳市南山区收货点'
        when 'LO-DEMO-0003' then '广州市天河区客户现场'
        else receiver_address
    end,
    cargo_name = case order_no
        when 'LO-DEMO-0001' then '服装样品箱'
        when 'LO-DEMO-0002' then '汽车配件托盘'
        when 'LO-DEMO-0003' then '生鲜零售周转箱'
        else cargo_name
    end,
    updated_at = current_timestamp
where order_no in ('LO-DEMO-0001', 'LO-DEMO-0002', 'LO-DEMO-0003');

update logistics_order
set cargo_name = elt(1 + mod(cast(substring(order_no, 12) as unsigned), 12), '生鲜冷链箱', '服装样品箱', '汽车配件托盘', '家电配件包裹', '医药恒温箱', '电商小件包裹', '办公设备箱', '建材五金托盘', '母婴用品箱', '电子元器件箱', '茶叶礼盒', '农产品周转箱')
where order_no like 'MOCK-ORDER-%';

update logistics_order
set sender_address = concat(case mod(cast(substring(order_no, 12) as unsigned), 8)
        when 0 then '上海'
        when 1 then '北京'
        when 2 then '广州'
        when 3 then '深圳'
        when 4 then '杭州'
        when 5 then '南京'
        when 6 then '成都'
        else '武汉'
    end, '市发货仓', 1 + mod(cast(substring(order_no, 12) as unsigned), 12), '号'),
    receiver_address = concat(case mod(cast(substring(order_no, 12) as unsigned) + 3, 8)
        when 0 then '上海'
        when 1 then '北京'
        when 2 then '广州'
        when 3 then '深圳'
        when 4 then '杭州'
        when 5 then '南京'
        when 6 then '成都'
        else '武汉'
    end, '市客户收货点', 1 + mod(cast(substring(order_no, 12) as unsigned), 16), '号'),
    updated_at = current_timestamp
where order_no like 'MOCK-ORDER-%';

update logistics_order_tracking
set location = case location
        when 'Shanghai East Warehouse' then '上海东区云仓'
        when 'Guangzhou South Warehouse' then '广州南区冷链仓'
        when 'Guangshen Expressway' then '广深高速'
        when 'Guangzhou Customer Site' then '广州客户现场'
        else location
    end,
    description = case tracking_status
        when 'CREATED' then '订单已创建，等待调度揽收'
        when 'PICKED_UP' then '司机已完成货物揽收'
        when 'IN_TRANSIT' then '车辆正在前往目的网点'
        when 'DELIVERED' then '客户已签收，订单完成配送'
        else description
    end,
    operator_name = case operator_name
        when 'System' then '系统'
        when 'Driver Sun' then '孙浩然'
        when 'Driver Liu' then '刘建国'
        when 'Dispatch Center' then '调度中心'
        else operator_name
    end
where order_no like 'LO-DEMO-%';

update logistics_order_tracking
set location = replace(replace(replace(replace(replace(replace(replace(replace(location, 'Shanghai', '上海'), 'Beijing', '北京'), 'Guangzhou', '广州'), 'Shenzhen', '深圳'), 'Hangzhou', '杭州'), 'Nanjing', '南京'), 'Chengdu', '成都'), 'Wuhan', '武汉'),
    description = concat('订单状态更新为 ', tracking_status, '，当前位置：', location),
    operator_name = elt(1 + mod(cast(substring(order_no, 12) as unsigned) + 9, 20), '陈静怡', '李明轩', '王雅婷', '周启航', '赵雨桐', '刘思远', '孙浩然', '黄俊杰', '吴嘉宁', '徐子涵', '郑凯文', '胡佳琪', '高雨辰', '林书瑶', '郭文博', '何欣怡', '马天宇', '罗梓涵', '谢思源', '唐若琳')
where order_no like 'MOCK-ORDER-%';

update logistics_inventory
set sku_name = elt(1 + mod(cast(substring(sku_code, 10) as unsigned), 12), '生鲜冷链周转箱', '服装样品纸箱', '汽车配件托盘', '家电配件包裹', '医药恒温包装箱', '电商小件包裹', '办公设备箱', '建材五金托盘', '母婴用品箱', '电子元器件箱', '茶叶礼盒', '农产品周转箱')
where sku_code like 'MOCK-SKU-%';

update logistics_inventory
set sku_name = case sku_code
        when 'SKU-APPAREL-001' then '服装样品纸箱'
        when 'SKU-FRESH-002' then '生鲜零售周转箱'
        when 'SKU-AUTO-003' then '汽车配件托盘'
        else sku_name
    end
where sku_code in ('SKU-APPAREL-001', 'SKU-FRESH-002', 'SKU-AUTO-003');

update logistics_exception
set report_user = case report_user when 'Driver Sun' then '孙浩然' else report_user end,
    handle_user = case handle_user when 'Dispatcher Li' then '李文涛' else handle_user end
where report_user in ('Driver Sun') or handle_user in ('Dispatcher Li');
