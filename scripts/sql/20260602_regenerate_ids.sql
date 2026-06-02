-- ============================================================
-- ID 统一刷库脚本：将旧的短ID全部替换为15位雪花格式ID
-- 执行前请备份数据库！
-- 依赖顺序：叶子表 → 被引用主表(级联更新引用列) → 校验
-- ============================================================
-- 运行方式：
--   /mnt/f/Development/Database/MySQL/Server-8.4.9/bin/mysql.exe -h 127.0.0.1 -u root logistics_management < this_file.sql
-- ============================================================

-- ============================================================
-- 第1组：无外部引用的叶子表
-- ============================================================

-- logistics_driver (103条)
CREATE TEMPORARY TABLE tmp_idmap AS
SELECT id AS old_id,
  CONCAT(DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), LPAD((@s := @s + 1), 3, '0')) AS new_id
FROM (SELECT @s := 0) init, logistics_driver WHERE id < 100000000000000;
UPDATE logistics_driver t INNER JOIN tmp_idmap m ON t.id = m.old_id SET t.id = m.new_id;
DROP TEMPORARY TABLE tmp_idmap;

-- logistics_vehicle (103条)
CREATE TEMPORARY TABLE tmp_idmap AS
SELECT id AS old_id,
  CONCAT(DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), LPAD((@s := @s + 1), 3, '0')) AS new_id
FROM (SELECT @s := 0) init, logistics_vehicle WHERE id < 100000000000000;
UPDATE logistics_vehicle t INNER JOIN tmp_idmap m ON t.id = m.old_id SET t.id = m.new_id;
DROP TEMPORARY TABLE tmp_idmap;

-- logistics_warehouse (103条)
CREATE TEMPORARY TABLE tmp_idmap AS
SELECT id AS old_id,
  CONCAT(DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), LPAD((@s := @s + 1), 3, '0')) AS new_id
FROM (SELECT @s := 0) init, logistics_warehouse WHERE id < 100000000000000;
UPDATE logistics_warehouse t INNER JOIN tmp_idmap m ON t.id = m.old_id SET t.id = m.new_id;
DROP TEMPORARY TABLE tmp_idmap;

-- logistics_route (103条)
CREATE TEMPORARY TABLE tmp_idmap AS
SELECT id AS old_id,
  CONCAT(DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), LPAD((@s := @s + 1), 3, '0')) AS new_id
FROM (SELECT @s := 0) init, logistics_route WHERE id < 100000000000000;
UPDATE logistics_route t INNER JOIN tmp_idmap m ON t.id = m.old_id SET t.id = m.new_id;
DROP TEMPORARY TABLE tmp_idmap;

-- logistics_inventory (103条)
CREATE TEMPORARY TABLE tmp_idmap AS
SELECT id AS old_id,
  CONCAT(DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), LPAD((@s := @s + 1), 3, '0')) AS new_id
FROM (SELECT @s := 0) init, logistics_inventory WHERE id < 100000000000000;
UPDATE logistics_inventory t INNER JOIN tmp_idmap m ON t.id = m.old_id SET t.id = m.new_id;
DROP TEMPORARY TABLE tmp_idmap;

-- logistics_freight_bill (103条)
CREATE TEMPORARY TABLE tmp_idmap AS
SELECT id AS old_id,
  CONCAT(DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), LPAD((@s := @s + 1), 3, '0')) AS new_id
FROM (SELECT @s := 0) init, logistics_freight_bill WHERE id < 100000000000000;
UPDATE logistics_freight_bill t INNER JOIN tmp_idmap m ON t.id = m.old_id SET t.id = m.new_id;
DROP TEMPORARY TABLE tmp_idmap;

-- logistics_order_tracking (124条)
CREATE TEMPORARY TABLE tmp_idmap AS
SELECT id AS old_id,
  CONCAT(DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), LPAD((@s := @s + 1), 3, '0')) AS new_id
FROM (SELECT @s := 0) init, logistics_order_tracking WHERE id < 100000000000000;
UPDATE logistics_order_tracking t INNER JOIN tmp_idmap m ON t.id = m.old_id SET t.id = m.new_id;
DROP TEMPORARY TABLE tmp_idmap;

