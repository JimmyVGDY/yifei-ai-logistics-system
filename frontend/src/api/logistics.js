import http from './http'

export function fetchOrders(limit = 20) {
  return http.get('/logistics/orders', { params: { limit } })
}

export function fetchDashboardSummary() {
  return http.get('/logistics/dashboard')
}

export function fetchModuleRecords(module, params = {}) {
  return http.get(`/logistics/modules/${module}`, { params })
}

export function createModuleRecord(module, payload) {
  return http.post(`/logistics/modules/${module}`, payload)
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
  return http.get(`/logistics/excel/export/${module}`, {
    params: { limit },
    responseType: 'blob'
  })
}

export function importCustomers(file) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/logistics/excel/import/customers', formData)
}

export function uploadBusinessFile(file) {
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
  return http.get('/logistics/orders/search', { params })
}

export function createOrder(payload) {
  return http.post('/logistics/orders', payload)
}

export function fetchInfrastructureStatus() {
  return http.get('/infra/status')
}

export function fetchStructuredLogs(params = {}) {
  return http.get('/system/structured-logs', { params })
}
