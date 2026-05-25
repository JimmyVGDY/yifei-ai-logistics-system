const statusMap = {
  ACTIVE: '启用',
  DISABLED: '停用',
  AVAILABLE: '空闲',
  ON_ROUTE: '运输中',
  WAIT_DISPATCH: '待调度',
  CREATED: '已创建',
  DISPATCHED: '已调度',
  ASSIGNED: '已分配',
  IN_TRANSIT: '运输中',
  TRANSPORTING: '运输中',
  DELIVERED: '已送达',
  SIGNED: '已签收',
  EXCEPTION: '异常',
  WAIT_HANDLE: '待处理',
  CLOSED: '已关闭',
  PAID: '已付款',
  UNPAID: '未付款',
  BLOCKED: '已限流',
  QUERY_FALLBACK: '查询降级',
  SUCCESS: '成功',
  FAILED: '失败',
  1: '启用',
  0: '停用'
}

export function statusLabel(value) {
  if (value === null || value === undefined || value === '') {
    return ''
  }
  return statusMap[value] || value
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