-- demo_user (1条)
CREATE TEMPORARY TABLE tmp_idmap AS
SELECT id AS old_id,
  CONCAT(DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), '000') AS new_id
FROM demo_user WHERE id < 100000000000000;
UPDATE demo_user t INNER JOIN tmp_idmap m ON t.id = m.old_id SET t.id = m.new_id;
DROP TEMPORARY TABLE tmp_idmap;

-- sys_uploaded_file (1条)
CREATE TEMPORARY TABLE tmp_idmap AS
SELECT id AS old_id,
  CONCAT(DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), '000') AS new_id
FROM sys_uploaded_file WHERE id < 100000000000000;
UPDATE sys_uploaded_file t INNER JOIN tmp_idmap m ON t.id = m.old_id SET t.id = m.new_id;
DROP TEMPORARY TABLE tmp_idmap;

-- ============================================================
-- 第2组：sys_menu（有自引用 parent_id，被 sys_role_menu 引用）
-- ============================================================
CREATE TEMPORARY TABLE tmp_map_menu AS
SELECT id AS old_id,
  CONCAT(DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), LPAD((@s := @s + 1), 3, '0')) AS new_id
FROM (SELECT @s := 0) init, sys_menu WHERE id < 100000000000000;

-- ① 更新 menu 自身 id
UPDATE sys_menu t INNER JOIN tmp_map_menu m ON t.id = m.old_id SET t.id = m.new_id;
-- ② 修复 parent_id 自引用
UPDATE sys_menu t
  INNER JOIN tmp_map_menu m ON t.parent_id = m.old_id
SET t.parent_id = m.new_id
WHERE t.parent_id > 0;
-- ③ 更新 sys_role_menu.menu_id
UPDATE sys_role_menu t
  INNER JOIN tmp_map_menu m ON t.menu_id = m.old_id
SET t.menu_id = m.new_id;
-- ④ 更新 sys_permission.menu_id
UPDATE sys_permission t
  INNER JOIN tmp_map_menu m ON t.menu_id = m.old_id
SET t.menu_id = m.new_id;

DROP TEMPORARY TABLE tmp_map_menu;

-- ============================================================
-- 第3组：sys_role（被 sys_role_menu/sys_role_permission/sys_user_role/sys_user.role_id 引用）
-- ============================================================
CREATE TEMPORARY TABLE tmp_map_role AS
SELECT id AS old_id,
  CONCAT(DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), LPAD((@s := @s + 1), 3, '0')) AS new_id
FROM (SELECT @s := 0) init, sys_role WHERE id < 100000000000000;

-- ① 更新 role 自身 id
UPDATE sys_role t INNER JOIN tmp_map_role m ON t.id = m.old_id SET t.id = m.new_id;
-- ② 更新 sys_role_menu.role_id
UPDATE sys_role_menu t INNER JOIN tmp_map_role m ON t.role_id = m.old_id SET t.role_id = m.new_id;
-- ③ 更新 sys_role_permission.role_id
UPDATE sys_role_permission t INNER JOIN tmp_map_role m ON t.role_id = m.old_id SET t.role_id = m.new_id;
-- ④ 更新 sys_user_role.role_id
UPDATE sys_user_role t INNER JOIN tmp_map_role m ON t.role_id = m.old_id SET t.role_id = m.new_id;
-- ⑤ 更新 sys_user.role_id（role_id 引用 sys_role）
UPDATE sys_user t INNER JOIN tmp_map_role m ON t.role_id = m.old_id SET t.role_id = m.new_id;

-- 保留 tmp_map_role，后续更新 sys_user 时还需要用到（见第6组）
-- 注意：tmp_map_role 不能在这里 DROP

-- ============================================================
-- 第4组：logistics_customer（被 logistics_order.customer_id / sys_user.customer_id 引用）
-- ============================================================
CREATE TEMPORARY TABLE tmp_map_customer AS
SELECT id AS old_id,
  CONCAT(DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), LPAD((@s := @s + 1), 3, '0')) AS new_id
FROM (SELECT @s := 0) init, logistics_customer WHERE id < 100000000000000;

