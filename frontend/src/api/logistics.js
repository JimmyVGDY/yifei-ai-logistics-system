import http from './http'

// 物流业务 API 的薄封装：页面只传业务参数，鉴权、错误处理和 baseURL 由 http.js 统一处理。
export function fetchOrders(limit = 20) {
  return http.get('/logistics/orders', { params: { limit } })
}

export function fetchDashboardSummary() {
  return http.get('/logistics/dashboard')
}

export function fetchModuleRecords(module, params = {}) {
  // 通用管理页按 module 复用同一分页接口，后端负责模块白名单和列权限过滤。
  return http.get(`/logistics/modules/${module}`, { params })
}

export function createModuleRecord(module, payload) {
  return http.post(`/logistics/modules/${module}`, payload)
}

export function createCustomerAccount(payload) {
  return http.post('/logistics/customer-accounts', payload)
}

export function updateModuleRecord(module, id, payload) {
  return http.post(`/logistics/modules/${module}/${id}`, payload)
}

export function deleteModuleRecord(module, id) {
  return http.post(`/logistics/modules/${module}/${id}/delete`)
}

export function reportException(payload) {
  return http.post('/logistics/exceptions/report', payload)
}

export function handleException(exceptionId, payload = { exceptionStatus: 'CLOSED' }) {
  return http.post(`/logistics/exceptions/${exceptionId}/handle`, payload)
}

export function generateFee(orderNo) {
  return http.post(`/logistics/fees/generate/${orderNo}`)
}

export function markFeePaid(feeId) {
  return http.post(`/logistics/fees/${feeId}/pay`)
}

export function exportModuleExcel(module, limit = 100) {
  // 导出接口返回 blob，http 拦截器会跳过 ApiResponse 拆包。
  return http.get(`/logistics/excel/export/${module}`, {
    params: { limit },
    responseType: 'blob'
  })
}

export function importCustomers(file) {
  // 客户导入必须使用 multipart/form-data，字段名与后端控制器保持一致。
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/logistics/excel/import/customers', formData)
}

export function uploadBusinessFile(file) {
  // 普通业务附件上传只负责传文件，文件类型和大小校验由后端兜底。
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/logistics/files/upload', formData)
}

export function fetchOrderTrend(days = 7) {
  return http.get('/logistics/statistics/order-trend', { params: { days } })
}

export function fetchIncomeTrend(months = 6) {
  return http.get('/logistics/statistics/income-trend', { params: { months } })
}

export function fetchOrder(orderNo) {
  return http.get(`/logistics/orders/${orderNo}`)
}

export function searchOrders(params = {}) {
  // 搜索接口参数较多，保持对象透传，避免前端拼接复杂查询字符串。
  return http.get('/logistics/orders/search', { params })
}

export function createOrder(payload) {
  return http.post('/logistics/orders', payload)
}

export function fetchInfrastructureStatus() {
  return http.get('/infra/status')
}
