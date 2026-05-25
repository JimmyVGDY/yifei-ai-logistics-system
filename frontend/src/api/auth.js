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
