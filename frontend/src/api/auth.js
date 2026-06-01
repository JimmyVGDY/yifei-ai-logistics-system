import http from './http'

export function login(payload) {
  return http.post('/auth/login', payload)
}

export function fetchSession() {
  return http.get('/auth/session')
}

export function logout() {
  return http.post('/auth/logout')
}

export function fetchLoginConflictStatus(conflictId) {
  return http.get(`/auth/login-conflicts/${conflictId}/status`)
}

export function fetchCurrentLoginConflict() {
  return http.get('/auth/login-conflicts/current')
}

export function rejectLoginConflict(conflictId) {
  return http.post(`/auth/login-conflicts/${conflictId}/reject`)
}
