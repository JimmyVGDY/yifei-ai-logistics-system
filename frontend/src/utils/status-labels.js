const statusMap = {
  ACTIVE: '启用',
  ENABLED: '启用',
  DISABLED: '停用',
  INACTIVE: '停用',
  AVAILABLE: '空闲',
  IDLE: '空闲',
  RESTING: '休息中',
  ON_ROUTE: '运输中',
  MAINTENANCE: '维修中',
  PAUSED: '暂停',
  CREATED: '已创建',
  WAIT_DISPATCH: '待调度',
  DISPATCHED: '已调度',
  ASSIGNED: '已分配',
  PROCESSING: '处理中',
  PICKED_UP: '已揽收',
  IN_TRANSIT: '运输中',
  TRANSPORTING: '运输中',
  ARRIVED: '已到达',
  DELIVERING: '派送中',
  DELIVERED: '已送达',
  SIGNED: '已签收',
  COMPLETED: '已完成',
  FINISHED: '已完成',
  CANCELLED: '已取消',
  CANCELED: '已取消',
  EXCEPTION: '异常',
  WAIT_HANDLE: '待处理',
  HANDLING: '处理中',
  CLOSED: '已关闭',
  PAID: '已付款',
  UNPAID: '未付款',
  PART_PAID: '部分付款',
  REFUNDED: '已退款',
  SUCCESS: '成功',
  FAILED: '失败',
  BLOCKED: '已限流',
  QUERY_FALLBACK: '查询降级',
  1: '启用',
  0: '停用'
}

export function statusLabel(value) {
  if (value === null || value === undefined || value === '') {
    return ''
  }
  return statusMap[String(value)] || `未知状态(${value})`
}

export function formatDateTime(value) {
  if (!value) {
    return ''
  }
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(value)) {
    return value
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const pad = (number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
