import http from './http'

export function chatWithAi(payload) {
  return http.post('/ai/chat', payload)
}

export function analyzeAiLogs(payload) {
  return http.post('/ai/logs/analyze', payload)
}

export function fetchAiConversations() {
  return http.get('/ai/conversations')
}

export function fetchAiConversation(id) {
  return http.get(`/ai/conversations/${id}`)
}
