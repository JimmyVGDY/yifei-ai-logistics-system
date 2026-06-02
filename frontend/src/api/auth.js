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

export function acceptLoginConflict(conflictId) {
  return http.post(`/auth/login-conflicts/${conflictId}/accept`)
}

export function fetchCaptcha() {
  return http.get('/auth/captcha')
}

export function updateProfile(data) {
  return http.put('/auth/profile', data)
}

export function changePassword(data) {
  return http.put('/auth/password', data)
}
