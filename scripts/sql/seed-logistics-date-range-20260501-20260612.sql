-- ============================================================================
-- 物流模拟数据生成脚本：2026-05-01 ~ 2026-06-12（43天）
--
-- 功能：
--   1. 新增 250 个客户（CUST-0201 ~ CUST-0450），8城市均匀分布
--   2. 按日期生成 30-50 单/天，含完整子记录（运单/调度/任务/轨迹/费用/运费账单/订单跟踪）
--   3. 状态按订单年龄推断（老订单大概率已签收，新订单待调度）
--   4. 支持重复执行（幂等防重）
--
-- 执行方式：
--   mysql -uroot logistics_management < scripts/sql/seed-logistics-date-range-20260501-20260612.sql
-- ============================================================================

USE logistics_management;

DROP PROCEDURE IF EXISTS seed_orders_date_range;

DELIMITER $$

CREATE PROCEDURE seed_orders_date_range(
    IN start_date_str VARCHAR(10),
    IN end_date_str VARCHAR(10)
)
BEGIN
    DECLARE v_date DATE;
    DECLARE v_end_date DATE;
    DECLARE v_orders_today INT;
    DECLARE v_order_idx INT;
    DECLARE v_total_orders INT DEFAULT 0;
    DECLARE v_total_customers INT DEFAULT 0;
    DECLARE v_days_ago INT;
    DECLARE v_day_of_week INT;
    DECLARE v_is_holiday INT DEFAULT 0;

    -- 订单变量
    DECLARE v_order_id BIGINT;
    DECLARE v_order_no VARCHAR(64);
    DECLARE v_cust_id BIGINT;
    DECLARE v_cust_name VARCHAR(128);
    DECLARE v_cust_city VARCHAR(64);
    DECLARE v_cust_contact VARCHAR(64);
    DECLARE v_cust_phone VARCHAR(128);
    DECLARE v_cust_province VARCHAR(64);
    DECLARE v_cust_address VARCHAR(255);
    DECLARE v_receiver_city VARCHAR(64);
    DECLARE v_receiver_address VARCHAR(255);
    DECLARE v_cargo_name VARCHAR(128);
    DECLARE v_cargo_weight DECIMAL(12,3);
    DECLARE v_route_id BIGINT;
    DECLARE v_wh_id BIGINT;
    DECLARE v_drv_id BIGINT;
    DECLARE v_veh_id BIGINT;
    DECLARE v_order_status VARCHAR(32);
    DECLARE v_created_at DATETIME;
    DECLARE v_planned_pickup DATETIME;
    DECLARE v_planned_delivery DATETIME;
    DECLARE v_payment_status VARCHAR(32);

    -- 子记录变量
    DECLARE v_waybill_id BIGINT;
    DECLARE v_waybill_no VARCHAR(64);
    DECLARE v_dispatch_id BIGINT;
    DECLARE v_task_id BIGINT;
    DECLARE v_task_no VARCHAR(64);
    DECLARE v_fee_id BIGINT;
    DECLARE v_bill_id BIGINT;
    DECLARE v_bill_no VARCHAR(64);
    DECLARE v_track_id BIGINT;
    DECLARE v_exception_id BIGINT;

    -- 状态列表与进度变量
    DECLARE v_transport_status VARCHAR(32);
    DECLARE v_dispatch_status VARCHAR(32);
    DECLARE v_task_status VARCHAR(32);
    DECLARE v_current_status VARCHAR(32);
    DECLARE v_operator_name VARCHAR(64);
    DECLARE v_operation_desc VARCHAR(255);
    DECLARE v_operation_time DATETIME;
    DECLARE v_status_idx INT;
    DECLARE v_track_count INT;
    DECLARE v_exception_type VARCHAR(64);
    DECLARE v_exception_desc VARCHAR(255);

    -- 临时变量
    DECLARE v_date_str VARCHAR(6);
    DECLARE v_sec_of_day INT;
    DECLARE v_hh INT;
    DECLARE v_mm INT;
    DECLARE v_ss INT;
    DECLARE v_rand FLOAT;
    DECLARE v_pick_idx INT;
    DECLARE v_base_fee DECIMAL(12,2);
    DECLARE v_weight_fee DECIMAL(12,2);
    DECLARE v_distance_fee DECIMAL(12,2);
    DECLARE v_is_same_city INT;
    DECLARE v_cust_cnt INT;
    DECLARE v_drv_cnt INT;
    DECLARE v_veh_cnt INT;
    DECLARE v_wh_cnt INT;
    DECLARE v_rt_cnt INT;
    DECLARE v_receiver_pool_idx INT;
    DECLARE v_receiver_city_tmp VARCHAR(64);
    DECLARE v_cargo_type_idx INT;

    -- 异常处理（输出真实错误信息便于排查）
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1 @errno = MYSQL_ERRNO, @msg = MESSAGE_TEXT;
        ROLLBACK;
        SELECT CONCAT('ERROR ', @errno, ': ', @msg) AS error_detail;
    END;

    START TRANSACTION;

    -- =========================================================================
    -- PART A: 新增客户（250个，幂等防重）
    -- =========================================================================
    SELECT '=== Part A: Inserting new customers ===' AS progress;

    -- 客户城市映射：8个城市
    -- 1=上海, 2=北京, 3=广州, 4=深圳, 5=杭州, 6=南京, 7=成都, 8=武汉

    SET @cust_idx = 201;
    SET @cust_seq = 0;
    WHILE @cust_idx <= 450 DO
        SET @city_idx = (@cust_idx - 1) % 8 + 1;
        SET @cust_city_name = ELT(@city_idx, '上海', '北京', '广州', '深圳', '杭州', '南京', '成都', '武汉');
        SET @cust_province_name = ELT(@city_idx, '上海市', '北京市', '广东省', '广东省', '浙江省', '江苏省', '四川省', '湖北省');

        -- 公司名称：行业模板 + 城市 + 后缀
        SET @industry_idx = (@cust_idx - 1) % 30 + 1;
        SET @company_template = ELT(@industry_idx,
            '远航供应链', '鹏程电子科技', '锦城商贸', '云帆物流', '丰禾食品',
            '恒达冷链', '瑞丰建材', '星辰医药', '嘉运日化', '星河机械',
            '通达快递', '优品电商', '明辉汽配', '鑫源家具', '联创服饰',
            '博远图书', '天诚仪器', '正邦化工', '龙腾农业', '万通包装',
            '海纳文化', '卓越高新', '安信金融', '禾丰粮油', '锦绣纺织',
            '环宇新能源', '中联环保', '鼎新科技', '华盛酒业', '创新文体');
        SET @cust_company_name = CONCAT(@cust_city_name, @company_template, '有限公司');

        -- 联系人：30个中文姓名池
        SET @contact_idx = (@cust_idx - 1) % 30 + 1;
        SET @cust_contact_name = ELT(@contact_idx,
            '陈志强', '李雪琴', '王浩然', '张雨桐', '刘思远',
            '赵雅婷', '周启航', '孙俊杰', '黄丽华', '吴建平',
            '郑凯文', '钱晓燕', '沈明辉', '冯桂英', '蒋海龙',
            '韩雪梅', '杨建国', '朱晓峰', '秦玉兰', '许文博',
            '何美玲', '吕志伟', '施佳琪', '魏东升', '严秀芳',
            '华建民', '金丽娟', '陶国栋', '姜春燕', '戚永强');

        -- 手机号：1{3,5,7,8}xxxxxxxx
        SET @cust_phone = CONCAT('1', ELT(1 + FLOOR(RAND() * 4), '3', '5', '7', '8'),
            LPAD(FLOOR(RAND() * 100000000), 8, '0'));

        -- 地址：按城市生成
        SET @district_idx = (@cust_idx - 1) % 30 + 1;
        SET @district_name = ELT(@district_idx % 6 + 1, '浦东新区', '朝阳区', '天河区', '南山区', '西湖区', '鼓楼区');
        SET @street_idx = (@cust_idx - 1) % 30 + 1;
        SET @street_name = ELT(@street_idx % 10 + 1,
            '张江路', '光华路', '科韵路', '科技园路', '文三路',
            '中山北路', '天府大道', '光谷大道', '南京东路', '长安街');
        SET @street_num = 100 + FLOOR(RAND() * 900);
        SET @cust_address = CONCAT(@cust_city_name, '市', @district_name, @street_name, @street_num, '号');

        -- 生成唯一 ID：基于序列号，避免 RAND() 碰撞
        SET @cust_seq = @cust_seq + 1;
        SET @new_cust_id = CAST(CONCAT(
            DATE_FORMAT(NOW(), '%y%m%d%H%i'),  -- 当前分钟
            LPAD(@cust_seq, 5, '0')             -- 5位序列号
        ) AS UNSIGNED);

        -- 插入客户（幂等防重）
        SET @new_code = CONCAT('CUST-', LPAD(@cust_idx, 4, '0'));
        IF NOT EXISTS (SELECT 1 FROM logistics_customer WHERE customer_code = @new_code) THEN
            INSERT INTO logistics_customer (
                id, customer_code, customer_name, contact_name, contact_phone,
                province, city, address, status, created_at, updated_at
            ) VALUES (
                @new_cust_id,
                @new_code,
                @cust_company_name,
                @cust_contact_name,
                @cust_phone,
                @cust_province_name,
                @cust_city_name,
                @cust_address,
                'ACTIVE',
                NOW(),
                NOW()
            );
        END IF;

        SET @cust_idx = @cust_idx + 1;
    END WHILE;

    SELECT COUNT(*) INTO v_total_customers FROM logistics_customer WHERE deleted = 0;
    SELECT CONCAT('Customers ready: ', v_total_customers) AS progress;

    -- =========================================================================
    -- PART B: 加载引用数据到临时表
    -- =========================================================================
    SELECT '=== Part B: Loading reference data ===' AS progress;

    DROP TEMPORARY TABLE IF EXISTS tmp_cust;
    CREATE TEMPORARY TABLE tmp_cust (
        rn INT AUTO_INCREMENT PRIMARY KEY,
        id BIGINT, customer_code VARCHAR(32), customer_name VARCHAR(128),
        city VARCHAR(64), contact_name VARCHAR(64), contact_phone VARCHAR(128),
        province VARCHAR(64), address VARCHAR(255)
    );
    INSERT INTO tmp_cust (id, customer_code, customer_name, city, contact_name, contact_phone, province, address)
    SELECT id, customer_code, customer_name, city, contact_name, contact_phone, province, address
    FROM logistics_customer WHERE status = 'ACTIVE' AND deleted = 0
    ORDER BY id;

    DROP TEMPORARY TABLE IF EXISTS tmp_drv;
    CREATE TEMPORARY TABLE tmp_drv (
        rn INT AUTO_INCREMENT PRIMARY KEY,
        id BIGINT, driver_code VARCHAR(32), driver_name VARCHAR(64)
    );
    INSERT INTO tmp_drv (id, driver_code, driver_name)
    SELECT id, driver_code, driver_name FROM logistics_driver
    WHERE status IN ('AVAILABLE', 'ON_ROUTE', 'RESTING') AND deleted = 0
    ORDER BY id;

    DROP TEMPORARY TABLE IF EXISTS tmp_veh;
    CREATE TEMPORARY TABLE tmp_veh (
        rn INT AUTO_INCREMENT PRIMARY KEY,
        id BIGINT, vehicle_no VARCHAR(32), vehicle_type VARCHAR(64)
    );
    INSERT INTO tmp_veh (id, vehicle_no, vehicle_type)
    SELECT id, vehicle_no, vehicle_type FROM logistics_vehicle
    WHERE status IN ('AVAILABLE', 'ON_ROUTE') AND deleted = 0
    ORDER BY id;

    DROP TEMPORARY TABLE IF EXISTS tmp_wh;
    CREATE TEMPORARY TABLE tmp_wh (
        rn INT AUTO_INCREMENT PRIMARY KEY,
        id BIGINT, warehouse_code VARCHAR(32), warehouse_name VARCHAR(128), city VARCHAR(64)
    );
    INSERT INTO tmp_wh (id, warehouse_code, warehouse_name, city)
    SELECT id, warehouse_code, warehouse_name, city FROM logistics_warehouse
    WHERE status = 'ACTIVE' AND deleted = 0
    ORDER BY id;

    -- 路线临时表：带 row number 按城市对查找
    DROP TEMPORARY TABLE IF EXISTS tmp_rt;
    CREATE TEMPORARY TABLE tmp_rt (
        rn INT AUTO_INCREMENT PRIMARY KEY,
        id BIGINT, route_code VARCHAR(32), origin_city VARCHAR(64),
        destination_city VARCHAR(64), distance_km DECIMAL(12,2)
    );
    INSERT INTO tmp_rt (id, route_code, origin_city, destination_city, distance_km)
    SELECT id, route_code, origin_city, destination_city, distance_km FROM logistics_route
    WHERE status = 'ACTIVE' AND deleted = 0
    ORDER BY id;

    -- 接收城市池（用于 receiver 地址）
    DROP TEMPORARY TABLE IF EXISTS tmp_cities;
    CREATE TEMPORARY TABLE tmp_cities (
        rn INT AUTO_INCREMENT PRIMARY KEY,
        city_name VARCHAR(64), province_name VARCHAR(64)
    );
    INSERT INTO tmp_cities (city_name, province_name) VALUES
        ('上海', '上海市'), ('北京', '北京市'), ('广州', '广东省'), ('深圳', '广东省'),
        ('杭州', '浙江省'), ('南京', '江苏省'), ('成都', '四川省'), ('武汉', '湖北省');

    -- 货物类型池
    DROP TEMPORARY TABLE IF EXISTS tmp_cargo;
    CREATE TEMPORARY TABLE tmp_cargo (
        rn INT AUTO_INCREMENT PRIMARY KEY,
        cargo_name VARCHAR(64), min_weight INT, max_weight INT
    );
    INSERT INTO tmp_cargo (cargo_name, min_weight, max_weight) VALUES
        ('汽车配件', 80, 500), ('生鲜冷链箱', 50, 300), ('电子元件', 10, 100),
        ('办公耗材', 20, 150), ('服装样品', 5, 80), ('医药恒温箱', 30, 200),
        ('建材五金托盘', 200, 800), ('电商小件包裹', 1, 30), ('母婴用品箱', 10, 50),
        ('茶叶礼盒', 5, 40), ('农产品周转箱', 50, 400), ('家电配件包裹', 20, 120);

    -- 操作员池
    DROP TEMPORARY TABLE IF EXISTS tmp_operator;
    CREATE TEMPORARY TABLE tmp_operator (
        rn INT AUTO_INCREMENT PRIMARY KEY, operator_name VARCHAR(64)
    );
    INSERT INTO tmp_operator (operator_name) VALUES
        ('陈静怡'), ('李明轩'), ('王雅婷'), ('周启航'), ('赵雨桐'),
        ('刘思远'), ('孙浩然'), ('黄俊杰'), ('吴嘉宁'), ('徐子涵'),
        ('郑凯文'), ('张伟民'), ('林小红'), ('何建国'), ('马丽华');

    -- 异常类型池
    DROP TEMPORARY TABLE IF EXISTS tmp_exception_types;
    CREATE TEMPORARY TABLE tmp_exception_types (
        rn INT AUTO_INCREMENT PRIMARY KEY,
        ex_type VARCHAR(64), ex_desc_template VARCHAR(255), weight INT
    );
    INSERT INTO tmp_exception_types (ex_type, ex_desc_template, weight) VALUES
        ('运输延误', '因天气原因导致运输延误，预计延迟{hours}小时到达', 40),
        ('货物损坏', '卸货时发现外包装破损，{item}部分受损', 15),
        ('地址错误', '收货地址信息有误，无法正常派送', 15),
        ('签收异常', '收件人无法联系，多次派送失败', 15),
        ('车辆故障', '运输途中车辆出现{part}故障，需紧急维修', 10),
        ('费用争议', '运费计算与实际服务存在差异，需核对账单', 5);

    SELECT COUNT(*) INTO v_cust_cnt FROM tmp_cust;
    SELECT COUNT(*) INTO v_drv_cnt FROM tmp_drv;
    SELECT COUNT(*) INTO v_veh_cnt FROM tmp_veh;
    SELECT COUNT(*) INTO v_wh_cnt FROM tmp_wh;
    SELECT COUNT(*) INTO v_rt_cnt FROM tmp_rt;
    SELECT CONCAT('Refs: customers=', v_cust_cnt, ' drivers=', v_drv_cnt,
        ' vehicles=', v_veh_cnt, ' warehouses=', v_wh_cnt, ' routes=', v_rt_cnt) AS progress;

    -- =========================================================================
    -- PART C: 按日期生成订单
    -- =========================================================================
    SELECT '=== Part C: Generating orders ===' AS progress;

    SET @global_seq = 0;
    SET v_date = STR_TO_DATE(start_date_str, '%Y-%m-%d');
    SET v_end_date = STR_TO_DATE(end_date_str, '%Y-%m-%d');

    WHILE v_date <= v_end_date DO
        SET v_day_of_week = DAYOFWEEK(v_date);
        SET v_days_ago = DATEDIFF(CURDATE(), v_date);

        -- 五一假期判断
        SET v_is_holiday = 0;
        IF v_date BETWEEN '2026-05-01' AND '2026-05-03' THEN
            SET v_is_holiday = 1;
        END IF;

        -- 确定当天订单数
        IF v_is_holiday = 1 THEN
            SET v_orders_today = 15 + FLOOR(RAND() * 11);   -- 15-25
        ELSEIF v_day_of_week = 1 THEN
            SET v_orders_today = 15 + FLOOR(RAND() * 11);   -- Sunday: 15-25
        ELSEIF v_day_of_week = 7 THEN
            SET v_orders_today = 20 + FLOOR(RAND() * 16);   -- Saturday: 20-35
        ELSE
            SET v_orders_today = 35 + FLOOR(RAND() * 16);   -- Weekday: 35-50
        END IF;

        -- 五一后第一个工作日恢复量
        IF v_date = '2026-05-04' THEN
            SET v_orders_today = 30 + FLOOR(RAND() * 16);
        END IF;

        SET v_order_idx = 0;

        WHILE v_order_idx < v_orders_today DO
            -- ------- 1. 生成 ID 和时间 -------
            SET v_date_str = DATE_FORMAT(v_date, '%y%m%d');
            -- 营业时间分布：08:00-20:00
            SET v_rand = RAND();
            SET v_sec_of_day = 28800 + CASE
                WHEN v_rand < 0.05 THEN FLOOR(RAND() * 3600)        -- 08:00-09:00
                WHEN v_rand < 0.30 THEN 3600 + FLOOR(RAND() * 7200) -- 09:00-11:00
                WHEN v_rand < 0.45 THEN 10800 + FLOOR(RAND() * 7200) -- 11:00-13:00
                WHEN v_rand < 0.65 THEN 18000 + FLOOR(RAND() * 7200) -- 13:00-15:00
                WHEN v_rand < 0.90 THEN 25200 + FLOOR(RAND() * 7200) -- 15:00-17:00
                ELSE 32400 + FLOOR(RAND() * 10800)                   -- 17:00-20:00
            END;
            SET v_hh = FLOOR(v_sec_of_day / 3600);
            SET v_mm = FLOOR((v_sec_of_day % 3600) / 60);
            SET v_ss = v_sec_of_day % 60;
            SET @global_seq = @global_seq + 1;

            SET v_order_id = CAST(CONCAT(v_date_str,
                LPAD(v_hh, 2, '0'), LPAD(v_mm, 2, '0'),
                LPAD(v_ss, 2, '0'), LPAD(@global_seq % 1000, 3, '0')) AS UNSIGNED);

            SET v_created_at = TIMESTAMP(v_date, MAKETIME(v_hh, v_mm, v_ss));

            -- 订单号
            SET v_order_no = CONCAT('LO', v_date_str, LPAD(v_hh, 2, '0'),
                LPAD(v_mm, 2, '0'), LPAD(@global_seq % 100000, 5, '0'));

            -- ------- 2. 随机选择引用数据 -------
            -- 客户
            SET v_pick_idx = 1 + FLOOR(RAND() * v_cust_cnt);
            SELECT id, customer_name, city, contact_name, contact_phone, province, address
            INTO v_cust_id, v_cust_name, v_cust_city, v_cust_contact, v_cust_phone, v_cust_province, v_cust_address
            FROM tmp_cust WHERE rn = v_pick_idx;

            -- 仓库（优先同城）
            SET v_pick_idx = 1 + FLOOR(RAND() * v_wh_cnt);
            SELECT id INTO v_wh_id FROM tmp_wh
            WHERE city = v_cust_city
            ORDER BY RAND() LIMIT 1;
            IF v_wh_id IS NULL THEN
                SELECT id INTO v_wh_id FROM tmp_wh WHERE rn = v_pick_idx;
            END IF;

            -- 收货城市（70%跨城，30%同城）
            SET v_is_same_city = IF(RAND() < 0.3, 1, 0);
            IF v_is_same_city = 1 THEN
                SET v_receiver_city = v_cust_city;
            ELSE
                SET v_pick_idx = 1 + FLOOR(RAND() * 8);
                SELECT city_name INTO v_receiver_city FROM tmp_cities WHERE rn = v_pick_idx;
                IF v_receiver_city = v_cust_city THEN
                    -- 如果随机到了同城，换一个
                    SET v_pick_idx = IF(v_pick_idx >= 8, 1, v_pick_idx + 1);
                    SELECT city_name INTO v_receiver_city FROM tmp_cities WHERE rn = v_pick_idx;
                END IF;
            END IF;

            -- 收货地址
            SET @recv_district_idx = 1 + FLOOR(RAND() * 6);
            SET @recv_district = ELT(@recv_district_idx, '浦东新区', '朝阳区', '天河区', '南山区', '西湖区', '鼓楼区');
            SET @recv_street_idx = 1 + FLOOR(RAND() * 10);
            SET @recv_street = ELT(@recv_street_idx,
                '张江路', '光华路', '科韵路', '科技园路', '文三路',
                '中山北路', '天府大道', '光谷大道', '南京东路', '长安街');
            SET @recv_street_num = 100 + FLOOR(RAND() * 900);
            SET v_receiver_address = CONCAT(v_receiver_city, '市', @recv_district, @recv_street, @recv_street_num, '号');

            -- 路线：查 origin=客户城市, destination=收货城市
            SELECT id, distance_km INTO v_route_id, @route_distance
            FROM tmp_rt
            WHERE origin_city = v_cust_city AND destination_city = v_receiver_city
            ORDER BY RAND() LIMIT 1;
            IF v_route_id IS NULL THEN
                -- 查反向路线
                SELECT id, distance_km INTO v_route_id, @route_distance
                FROM tmp_rt
                WHERE origin_city = v_receiver_city AND destination_city = v_cust_city
                ORDER BY RAND() LIMIT 1;
            END IF;
            IF v_route_id IS NULL THEN
                -- 找不到路线，取任意一条
                SET v_pick_idx = 1 + FLOOR(RAND() * v_rt_cnt);
                SELECT id, distance_km INTO v_route_id, @route_distance FROM tmp_rt WHERE rn = v_pick_idx;
            END IF;
            IF @route_distance IS NULL THEN SET @route_distance = 500; END IF;

            -- 货物类型
            SET v_cargo_type_idx = 1 + FLOOR(RAND() * 12);
            SELECT cargo_name, FLOOR(min_weight + RAND() * (max_weight - min_weight))
            INTO v_cargo_name, v_cargo_weight
            FROM tmp_cargo WHERE rn = v_cargo_type_idx;

            -- 司机和车辆
            SET v_pick_idx = 1 + FLOOR(RAND() * v_drv_cnt);
            SELECT id INTO v_drv_id FROM tmp_drv WHERE rn = v_pick_idx;
            SET v_pick_idx = 1 + FLOOR(RAND() * v_veh_cnt);
            SELECT id INTO v_veh_id FROM tmp_veh WHERE rn = v_pick_idx;

            -- ------- 3. 确定订单状态（按年龄） -------
            SET v_rand = RAND();
            IF v_days_ago >= 30 THEN
                SET v_order_status = CASE
                    WHEN v_rand < 0.85 THEN 'SIGNED'
                    WHEN v_rand < 0.95 THEN 'DELIVERED'
                    ELSE 'EXCEPTION' END;
            ELSEIF v_days_ago >= 20 THEN
                SET v_order_status = CASE
                    WHEN v_rand < 0.70 THEN 'SIGNED'
                    WHEN v_rand < 0.90 THEN 'DELIVERED'
                    WHEN v_rand < 0.95 THEN 'IN_TRANSIT'
                    ELSE 'EXCEPTION' END;
            ELSEIF v_days_ago >= 10 THEN
                SET v_order_status = CASE
                    WHEN v_rand < 0.40 THEN 'SIGNED'
                    WHEN v_rand < 0.70 THEN 'DELIVERED'
                    WHEN v_rand < 0.90 THEN 'IN_TRANSIT'
                    WHEN v_rand < 0.97 THEN 'PICKED_UP'
                    ELSE 'EXCEPTION' END;
            ELSEIF v_days_ago >= 4 THEN
                SET v_order_status = CASE
                    WHEN v_rand < 0.35 THEN 'DELIVERED'
                    WHEN v_rand < 0.65 THEN 'IN_TRANSIT'
                    WHEN v_rand < 0.85 THEN 'PICKED_UP'
                    WHEN v_rand < 0.95 THEN 'SIGNED'
                    ELSE 'EXCEPTION' END;
            ELSEIF v_days_ago >= 2 THEN
                SET v_order_status = CASE
                    WHEN v_rand < 0.30 THEN 'PICKED_UP'
                    WHEN v_rand < 0.55 THEN 'IN_TRANSIT'
                    WHEN v_rand < 0.80 THEN 'DISPATCHED'
                    WHEN v_rand < 0.95 THEN 'DELIVERED'
                    ELSE 'EXCEPTION' END;
            ELSEIF v_days_ago = 1 THEN
                SET v_order_status = CASE
                    WHEN v_rand < 0.40 THEN 'DISPATCHED'
                    WHEN v_rand < 0.70 THEN 'PICKED_UP'
                    WHEN v_rand < 0.95 THEN 'WAIT_DISPATCH'
                    ELSE 'EXCEPTION' END;
            ELSE
                SET v_order_status = CASE
                    WHEN v_rand < 0.50 THEN 'WAIT_DISPATCH'
                    WHEN v_rand < 0.85 THEN 'DISPATCHED'
                    WHEN v_rand < 0.95 THEN 'PICKED_UP'
                    ELSE 'EXCEPTION' END;
            END IF;

            -- 计划时间
            SET v_planned_pickup = DATE_ADD(v_created_at, INTERVAL (1 + FLOOR(RAND() * 4)) HOUR);
            SET v_planned_delivery = DATE_ADD(v_planned_pickup, INTERVAL (12 + FLOOR(RAND() * 72)) HOUR);

            -- ------- 4. 插入订单 -------
            INSERT INTO logistics_order (
                id, order_no, customer_id, route_id, warehouse_id, vehicle_id, driver_id,
                customer_name, sender_address, receiver_address,
                cargo_name, cargo_weight, cargo_volume, status,
                planned_pickup_time, planned_delivery_time, created_at, updated_at
            )
            SELECT v_order_id, v_order_no, v_cust_id, v_route_id, v_wh_id, v_veh_id, v_drv_id,
                v_cust_name, v_cust_address, v_receiver_address,
                v_cargo_name, v_cargo_weight, ROUND(v_cargo_weight * 0.002, 3), v_order_status,
                v_planned_pickup, v_planned_delivery, v_created_at, v_created_at
            WHERE NOT EXISTS (
                SELECT 1 FROM logistics_order WHERE order_no = v_order_no
            );

            -- ------- 5. 子记录状态映射 -------
            SET v_transport_status = CASE v_order_status
                WHEN 'WAIT_DISPATCH' THEN 'WAIT_DISPATCH'
                WHEN 'DISPATCHED' THEN 'DISPATCHED'
                WHEN 'PICKED_UP' THEN 'PICKED_UP'
                WHEN 'IN_TRANSIT' THEN 'IN_TRANSIT'
                WHEN 'DELIVERED' THEN 'DELIVERED'
                WHEN 'SIGNED' THEN 'SIGNED'
                WHEN 'EXCEPTION' THEN 'EXCEPTION'
                ELSE 'WAIT_DISPATCH' END;

            SET v_dispatch_status = IF(v_order_status IN ('DELIVERED', 'SIGNED'), 'FINISHED', 'ASSIGNED');

            SET v_task_status = IF(v_order_status IN ('DELIVERED', 'SIGNED'), 'SIGNED', 'TRANSPORTING');

            SET v_payment_status = CASE
                WHEN v_order_status IN ('SIGNED') AND RAND() < 0.95 THEN 'PAID'
                WHEN v_order_status IN ('DELIVERED') AND RAND() < 0.80 THEN 'PAID'
                WHEN v_order_status IN ('IN_TRANSIT') AND RAND() < 0.10 THEN 'PAID'
                ELSE 'UNPAID' END;

            -- ------- 6. 生成运单 -------
            SET @global_seq = @global_seq + 1;
            SET v_waybill_id = CAST(CONCAT(v_date_str,
                LPAD(v_hh, 2, '0'), LPAD(v_mm, 2, '0'),
                LPAD(v_ss + 1, 2, '0'), LPAD(@global_seq % 1000, 3, '0')) AS UNSIGNED);
            SET v_waybill_no = CONCAT('WB', v_date_str, LPAD(@global_seq % 100000, 5, '0'));

            INSERT INTO logistics_waybill (
                id, waybill_no, order_id, start_site, target_site,
                current_location, transport_status, create_time, update_time
            )
            SELECT v_waybill_id, v_waybill_no, v_order_id, v_cust_city, v_receiver_city,
                IF(v_order_status IN ('DELIVERED', 'SIGNED'), v_receiver_city, v_cust_city),
                v_transport_status, v_created_at, v_created_at
            WHERE NOT EXISTS (
                SELECT 1 FROM logistics_waybill WHERE waybill_no = v_waybill_no
            );

            -- ------- 7. 生成调度 -------
            SET @global_seq = @global_seq + 1;
            SET v_dispatch_id = CAST(CONCAT(v_date_str,
                LPAD(v_hh, 2, '0'), LPAD(v_mm, 2, '0'),
                LPAD(v_ss + 2, 2, '0'), LPAD(@global_seq % 1000, 3, '0')) AS UNSIGNED);

            INSERT INTO logistics_dispatch (
                id, order_id, waybill_id, driver_id, vehicle_id,
                start_site, target_site,
                planned_departure_time, planned_arrival_time,
                dispatch_status, create_time, update_time
            )
            SELECT v_dispatch_id, v_order_id, v_waybill_id, v_drv_id, v_veh_id,
                v_cust_city, v_receiver_city,
                v_planned_pickup,
                DATE_ADD(v_planned_pickup, INTERVAL FLOOR(@route_distance / 50) HOUR),
                v_dispatch_status, v_created_at, v_created_at
            WHERE NOT EXISTS (
                SELECT 1 FROM logistics_dispatch WHERE order_id = v_order_id AND waybill_id = v_waybill_id
            );

            -- ------- 8. 生成运输任务 -------
            SET @global_seq = @global_seq + 1;
            SET v_task_id = CAST(CONCAT(v_date_str,
                LPAD(v_hh, 2, '0'), LPAD(v_mm, 2, '0'),
                LPAD(v_ss + 3, 2, '0'), LPAD(@global_seq % 1000, 3, '0')) AS UNSIGNED);
            SET v_task_no = CONCAT('TASK', v_date_str, LPAD(@global_seq % 100000, 5, '0'));

            INSERT INTO logistics_task (
                id, task_no, order_id, waybill_id, dispatch_id,
                driver_id, vehicle_id, task_status, create_time, update_time
            )
            SELECT v_task_id, v_task_no, v_order_id, v_waybill_id, v_dispatch_id,
                v_drv_id, v_veh_id, v_task_status, v_created_at, v_created_at
            WHERE NOT EXISTS (
                SELECT 1 FROM logistics_task WHERE task_no = v_task_no
            );

            -- ------- 9. 生成费用 -------
            SET @global_seq = @global_seq + 1;
            SET v_fee_id = CAST(CONCAT(v_date_str,
                LPAD(v_hh, 2, '0'), LPAD(v_mm, 2, '0'),
                LPAD(v_ss + 4, 2, '0'), LPAD(@global_seq % 1000, 3, '0')) AS UNSIGNED);
            SET v_base_fee = 120 + FLOOR(RAND() * 80);                          -- 120-200
            SET v_weight_fee = ROUND(v_cargo_weight * 2.5, 2);                   -- 2.5元/kg
            SET v_distance_fee = ROUND(@route_distance * 0.8, 2);                -- 0.8元/km
            SET @additional_fee = IF(RAND() < 0.2, 50 + FLOOR(RAND() * 150), 0); -- 20%概率附加费
            SET @discount_fee = IF(RAND() < 0.3, FLOOR(RAND() * 50), 0);         -- 30%概率折扣
            SET @payable = v_base_fee + v_weight_fee + v_distance_fee + @additional_fee - @discount_fee;
            SET @actual_fee = IF(v_payment_status = 'PAID', @payable, @payable);

            INSERT INTO logistics_fee (
                id, order_id, base_fee, weight_fee, distance_fee,
                additional_fee, discount_fee, payable_fee, actual_fee,
                payment_status, create_time, update_time
            )
            SELECT v_fee_id, v_order_id, v_base_fee, v_weight_fee, v_distance_fee,
                @additional_fee, @discount_fee, @payable, @actual_fee,
                v_payment_status, v_created_at, v_created_at
            WHERE NOT EXISTS (
                SELECT 1 FROM logistics_fee WHERE order_id = v_order_id
            );

            -- ------- 10. 生成运费账单 -------
            SET @global_seq = @global_seq + 1;
            SET v_bill_id = CAST(CONCAT(v_date_str,
                LPAD(v_hh, 2, '0'), LPAD(v_mm, 2, '0'),
                LPAD(v_ss + 5, 2, '0'), LPAD(@global_seq % 1000, 3, '0')) AS UNSIGNED);
            SET v_bill_no = CONCAT('FB', v_date_str, LPAD(@global_seq % 100000, 5, '0'));

            INSERT INTO logistics_freight_bill (
                id, bill_no, order_no, base_amount, fuel_surcharge,
                discount_amount, payable_amount, pay_status, created_at, updated_at
            )
            SELECT v_bill_id, v_bill_no, v_order_no,
                v_base_fee, ROUND(v_distance_fee * 0.15, 2),  -- fuel surcharge
                @discount_fee, @payable,
                IF(v_payment_status = 'PAID' AND RAND() < 0.9, 'PAID', 'UNPAID'),
                v_created_at, v_created_at
            WHERE NOT EXISTS (
                SELECT 1 FROM logistics_freight_bill WHERE bill_no = v_bill_no
            );

            -- ------- 11. 生成轨迹记录（2-5条，按状态递进） -------
            SET v_track_count = CASE
                WHEN v_order_status IN ('WAIT_DISPATCH') THEN 2
                WHEN v_order_status IN ('DISPATCHED') THEN 3
                WHEN v_order_status IN ('PICKED_UP') THEN 3
                WHEN v_order_status IN ('IN_TRANSIT') THEN 4
                WHEN v_order_status IN ('DELIVERED', 'SIGNED') THEN 5
                WHEN v_order_status IN ('EXCEPTION') THEN 4
                ELSE 3 END;

            SET v_status_idx = 1;
            -- 轨迹状态序列
            SET @track_status1 = 'WAIT_DISPATCH';
            SET @track_desc1 = '订单创建，等待调度分配';
            SET @track_status2 = 'DISPATCHED';
            SET @track_desc2 = CONCAT('已调度司机和车辆，准备从', v_cust_city, '出发');
            SET @track_status3 = 'PICKED_UP';
            SET @track_desc3 = CONCAT('货物已装车，离开仓库，驶往', v_receiver_city);
            SET @track_status4 = 'IN_TRANSIT';
            SET @track_desc4 = CONCAT('运输中，当前位置接近', v_receiver_city);
            SET @track_status5 = 'DELIVERED';
            SET @track_desc5 = CONCAT('货物已送达', v_receiver_address, '，等待签收');
            SET @track_status6 = 'SIGNED';
            SET @track_desc6 = '收件人已签收，订单完成';

            -- 选操作员
            SET @op_idx = 1 + FLOOR(RAND() * 15);
            SELECT operator_name INTO v_operator_name FROM tmp_operator WHERE rn = @op_idx;

            SET v_status_idx = 1;
            WHILE v_status_idx <= v_track_count DO
                SET @global_seq = @global_seq + 1;
                SET v_track_id = CAST(CONCAT(v_date_str,
                    LPAD(v_hh, 2, '0'), LPAD(v_mm, 2, '0'),
                    LPAD(v_ss + 10 + v_status_idx, 2, '0'),
                    LPAD(@global_seq % 1000, 3, '0')) AS UNSIGNED);

                -- 轨迹时间：每条比上一条晚
                SET v_operation_time = DATE_ADD(v_created_at, INTERVAL (
                    CASE v_status_idx
                        WHEN 1 THEN 1 + FLOOR(RAND() * 30)      -- 10-90分钟
                        WHEN 2 THEN 30 + FLOOR(RAND() * 60)      -- 30-120分钟
                        WHEN 3 THEN 120 + FLOOR(RAND() * 240)    -- 2-6小时
                        WHEN 4 THEN 360 + FLOOR(RAND() * 1440)   -- 6-30小时
                        WHEN 5 THEN 1440 + FLOOR(RAND() * 1440)  -- 24-72小时
                        ELSE 60
                    END) MINUTE);

                SET v_current_status = CASE v_status_idx
                    WHEN 1 THEN @track_status1 WHEN 2 THEN @track_status2
                    WHEN 3 THEN @track_status3 WHEN 4 THEN @track_status4
                    WHEN 5 THEN @track_status5 ELSE @track_status6 END;

                SET v_operation_desc = CASE v_status_idx
                    WHEN 1 THEN @track_desc1 WHEN 2 THEN @track_desc2
                    WHEN 3 THEN @track_desc3 WHEN 4 THEN @track_desc4
                    WHEN 5 THEN @track_desc5 ELSE @track_desc6 END;

                INSERT INTO logistics_track (
                    id, order_id, waybill_id, current_status, current_location,
                    operator_name, operation_desc, operation_time
                )
                SELECT v_track_id, v_order_id, v_waybill_id,
                    v_current_status,
                    CASE v_status_idx
                        WHEN 1 THEN v_cust_city
                        WHEN 2 THEN v_cust_city
                        WHEN 3 THEN CONCAT(v_cust_city, '出城高速口')
                        WHEN 4 THEN CONCAT(v_receiver_city, '方向中途站')
                        WHEN 5 THEN v_receiver_city
                        ELSE v_receiver_city END,
                    v_operator_name, v_operation_desc, v_operation_time
                WHERE NOT EXISTS (
                    SELECT 1 FROM logistics_track
                    WHERE order_id = v_order_id
                      AND waybill_id = v_waybill_id
                      AND operation_time = v_operation_time
                );

                SET v_status_idx = v_status_idx + 1;
            END WHILE;

            -- ------- 12. 生成异常记录（~6%概率） -------
            IF v_order_status = 'EXCEPTION' OR (v_order_status != 'EXCEPTION' AND RAND() < 0.03) THEN
                SET @global_seq = @global_seq + 1;
                SET v_exception_id = CAST(CONCAT(v_date_str,
                    LPAD(v_hh, 2, '0'), LPAD(v_mm, 2, '0'),
                    LPAD(v_ss + 20, 2, '0'), LPAD(@global_seq % 1000, 3, '0')) AS UNSIGNED);

                -- 选异常类型
                SET @ex_w = FLOOR(1 + RAND() * 100);
                SET @ex_pick = 1;
                IF @ex_w <= 40 THEN SET @ex_pick = 1;
                ELSEIF @ex_w <= 55 THEN SET @ex_pick = 2;
                ELSEIF @ex_w <= 70 THEN SET @ex_pick = 3;
                ELSEIF @ex_w <= 85 THEN SET @ex_pick = 4;
                ELSEIF @ex_w <= 95 THEN SET @ex_pick = 5;
                ELSE SET @ex_pick = 6;
                END IF;

                SELECT ex_type, REPLACE(REPLACE(REPLACE(ex_desc_template,
                    '{hours}', 2 + FLOOR(RAND() * 24)),
                    '{part}', ELT(1 + FLOOR(RAND() * 4), '发动机', '刹车系统', '轮胎', '变速箱')),
                    '{item}', IFNULL(v_cargo_name, '货物'))
                INTO v_exception_type, v_exception_desc
                FROM tmp_exception_types WHERE rn = @ex_pick;

                INSERT INTO logistics_exception (
                    id, order_id, task_id, exception_type, exception_desc,
                    exception_status, report_user, report_time, handle_user, handle_time
                )
                SELECT v_exception_id, v_order_id, v_task_id,
                    v_exception_type, v_exception_desc,
                    IF(v_days_ago >= 10 AND RAND() < 0.7, 'CLOSED', 'WAIT_HANDLE'),
                    v_operator_name,
                    DATE_ADD(v_created_at, INTERVAL (v_track_count + 1) HOUR),
                    IF(v_days_ago >= 10 AND RAND() < 0.7, '系统管理员', NULL),
                    IF(v_days_ago >= 10 AND RAND() < 0.7,
                        DATE_ADD(v_created_at, INTERVAL (v_track_count + 24) HOUR), NULL)
                WHERE NOT EXISTS (
                    SELECT 1 FROM logistics_exception
                    WHERE order_id = v_order_id AND exception_type = v_exception_type AND report_time = DATE_ADD(v_created_at, INTERVAL (v_track_count + 1) HOUR)
                );
            END IF;

            -- ------- 13. 生成订单跟踪 -------
            SET @global_seq = @global_seq + 1;
            SET @tracking_id = CAST(CONCAT(v_date_str,
                LPAD(v_hh, 2, '0'), LPAD(v_mm, 2, '0'),
                LPAD(v_ss + 21, 2, '0'), LPAD(@global_seq % 1000, 3, '0')) AS UNSIGNED);

            INSERT INTO logistics_order_tracking (
                id, order_no, tracking_status, location, description,
                operator_name, occurred_at, created_at
            )
            SELECT @tracking_id, v_order_no, v_order_status,
                IF(v_order_status IN ('DELIVERED', 'SIGNED'), v_receiver_city, v_cust_city),
                CONCAT('订单当前状态：',
                    CASE v_order_status
                        WHEN 'WAIT_DISPATCH' THEN '等待调度' WHEN 'DISPATCHED' THEN '已调度'
                        WHEN 'PICKED_UP' THEN '已取件' WHEN 'IN_TRANSIT' THEN '运输中'
                        WHEN 'DELIVERED' THEN '已送达' WHEN 'SIGNED' THEN '已签收'
                        ELSE '异常处理中' END),
                v_operator_name,
                v_created_at, v_created_at
            WHERE NOT EXISTS (
                SELECT 1 FROM logistics_order_tracking
                WHERE order_no = v_order_no AND tracking_status = v_order_status AND occurred_at = v_created_at
            );

            SET v_order_idx = v_order_idx + 1;
            SET v_total_orders = v_total_orders + 1;
        END WHILE;

        -- 每日进度
        SELECT CONCAT(DATE_FORMAT(v_date, '%Y-%m-%d'), ' (',
            CASE DAYOFWEEK(v_date)
                WHEN 1 THEN '周日' WHEN 2 THEN '周一' WHEN 3 THEN '周二'
                WHEN 4 THEN '周三' WHEN 5 THEN '周四' WHEN 6 THEN '周五' ELSE '周六' END,
            '): ', v_orders_today, ' orders') AS progress;

        SET v_date = DATE_ADD(v_date, INTERVAL 1 DAY);
    END WHILE;

    -- =========================================================================
    -- PART D: 清理与汇总
    -- =========================================================================
    DROP TEMPORARY TABLE IF EXISTS tmp_cust;
    DROP TEMPORARY TABLE IF EXISTS tmp_drv;
    DROP TEMPORARY TABLE IF EXISTS tmp_veh;
    DROP TEMPORARY TABLE IF EXISTS tmp_wh;
    DROP TEMPORARY TABLE IF EXISTS tmp_rt;
    DROP TEMPORARY TABLE IF EXISTS tmp_cities;
    DROP TEMPORARY TABLE IF EXISTS tmp_cargo;
    DROP TEMPORARY TABLE IF EXISTS tmp_operator;
    DROP TEMPORARY TABLE IF EXISTS tmp_exception_types;

    COMMIT;

    SELECT CONCAT('=== Complete! Total orders generated: ', v_total_orders,
        ', customers: ', v_total_customers, ' ===') AS summary;

END$$

DELIMITER ;

-- ============================================================================
-- 执行存储过程
-- ============================================================================
CALL seed_orders_date_range('2026-05-01', '2026-06-12');

DROP PROCEDURE IF EXISTS seed_orders_date_range;
