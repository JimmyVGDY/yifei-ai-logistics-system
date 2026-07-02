import { getAuthToken } from '../stores/auth-store.js'

export const FIELD_LABEL_MAP = {
  id: 'ID',
  order_no: '订单号',
  order_id: '订单ID',
  task_id: '任务ID',
  waybill_id: '运单ID',
  dispatch_id: '调度ID',
  driver_id: '司机ID',
  vehicle_id: '车辆ID',
  route_id: '路线ID',
  warehouse_id: '仓库ID',
  customer_id: '客户ID',
  role_id: '角色ID',
  parent_id: '父级ID',
  customer_code: '客户编号',
  customer_name: '客户名称',
  contact_name: '联系人',
  contact_phone: '联系电话',
  province: '省份',
  city: '城市',
  address: '地址',
  sender_address: '发货地址',
  receiver_address: '收货地址',
  cargo_name: '货物名称',
  cargo_weight: '重量',
  cargo_volume: '体积',
  status: '状态',
  planned_pickup_time: '计划揽收时间',
  planned_delivery_time: '计划送达时间',
  warehouse_code: '仓库编码',
  warehouse_name: '仓库名称',
  manager_name: '负责人',
  capacity_cubic: '容量',
  driver_code: '司机编号',
  driver_name: '司机姓名',
  phone: '手机号',
  license_no: '驾驶证号',
  license_type: '准驾车型',
  vehicle_no: '车牌号',
  vehicle_type: '车辆类型',
  load_capacity_kg: '载重',
  volume_capacity_cubic: '容积',
  current_city: '当前城市',
  route_code: '路线编码',
  origin_city: '出发城市',
  destination_city: '目的城市',
  distance_km: '距离(km)',
  estimated_hours: '预计耗时(h)',
  waybill_no: '运单号',
  start_site: '始发网点',
  target_site: '目的网点',
  current_location: '当前位置',
  transport_status: '运输状态',
  planned_departure_time: '计划出发时间',
  planned_arrival_time: '计划到达时间',
  dispatch_status: '调度状态',
  task_no: '任务号',
  task_status: '任务状态',
  proof_url: '签收凭证',
  current_status: '当前状态',
  operator_name: '操作人',
  operation_desc: '操作说明',
  exception_type: '异常类型',
  exception_desc: '异常描述',
  exception_status: '异常状态',
  report_user: '上报人',
  report_time: '上报时间',
  handle_user: '处理人',
  handle_time: '处理时间',
  base_fee: '基础运费',
  weight_fee: '重量费用',
  distance_fee: '距离费用',
  additional_fee: '附加费',
  discount_fee: '优惠金额',
  payable_fee: '应付金额',
  actual_fee: '实付金额',
  payment_status: '付款状态',
  sku_code: 'SKU编码',
  sku_name: 'SKU名称',
  quantity: '数量',
  locked_quantity: '锁定数量',
  bill_no: '账单号',
  base_amount: '基础金额',
  fuel_surcharge: '燃油附加费',
  discount_amount: '优惠金额',
  payable_amount: '应付金额',
  pay_status: '支付状态',
  tracking_status: '跟踪状态',
  location: '位置',
  description: '描述',
  occurred_at: '发生时间',
  user_code: '用户编号',
  username: '登录账号',
  real_name: '姓名',
  mobile: '手机号',
  email: '邮箱',
  role_name: '角色名称',
  role_code: '角色编码',
  customer_subject_type: '客户主体类型',
  customer_account_type: '客户账号类型',
  menu_name: '菜单名称',
  menu_path: '菜单路径',
  permission_code: '权限编码',
  sort_no: '排序号',
  created_at: '创建时间',
  updated_at: '更新时间',
  create_time: '创建时间',
  update_time: '更新时间',
  operation_time: '操作时间',
  time: '时间',
  operation: '操作内容',
  uri: '请求地址',
  method: '方法',
  costMs: '耗时(ms)',
  errorMessage: '异常信息',
  order_count: '订单数量',
  customer_count: '客户数量',
  waybill_count: '运单数量',
  task_count: '任务数量',
  total_count: '数量',
  count: '数量',
  cnt: '数量',
  total_amount: '金额合计',
  avg_amount: '平均金额',
}

const LABEL_TO_FIELD = Object.fromEntries(
  Object.entries(FIELD_LABEL_MAP).map(([field, label]) => [label, field])
)

const INTERNAL_FIELDS = new Set([
  'id', 'deleted', 'version', 'create_by', 'update_by', 'created_by', 'updated_by',
  'trace_id', 'operation_id', 'login_session_id', 'cursor_id', 'password', 'token',
  'secret', 'api_key', 'access_token', 'refresh_token', 'mobile_hash'
])

const SENSITIVE_FIELDS = new Set([
  'phone', 'mobile', 'email', 'contact_phone', 'license_no', 'base_fee', 'weight_fee',
  'distance_fee', 'additional_fee', 'discount_fee', 'payable_fee', 'actual_fee',
  'payable_amount', 'exception_desc', 'proof_url', 'customer_id'
])

