function columns(definition) {
  return definition.split(',').map((item) => {
    const [prop, label] = item.split(':')
    return { prop, label, minWidth: prop.includes('desc') || prop.includes('address') ? 220 : 130 }
  })
}

function moduleMeta(module, title, description, columnDefinition, editDefinition) {
  return { title, description, columns: columns(columnDefinition), editable: true, editFields: editFields(editDefinition, module) }
}

function editFields(definition, module) {
  return definition.split(',').map((item) => {
    const [prop, label, type, precision] = item.split(':')
    return { prop, label, type: type || 'text', precision: precision ? Number(precision) : undefined, options: fieldOptions(prop, module) }
  })
}

export function options(definition) {
  return definition.split(',').map((item) => {
    const [value, label] = item.split(':')
    return { value, label }
  })
}

export const statusOptions = {
  numeric: options('1:启用,0:停用'),
  common: options('ACTIVE:启用,DISABLED:停用,PAUSED:暂停'),
  order: options('CREATED:已创建,WAIT_DISPATCH:待调度,DISPATCHED:已调度,PICKED_UP:已揽收,IN_TRANSIT:运输中,DELIVERING:派送中,DELIVERED:已送达,SIGNED:已签收,COMPLETED:已完成,CANCELLED:已取消,EXCEPTION:异常'),
  transport: options('WAIT_DISPATCH:待调度,DISPATCHED:已调度,IN_TRANSIT:运输中,ARRIVED:已到达,DELIVERED:已送达,SIGNED:已签收,EXCEPTION:异常'),
  dispatch: options('ASSIGNED:已分配,WAIT_DISPATCH:待调度,DISPATCHED:已调度,PROCESSING:处理中,FINISHED:已完成,CANCELLED:已取消'),
  task: options('ASSIGNED:已分配,PICKED_UP:已揽收,TRANSPORTING:运输中,DELIVERING:派送中,SIGNED:已签收,FINISHED:已完成,EXCEPTION:异常'),
  driver: options('AVAILABLE:空闲,ON_ROUTE:运输中,RESTING:休息中,DISABLED:停用'),
  vehicle: options('AVAILABLE:空闲,ON_ROUTE:运输中,MAINTENANCE:维修中,DISABLED:停用'),
  exception: options('WAIT_HANDLE:待处理,PROCESSING:处理中,CLOSED:已关闭'),
  fee: options('UNPAID:未付款,PART_PAID:部分付款,PAID:已付款,REFUNDED:已退款')
}

export const fieldOptionGroups = {
  licenseType: options('A1:A1,A2:A2,B1:B1,B2:B2,C1:C1,C2:C2'),
  vehicleType: options('冷链厢式货车:冷链厢式货车,重型半挂货车:重型半挂货车,城市配送面包车:城市配送面包车,9.6米厢式货车:9.6米厢式货车'),
  exceptionType: options('地址错误:地址错误,货损:货损,延误:延误,客户拒收:客户拒收,车辆故障:车辆故障,其他:其他'),
  province: options('北京市:北京市,上海市:上海市,广东省:广东省,浙江省:浙江省,江苏省:江苏省,四川省:四川省,湖北省:湖北省'),
  city: options('北京:北京,上海:上海,广州:广州,深圳:深圳,杭州:杭州,南京:南京,成都:成都,武汉:武汉')
}

export const relationFieldSources = {
  users: { role_id: 'roles', customer_id: 'orderCustomers' },
  waybills: { order_id: 'orders' },
  dispatches: { order_id: 'orders', waybill_id: 'waybills', driver_id: 'drivers', vehicle_id: 'vehicles' },
  tasks: { order_id: 'orders', waybill_id: 'waybills', dispatch_id: 'dispatches', driver_id: 'drivers', vehicle_id: 'vehicles' },
  tracks: { order_id: 'orders', waybill_id: 'waybills' },
  exceptions: { order_id: 'orders', task_id: 'tasks' },
  fees: { order_id: 'orders' }
}

