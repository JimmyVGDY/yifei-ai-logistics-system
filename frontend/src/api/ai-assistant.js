import http from './http'
import { getAuthToken } from '../stores/auth-store'

export function chatWithAi(payload) {
  return http.post('/ai/chat', payload)
}

/**
 * SSE 流式对话 —— 使用 fetch + ReadableStream 替代 EventSource，
 * 以便在请求头中携带 Sa-Token 认证信息。
 * <p>
 * 返回一个对象 { abort, promise }：
 * - abort(): 取消当前对话
 * - promise: Promise<{conversationId, answer, citations, toolCalls}>
 * <p>
 * onEvent 回调在收到每个 SSE 事件时被调用，用于更新 UI 进度：
 * - { type: 'thinking', message: '...' }
 * - { type: 'tool_start', toolName, target, toolCallCount, maxToolCalls, elapsedMs }
 * - { type: 'tool_result', toolName, target, result, toolCallCount, maxToolCalls, elapsedMs }
 * - { type: 'done', answer, elapsedMs, citationCount, toolCallCount }
 * - { type: 'error', message, elapsedMs }
 */
export function chatWithAiStream({ message, conversationId, pageContext, onEvent }) {
  const controller = new AbortController()
  const { tokenName, tokenValue } = getAuthToken()
  const baseURL = import.meta.env.VITE_API_BASE || '/api'
  const url = `${baseURL}/ai/chat/stream`

  const promise = (async () => {
    const headers = {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json'
    }
    if (tokenName && tokenValue) {
      headers[tokenName] = tokenValue
    }

    const response = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify({ message, conversationId, pageContext }),
      signal: controller.signal
    })

    if (!response.ok) {
      const text = await response.text().catch(() => '')
      throw new Error(text || `HTTP ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let result = null
    let streamError = null

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      // 最后一行可能不完整，保留在 buffer 中
      buffer = lines.pop() || ''

      let eventName = ''
      let eventData = ''

      for (const line of lines) {
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          eventData = line.slice(5).trim()
        } else if (line === '' && eventName && eventData) {
          // 空行表示事件结束
          try {
            const data = JSON.parse(eventData)
            if (eventName === 'done') {
              result = {
                conversationId: conversationId || '',
                answer: data.answer || '',
                citations: [],
                toolCalls: [],
                elapsedMs: data.elapsedMs || 0
              }
            } else if (eventName === 'error') {
              streamError = data.message || 'AI 流式响应失败，请稍后重试'
            }
            if (onEvent) {
              onEvent({ type: eventName, ...data })
            }
          } catch (e) {
            // JSON 解析失败，跳过
          }
          eventName = ''
          eventData = ''
        }
      }
    }

    if (streamError) {
      throw new Error(streamError)
    }
    if (!result) {
      throw new Error('SSE 连接意外关闭，未收到最终结果')
    }
    return result
  })()

  return {
    abort: () => controller.abort(),
    promise
  }
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
