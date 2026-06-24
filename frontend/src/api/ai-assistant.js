import http from './http'
import { getAuthToken } from '../stores/auth-store'

let activeStreamConversationId = ''
const transientHistories = new Map()
const MAX_CLIENT_HISTORY = 8
const MAX_HISTORY_CONTENT_LENGTH = 1000

export function chatWithAi(payload) {
  return http.post('/ai/chat', payload)
}

function createClientConversationId() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function resolveStreamConversationId(conversationId) {
  if (conversationId) {
    activeStreamConversationId = conversationId
    return conversationId
  }
  if (!activeStreamConversationId) {
    activeStreamConversationId = createClientConversationId()
  }
  return activeStreamConversationId
}

function normalizeHistoryContent(content) {
  const text = String(content || '').trim()
  if (!text) return ''
  return text.length > MAX_HISTORY_CONTENT_LENGTH ? text.slice(0, MAX_HISTORY_CONTENT_LENGTH) : text
}

function appendTransientHistory(conversationId, role, content) {
  if (!conversationId || !['user', 'assistant'].includes(role)) return
  const safeContent = normalizeHistoryContent(content)
  if (!safeContent) return
  const history = transientHistories.get(conversationId) || []
  const last = history[history.length - 1]
  if (!last || last.role !== role || last.content !== safeContent) {
    history.push({ role, content: safeContent })
  }
  while (history.length > MAX_CLIENT_HISTORY) {
    history.shift()
  }
  transientHistories.set(conversationId, history)
}

function clientHistoryFor(conversationId) {
  const history = transientHistories.get(conversationId) || []
  return history.slice(-MAX_CLIENT_HISTORY).map(item => ({
    role: item.role,
    content: item.content
  }))
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
export function chatWithAiStream({ message, conversationId, pageContext, cursorId, onEvent }) {
  const controller = new AbortController()
  const { tokenName, tokenValue } = getAuthToken()
  const baseURL = import.meta.env.VITE_API_BASE || '/api'
  const url = `${baseURL}/ai/chat/stream`
  const effectiveConversationId = resolveStreamConversationId(conversationId)
  const clientHistory = clientHistoryFor(effectiveConversationId)

  const promise = (async () => {
    const headers = {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json'
    }
    if (tokenName && tokenValue) {
      headers[tokenName] = tokenValue
    }

    // 先记录当前用户消息到前端临时历史，保证即使本轮 SSE 失败，下一轮追问仍能带上上下文。
    appendTransientHistory(effectiveConversationId, 'user', message)

    const response = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify({
        message,
        conversationId: effectiveConversationId,
        pageContext,
        cursorId,
        clientHistory
      }),
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
    const toolCalls = []
    const dataResults = []

    const parseSseEvent = (evName, evData) => {
      if (!evName || !evData) return
      try {
        const data = JSON.parse(evData)
        if (evName === 'done') {
          result = {
            conversationId: data.conversationId || effectiveConversationId,
            answer: data.answer || '',
            citations: Array.isArray(data.citations) ? data.citations : [],
            toolCalls,
            dataResults,
            elapsedMs: data.elapsedMs || 0
          }
        } else if (evName === 'tool_result') {
          toolCalls.push({
            toolName: data.toolName || '业务数据查询',
            target: data.target || '',
            result: data.result || ''
          })
          const rows = Array.isArray(data.rows) ? data.rows : (Array.isArray(data.data) ? data.data : [])
          if (rows.length) {
            dataResults.push({
              toolName: data.toolName || '业务数据查询',
              target: data.target || '',
              summary: data.result || '',
              columns: Array.isArray(data.columns) ? data.columns : [],
              rows,
              cursorId: data.cursorId || '',
              total: data.total ?? data.totalCount,
              returnedCount: data.returnedCount,
              remainingCount: data.remainingCount,
              hasMore: data.hasMore === true,
              nextPageHint: data.nextPageHint || ''
            })
          }
        } else if (evName === 'error') {
          streamError = data.message || 'AI 流式响应失败，请稍后重试'
        }
        if (onEvent) {
          onEvent({ type: evName, ...data })
        }
      } catch (e) {
        // JSON 解析失败，跳过
      }
    }

    const processBuffer = (lines) => {
      let evName = ''
      let evData = ''
      for (const line of lines) {
        if (line.startsWith('event:')) {
          evName = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          evData = line.slice(5).trim()
        } else if (line === '' && evName && evData) {
          parseSseEvent(evName, evData)
          evName = ''
          evData = ''
        }
      }
      return { evName, evData }
    }

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        // 根因修复：流结束时处理 buffer 中剩余数据
        // done 事件的 data 行可能被 TCP 分帧卡在 buffer 里
        if (buffer.trim()) {
          const finalLines = buffer.split('\n')
          const { evName, evData } = processBuffer(finalLines)
          parseSseEvent(evName, evData)
        }
        break
      }

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      processBuffer(lines)
    }

    if (!result && streamError) {
      throw new Error(streamError)
    }
    if (!result) {
      throw new Error('SSE 连接意外关闭，未收到最终结果')
    }
    if (streamError && !result.answer) {
      result.answer = streamError
    }
    if (result.conversationId) {
      activeStreamConversationId = result.conversationId
    }
    if (result.answer) {
      appendTransientHistory(result.conversationId || effectiveConversationId, 'assistant', result.answer)
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

export function fetchAiConversations(params = {}) {
  return http.get('/ai/conversations', { params })
}

export function fetchAiConversation(id) {
  return http.get(`/ai/conversations/${id}`)
}

export function archiveAiConversation(id) {
  return http.put(`/ai/conversations/${id}/archive`)
}

export function restoreAiConversation(id) {
  return http.put(`/ai/conversations/${id}/restore`)
}

export function deleteAiConversation(id) {
  return http.delete(`/ai/conversations/${id}`)
}

export function clearAiConversations(params = {}) {
  return http.delete('/ai/conversations', { params })
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

export function approveAiMemoryItem(id) {
  return http.put(`/ai/memory/items/${id}/approve`)
}

export function rejectAiMemoryItem(id) {
  return http.put(`/ai/memory/items/${id}/reject`)
}

export function restoreAiMemoryItem(id) {
  return http.put(`/ai/memory/items/${id}/restore`)
}

export function clearAiMemories() {
  return http.delete('/ai/memory/items')
}

export function submitFeedback(payload) {
  return http.post('/ai/feedback', payload)
}