export const moduleMetas = {
  orders: moduleMeta('orders', '运单管理', '统一维护订单、调度前状态和业务下单入口', 'order_no:订单号,customer_name:客户名称,sender_address:发货地址,receiver_address:收货地址,cargo_name:货物名称,cargo_weight:重量,status:状态,created_at:创建时间,updated_at:更新时间', 'customerName:客户名称,senderAddress:发货地址,receiverAddress:收货地址,cargoName:货物名称,cargoWeight:重量:number:3'),
  customers: moduleMeta('customers', '客户管理', '维护寄件客户和联系人资料', 'customer_code:客户编号,customer_name:客户名称,contact_name:联系人,contact_phone:联系电话,province:省份,city:城市,address:地址,status:状态,created_at:创建时间,updated_at:更新时间', 'customer_name:客户名称,contact_name:联系人,contact_phone:联系电话,province:省份,city:城市,address:地址,status:状态'),
  waybills: moduleMeta('waybills', '运单中心', '订单创建后生成的运单和运输状态', 'waybill_no:运单号,order_id:订单ID,order_no:订单号,start_site:始发网点,target_site:目的网点,current_location:当前位置,transport_status:运输状态,create_time:创建时间,update_time:更新时间', 'order_id:订单ID,start_site:始发网点,target_site:目的网点,current_location:当前位置,transport_status:运输状态'),
  dispatches: moduleMeta('dispatches', '调度管理', '分配司机、车辆并跟踪调度状态', 'order_id:订单ID,order_no:订单号,waybill_id:运单ID,driver_id:司机ID,driver_name:司机,vehicle_id:车辆ID,vehicle_no:车辆,start_site:始发网点,target_site:目的网点,dispatch_status:调度状态,create_time:创建时间,update_time:更新时间', 'order_id:订单ID,waybill_id:运单ID,driver_id:司机ID,vehicle_id:车辆ID,start_site:始发网点,target_site:目的网点,dispatch_status:调度状态'),
  tasks: moduleMeta('tasks', '运输任务', '司机接单、运输、签收和异常上报', 'task_no:任务号,order_id:订单ID,order_no:订单号,driver_id:司机ID,driver_name:司机,vehicle_id:车辆ID,vehicle_no:车辆,task_status:任务状态,proof_url:签收凭证,create_time:创建时间,update_time:更新时间', 'order_id:订单ID,waybill_id:运单ID,dispatch_id:调度ID,driver_id:司机ID,vehicle_id:车辆ID,task_status:任务状态,proof_url:签收凭证'),
  tracks: moduleMeta('tracks', '物流轨迹', '按时间线记录订单运输轨迹', 'order_id:订单ID,order_no:订单号,waybill_id:运单ID,current_status:当前状态,current_location:当前位置,operator_name:操作人,operation_desc:操作说明,operation_time:操作时间', 'order_id:订单ID,waybill_id:运单ID,current_status:当前状态,current_location:当前位置,operator_name:操作人,operation_desc:操作说明,operation_time:操作时间:datetime'),
  drivers: moduleMeta('drivers', '司机管理', '维护司机证件和可用状态', 'driver_code:司机编号,driver_name:司机姓名,phone:手机号,license_no:驾驶证号,license_type:准驾车型,status:状态,created_at:创建时间,updated_at:更新时间', 'driver_name:司机姓名,phone:手机号,license_no:驾驶证号,license_type:准驾车型,status:状态'),
  vehicles: moduleMeta('vehicles', '车辆管理', '维护车辆、容量和当前位置', 'vehicle_no:车牌号,vehicle_type:车辆类型,load_capacity_kg:载重,volume_capacity_cubic:容积,current_city:当前城市,status:状态,created_at:创建时间,updated_at:更新时间', 'vehicle_no:车牌号,vehicle_type:车辆类型,load_capacity_kg:载重:number:2,volume_capacity_cubic:容积:number:2,current_city:当前城市,status:状态'),
  exceptions: moduleMeta('exceptions', '异常管理', '运输异常上报、处理和查询', 'order_id:订单ID,order_no:订单号,task_id:任务ID,exception_type:异常类型,exception_desc:异常描述,exception_status:异常状态,report_user:上报人,report_time:上报时间,handle_user:处理人,handle_time:处理时间', 'order_id:订单ID,task_id:任务ID,exception_type:异常类型,exception_desc:异常描述,exception_status:异常状态'),
  fees: moduleMeta('fees', '费用结算', '订单费用计算、账单和付款状态', 'order_id:订单ID,order_no:订单号,base_fee:基础运费,weight_fee:重量费用,distance_fee:距离费用,additional_fee:附加费,discount_fee:优惠金额,payable_fee:应收金额,actual_fee:实收金额,payment_status:付款状态,create_time:创建时间,update_time:更新时间', 'order_id:订单ID,base_fee:基础运费:number:2,weight_fee:重量费用:number:2,distance_fee:距离费用:number:2,additional_fee:附加费:number:2,discount_fee:优惠金额:number:2,payable_fee:应收金额:number:2,actual_fee:实收金额:number:2,payment_status:付款状态'),
  users: moduleMeta('users', '用户管理', '后台用户、状态和角色分配', 'user_code:用户编号,username:登录账号,real_name:姓名,mobile:手机号,email:邮箱,role_id:角色ID,role_name:角色,customer_id:关联客户ID,customer_name:关联客户,customer_subject_type:客户主体类型,customer_account_type:客户账号类型,status:状态,create_time:创建时间,update_time:更新时间', 'username:登录账号,real_name:姓名,mobile:手机号,email:邮箱,password:密码,role_id:角色ID,status:状态'),
  roles: moduleMeta('roles', '角色管理', '系统管理员、客服、调度、司机、财务和客户角色', 'role_code:角色编码,role_name:角色名称,status:状态,create_time:创建时间,update_time:更新时间', 'role_name:角色名称,status:状态'),
  operationLogs: { title: '操作日志', description: '记录关键接口和业务写操作', editable: false, columns: columns('operation_id:操作ID,trace_id:Trace ID,login_session_id:会话ID,user_code:用户编号,user_id:用户主键,username:操作人,role_code:角色编号,operation_source:操作来源,executor_type:执行者,operation:操作内容,ai_conversation_id:AI会话ID,ai_tool_name:AI工具,ai_tool_target:AI目标,ai_memory_id:AI记忆ID,ai_memory_event_type:记忆事件,ai_memory_source:记忆来源,ai_memory_hit_count:记忆命中数,target_id:对象ID,client_ip:客户端IP,request_uri:请求地址,request_method:方法,operation_status:状态,cost_ms:耗时ms,request_params:参数摘要,change_summary:变更摘要,ai_prompt_summary:AI问题摘要,ai_result_summary:AI结果摘要,ai_memory_trace_summary:记忆链路摘要,error_message:异常信息,operation_time:操作时间') },
  files: { title: '上传文件', description: '查看上传到本地的业务附件记录', editable: false, columns: columns('original_name:原文件名,relative_path:保存路径,file_size:大小,content_type:类型,upload_user:上传人,upload_time:上传时间') }
}