-- ① 更新 customer 自身 id
UPDATE logistics_customer t INNER JOIN tmp_map_customer m ON t.id = m.old_id SET t.id = m.new_id;
-- ② 更新 logistics_order.customer_id
UPDATE logistics_order t
  INNER JOIN tmp_map_customer m ON t.customer_id = m.old_id
SET t.customer_id = m.new_id;
-- ③ 更新 sys_user.customer_id
UPDATE sys_user t
  INNER JOIN tmp_map_customer m ON t.customer_id = m.old_id
SET t.customer_id = m.new_id;

-- 保留 tmp_map_customer，后续可能还有引用

-- ============================================================
-- 第5组：logistics_order（被 logistics_track/fee/exception/dispatch/waybill/task 的 order_id 引用）
-- ============================================================
CREATE TEMPORARY TABLE tmp_map_order AS
SELECT id AS old_id,
  CONCAT(DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), LPAD((@s := @s + 1), 3, '0')) AS new_id
FROM (SELECT @s := 0) init, logistics_order WHERE id < 100000000000000;

-- ① 更新 order 自身 id
UPDATE logistics_order t INNER JOIN tmp_map_order m ON t.id = m.old_id SET t.id = m.new_id;
-- ② 更新所有引用 order.id 的表
UPDATE logistics_track t INNER JOIN tmp_map_order m ON t.order_id = m.old_id SET t.order_id = m.new_id;
UPDATE logistics_fee t INNER JOIN tmp_map_order m ON t.order_id = m.old_id SET t.order_id = m.new_id;
UPDATE logistics_exception t INNER JOIN tmp_map_order m ON t.order_id = m.old_id SET t.order_id = m.new_id;
UPDATE logistics_dispatch t INNER JOIN tmp_map_order m ON t.order_id = m.old_id SET t.order_id = m.new_id;
UPDATE logistics_waybill t INNER JOIN tmp_map_order m ON t.order_id = m.old_id SET t.order_id = m.new_id;
UPDATE logistics_task t INNER JOIN tmp_map_order m ON t.order_id = m.old_id SET t.order_id = m.new_id;

DROP TEMPORARY TABLE tmp_map_order;

-- ============================================================
-- 第6组：sys_user（被 sys_user_role/sys_user_permission/sys_login_history/sys_operation_log 引用）
-- ============================================================
CREATE TEMPORARY TABLE tmp_map_user AS
SELECT id AS old_id,
  CONCAT(DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), LPAD((@s := @s + 1), 3, '0')) AS new_id
FROM (SELECT @s := 0) init, sys_user WHERE id < 100000000000000;

-- ① 更新 user 自身 id
UPDATE sys_user t INNER JOIN tmp_map_user m ON t.id = m.old_id SET t.id = m.new_id;
-- ② 更新所有引用 sys_user.id 的表
UPDATE sys_user_role t INNER JOIN tmp_map_user m ON t.user_id = m.old_id SET t.user_id = m.new_id;
UPDATE sys_user_permission t INNER JOIN tmp_map_user m ON t.user_id = m.old_id SET t.user_id = m.new_id;
UPDATE sys_login_history t INNER JOIN tmp_map_user m ON t.user_id = m.old_id SET t.user_id = m.new_id;
UPDATE sys_operation_log t INNER JOIN tmp_map_user m ON t.user_id = m.old_id SET t.user_id = m.new_id;

DROP TEMPORARY TABLE tmp_map_user;
DROP TEMPORARY TABLE tmp_map_role;
DROP TEMPORARY TABLE tmp_map_customer;

-- ============================================================
-- 第7组：叶子表收尾（这些表自己的 id 是旧的，但也被其他表引用）
-- ============================================================

-- logistics_dispatch (2条，自身的 id 旧，但 order_id 已在第5组更新)
CREATE TEMPORARY TABLE tmp_idmap AS
SELECT id AS old_id,
  CONCAT(DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), LPAD((@s := @s + 1), 3, '0')) AS new_id
FROM (SELECT @s := 0) init, logistics_dispatch WHERE id < 100000000000000;
UPDATE logistics_dispatch t INNER JOIN tmp_idmap m ON t.id = m.old_id SET t.id = m.new_id;
DROP TEMPORARY TABLE tmp_idmap;

-- logistics_waybill (3条)
CREATE TEMPORARY TABLE tmp_idmap AS
SELECT id AS old_id,
  CONCAT(DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), LPAD((@s := @s + 1), 3, '0')) AS new_id
