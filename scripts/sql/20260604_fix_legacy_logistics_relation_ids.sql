-- 修复旧演示数据中残留的自增 ID 关联。
-- 背景：项目主键升级为短位随机 BIGINT 后，部分调度、任务、轨迹记录仍保存 1、2、3 等旧关联 ID，
--       导致列表查询关联不到运单、司机、车辆和运单中心记录。
-- 原则：只修复可以通过业务编号明确匹配的数据；无法确定映射的数据不强行改动。

use logistics_management;

-- 运单中心旧 ID 映射：1 -> WB-LO-DEMO-0001，2 -> WB-LO-DEMO-0002，3 -> WB-LO-DEMO-0003。
update logistics_dispatch d
join logistics_waybill w
  on w.waybill_no = concat('WB-LO-DEMO-', lpad(d.waybill_id, 4, '0'))
set d.waybill_id = w.id
where d.waybill_id between 1 and 9999;

update logistics_task t
join logistics_waybill w
  on w.waybill_no = concat('WB-LO-DEMO-', lpad(t.waybill_id, 4, '0'))
set t.waybill_id = w.id
where t.waybill_id between 1 and 9999;

update logistics_track tr
join logistics_waybill w
  on w.waybill_no = concat('WB-LO-DEMO-', lpad(tr.waybill_id, 4, '0'))
set tr.waybill_id = w.id
where tr.waybill_id between 1 and 9999;

-- 司机旧 ID 映射：1/2/3 对应 DRV-001/002/003，其余旧 MOCK 数据按 old_id - 3 对应 MOCK-DRV-xxx。
update logistics_dispatch d
join logistics_driver dr
  on dr.driver_code = case
      when d.driver_id between 1 and 3 then concat('DRV-', lpad(d.driver_id, 3, '0'))
      when d.driver_id > 3 then concat('MOCK-DRV-', lpad(d.driver_id - 3, 3, '0'))
      else null
  end
set d.driver_id = dr.id
where d.driver_id between 1 and 9999;

update logistics_task t
join logistics_driver dr
  on dr.driver_code = case
      when t.driver_id between 1 and 3 then concat('DRV-', lpad(t.driver_id, 3, '0'))
      when t.driver_id > 3 then concat('MOCK-DRV-', lpad(t.driver_id - 3, 3, '0'))
      else null
  end
set t.driver_id = dr.id
where t.driver_id between 1 and 9999;

-- 车辆旧 ID 映射：1/2/3 对应首批三辆车，其余旧 MOCK 数据按 old_id - 3 对应 MOCK-VEH-xxx。
update logistics_dispatch d
join logistics_vehicle v
  on v.id = case
      when d.vehicle_id between 1 and 3 then 260602222327000 + d.vehicle_id
      else null
  end
set d.vehicle_id = v.id
where d.vehicle_id between 1 and 3;

update logistics_dispatch d
join logistics_vehicle v
  on v.vehicle_no = concat('MOCK-VEH-', lpad(d.vehicle_id - 3, 3, '0'))
set d.vehicle_id = v.id
where d.vehicle_id > 3 and d.vehicle_id between 1 and 9999;

update logistics_task t
join logistics_vehicle v
  on v.id = case
      when t.vehicle_id between 1 and 3 then 260602222327000 + t.vehicle_id
      else null
  end
set t.vehicle_id = v.id
where t.vehicle_id between 1 and 3;

update logistics_task t
join logistics_vehicle v
  on v.vehicle_no = concat('MOCK-VEH-', lpad(t.vehicle_id - 3, 3, '0'))
set t.vehicle_id = v.id
where t.vehicle_id > 3 and t.vehicle_id between 1 and 9999;

-- 任务里的旧调度 ID 优先按订单归属回填，避免 1/2 这类旧 ID 关联不到当前调度主键。
update logistics_task t
join logistics_dispatch d on d.order_id = t.order_id
set t.dispatch_id = d.id
where t.dispatch_id between 1 and 9999;
