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

export function fetchAiMemoryProfile() {
  return http.get('/ai/memory/profile')
}

export function fetchAiMemoryItems(params = {}) {
  return http.get('/ai/memory/items', { params })
}

export function updateAiMemorySettings(payload) {
  return http.put('/ai/memory/settings', payload)
}

export function deleteAiMemoryItem(id) {
  return http.delete(`/ai/memory/items/${id}`)
}

export function clearAiMemories() {
  return http.delete('/ai/memory/items')
}