FROM (SELECT @s := 0) init, logistics_waybill WHERE id < 100000000000000;
UPDATE logistics_waybill t INNER JOIN tmp_idmap m ON t.id = m.old_id SET t.id = m.new_id;
DROP TEMPORARY TABLE tmp_idmap;

-- logistics_task (2条)
CREATE TEMPORARY TABLE tmp_idmap AS
SELECT id AS old_id,
  CONCAT(DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), LPAD((@s := @s + 1), 3, '0')) AS new_id
FROM (SELECT @s := 0) init, logistics_task WHERE id < 100000000000000;
UPDATE logistics_task t INNER JOIN tmp_idmap m ON t.id = m.old_id SET t.id = m.new_id;
DROP TEMPORARY TABLE tmp_idmap;

-- logistics_track (2条旧ID，其 order_id 已在第5组更新）
CREATE TEMPORARY TABLE tmp_idmap AS
SELECT id AS old_id,
  CONCAT(DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), LPAD((@s := @s + 1), 3, '0')) AS new_id
FROM (SELECT @s := 0) init, logistics_track WHERE id < 100000000000000;
UPDATE logistics_track t INNER JOIN tmp_idmap m ON t.id = m.old_id SET t.id = m.new_id;
DROP TEMPORARY TABLE tmp_idmap;

-- logistics_fee (2条旧ID）
CREATE TEMPORARY TABLE tmp_idmap AS
SELECT id AS old_id,
  CONCAT(DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), LPAD((@s := @s + 1), 3, '0')) AS new_id
FROM (SELECT @s := 0) init, logistics_fee WHERE id < 100000000000000;
UPDATE logistics_fee t INNER JOIN tmp_idmap m ON t.id = m.old_id SET t.id = m.new_id;
DROP TEMPORARY TABLE tmp_idmap;

-- logistics_exception (3条旧ID）
CREATE TEMPORARY TABLE tmp_idmap AS
SELECT id AS old_id,
  CONCAT(DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), LPAD((@s := @s + 1), 3, '0')) AS new_id
FROM (SELECT @s := 0) init, logistics_exception WHERE id < 100000000000000;
UPDATE logistics_exception t INNER JOIN tmp_idmap m ON t.id = m.old_id SET t.id = m.new_id;
DROP TEMPORARY TABLE tmp_idmap;

-- ============================================================
-- 校验
-- ============================================================
SELECT '=== 校验：检查是否还有旧ID残留（结果应为0） ===' AS info;

SELECT 'sys_user' AS tbl, COUNT(*) AS old_cnt FROM sys_user WHERE id < 100000000000000
UNION ALL SELECT 'sys_role', COUNT(*) FROM sys_role WHERE id < 100000000000000
UNION ALL SELECT 'sys_menu', COUNT(*) FROM sys_menu WHERE id < 100000000000000
UNION ALL SELECT 'logistics_order', COUNT(*) FROM logistics_order WHERE id < 100000000000000
UNION ALL SELECT 'logistics_customer', COUNT(*) FROM logistics_customer WHERE id < 100000000000000
UNION ALL SELECT 'logistics_driver', COUNT(*) FROM logistics_driver WHERE id < 100000000000000
UNION ALL SELECT 'logistics_vehicle', COUNT(*) FROM logistics_vehicle WHERE id < 100000000000000
UNION ALL SELECT 'logistics_warehouse', COUNT(*) FROM logistics_warehouse WHERE id < 100000000000000
UNION ALL SELECT 'logistics_dispatch', COUNT(*) FROM logistics_dispatch WHERE id < 100000000000000
UNION ALL SELECT 'logistics_waybill', COUNT(*) FROM logistics_waybill WHERE id < 100000000000000
UNION ALL SELECT 'logistics_track', COUNT(*) FROM logistics_track WHERE id < 100000000000000
UNION ALL SELECT 'logistics_fee', COUNT(*) FROM logistics_fee WHERE id < 100000000000000
UNION ALL SELECT 'logistics_exception', COUNT(*) FROM logistics_exception WHERE id < 100000000000000;

SELECT '=== 迁移完成 ===' AS result;