export const operationLogTableColumns = columns('operation_time:操作时间,operation_status:状态,operation_source:操作来源,executor_type:执行者,operation:操作内容,ai_tool_name:AI工具,ai_tool_target:AI目标,ai_memory_event_type:记忆事件,ai_memory_hit_count:记忆命中数,username:操作人,user_code:用户编号,request_method:方法,request_uri:请求地址,cost_ms:耗时ms,trace_id:Trace ID,login_session_id:会话ID')

function fieldOptions(prop, module) {
  if (prop === 'license_type') {
    return fieldOptionGroups.licenseType
  }
  if (prop === 'vehicle_type') {
    return fieldOptionGroups.vehicleType
  }
  if (prop === 'exception_type') {
    return fieldOptionGroups.exceptionType
  }
  if (prop === 'province') {
    return fieldOptionGroups.province
  }
  if (prop === 'city' || prop === 'current_city') {
    return fieldOptionGroups.city
  }
  if (prop === 'status') {
    if (['orders'].includes(module)) {
      return statusOptions.order
    }
    if (module === 'drivers') {
      return statusOptions.driver
    }
    if (module === 'vehicles') {
      return statusOptions.vehicle
    }
    if (['users', 'roles'].includes(module)) {
      return statusOptions.numeric
    }
    return statusOptions.common
  }
  if (prop === 'transport_status' || prop === 'current_status') {
    return statusOptions.transport
  }
  if (prop === 'dispatch_status') {
    return statusOptions.dispatch
  }
  if (prop === 'task_status') {
    return statusOptions.task
  }
  if (prop === 'exception_status') {
    return statusOptions.exception
  }
  if (prop === 'payment_status') {
    return statusOptions.fee
  }
  return undefined
}
