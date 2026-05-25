import http from './http'

export function fetchOrders(limit = 20) {
  return http.get('/logistics/orders', { params: { limit } })
}

export function fetchDashboardSummary() {
  return http.get('/logistics/dashboard')
}

export function fetchModuleRecords(module, limit = 20) {
  return http.get(`/logistics/modules/${module}`, { params: { limit } })
}

export function fetchOrder(orderNo) {
  return http.get(`/logistics/orders/${orderNo}`)
}

export function createOrder(payload) {
  return http.post('/logistics/orders', payload)
}

export function fetchInfrastructureStatus() {
  return http.get('/infra/status')
}