const MODULE_NAME_MAP = {
  query_business_module: '业务数据查询',
  global_fuzzy_search: '全局业务搜索',
  joined_business_query: '业务联合查询',
  query_dashboard: '运营看板查询',
  query_log_analysis: '日志排障分析',
  execute_readonly_sql: '统计分析',
  continue_cursor: '继续分页查询',
  orders: '运单管理',
  waybills: '运单中心',
  customers: '客户管理',
  dispatches: '调度管理',
  tasks: '运输任务',
  tracks: '物流轨迹',
  drivers: '司机管理',
  vehicles: '车辆管理',
  exceptions: '异常管理',
  fees: '费用结算',
  users: '用户管理',
  roles: '角色管理',
  files: '上传文件',
  operationLogs: '操作日志',
  generated_sql: '统计结果',
}

export function sanitizeToolName(value) {
  return MODULE_NAME_MAP[value] || safeDisplayText(value, '业务数据查询')
}

export function sanitizeTarget(value) {
  return MODULE_NAME_MAP[value] || safeDisplayText(value, '查询结果')
}

export function sanitizeSummary(value, fallback = '查询完成，结构化结果已在下方表格展示。') {
  return safeDisplayText(value, fallback)
}

export function sanitizeAssistantContent(value) {
  return safeDisplayText(value, '查询完成，结构化结果已在下方表格展示。')
}

export function fieldLabel(fieldName) {
  return resolveColumn(fieldName)?.label || ''
}

export function normalizeDataResult(item) {
  const rawRows = Array.isArray(item?.rows) ? item.rows : []
  const rawColumns = Array.isArray(item?.columns) && item.columns.length
    ? item.columns
    : Object.keys(rawRows[0] || {})
  const columnDefs = rawColumns
    .map(resolveColumn)
    .filter(Boolean)
  const columns = columnDefs.map(col => col.label)
  const rows = rawRows.map(row => {
    const displayRow = {}
    for (const col of columnDefs) {
      if (Object.prototype.hasOwnProperty.call(row, col.source)) {
        displayRow[col.label] = row[col.source]
      } else if (Object.prototype.hasOwnProperty.call(row, col.label)) {
        displayRow[col.label] = row[col.label]
      }
    }
    return displayRow
  }).filter(row => Object.keys(row).length > 0)

  return {
    groupId: safeDisplayText(item?.groupId || '', ''),
    toolName: sanitizeToolName(item?.displayToolName || item?.toolName || '业务数据查询'),
    target: sanitizeTarget(item?.displayTarget || item?.target || '查询结果'),
    summary: sanitizeSummary(item?.displaySummary || item?.summary || item?.result || ''),
    columns,
    rows,
    cursorId: item?.cursorId || '',
    total: Number(item?.total ?? rows.length),
    returnedCount: Number(item?.returnedCount ?? rows.length),
    remainingCount: Number(item?.remainingCount ?? 0),
    hasMore: item?.hasMore === true,
    nextPageHint: sanitizeSummary(item?.nextPageHint || '', '')
  }
}

export function displayColumns(result) {
  const rawColumns = Array.isArray(result?.columns) && result.columns.length
    ? result.columns
    : Object.keys(result?.rows?.[0] || {})
  return rawColumns
    .map(resolveColumn)
    .filter(Boolean)
    .map(col => ({ prop: col.label, label: col.label }))
}

function resolveColumn(rawColumn) {
  const source = String(rawColumn || '').trim()
  if (!source || isInternalField(source)) return null
  const allowed = allAllowedColumns()
  let field = ''
  let label = ''
  if (FIELD_LABEL_MAP[source]) {
    field = source
    label = FIELD_LABEL_MAP[source]
  } else if (LABEL_TO_FIELD[source]) {
    field = LABEL_TO_FIELD[source]
    label = source
  } else if (isSafeChineseLabel(source)) {
    label = source
  } else {
    return null
  }
  if (field && isInternalField(field)) return null
  if (field && allowed.size > 0 && !allowed.has(field)) return null
  if (field && SENSITIVE_FIELDS.has(field) && !allowed.has(field)) return null
  return { source, field, label }
}

function allAllowedColumns() {
  const perms = getAuthToken().permissions
  if (Array.isArray(perms)) return new Set()
  const all = new Set()
  for (const [module, scope] of Object.entries(perms || {})) {
    if (module === '_standalone') continue
    for (const col of (scope.columns || [])) {
      all.add(col)
    }
  }
  return all
}

function isInternalField(value) {
  const raw = String(value || '').replace(/`/g, '').trim()
  const lower = raw.replace(/([a-z])([A-Z])/g, '$1_$2').toLowerCase()
  return INTERNAL_FIELDS.has(lower) || lower.endsWith('_id') || lower.startsWith('_') || lower.includes('.')
}

function isSafeChineseLabel(value) {
  if (!/^[\u4e00-\u9fa5A-Za-z0-9（）()·/ -]+$/.test(value)) return false
  return !looksUnsafe(value)
}

function safeDisplayText(value, fallback) {
  const text = String(value || '').trim()
  if (!text) return fallback
  if (looksUnsafe(text)) return fallback
  return text
}

function looksUnsafe(text) {
  const lower = String(text || '').toLowerCase()
  if (lower.includes('execute_readonly_sql') || lower.includes('query_business_module')) return true
  if (lower.includes('select ') || lower.includes(' from logistics_') || lower.includes(' from sys_')) return true
  if (lower.includes('generated_sql') || lower.includes('permissionchecked')) return true
  if (/\|\s*-{3,}/.test(text) || /\n\|/.test(text)) return true
  return /\b[a-z]+_[a-z0-9_]+\b/.test(lower)
}
