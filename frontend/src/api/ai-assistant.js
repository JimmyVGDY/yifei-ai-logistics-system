import http from './http.js'
import { createSseParser } from './sse-parser.js'
import { getAuthToken } from '../stores/auth-store.js'

let activeStreamConversationId = ''
// 前端临时历史只用于 SSE 失败或后端历史暂不可用时兜底，不作为权威会话存储。
const transientHistories = new Map()
const MAX_CLIENT_HISTORY = 8
const MAX_HISTORY_CONTENT_LENGTH = 1000
const DEFAULT_SSE_IDLE_TIMEOUT_MS = 30000

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
  // 服务端返回 conversationId 前，先用前端临时 ID 串起同一浏览器会话。
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
  // 只保存用户/助手纯文本，避免把工具明细、token 或结构化表格塞进下一轮 prompt。
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
  let idleTimer = null
  let idleTimedOut = false
  const env = import.meta.env || globalThis.__VITE_ENV__ || {}
  const idleTimeoutMs = Number(env.VITE_AI_SSE_IDLE_TIMEOUT_MS || DEFAULT_SSE_IDLE_TIMEOUT_MS)
  const { tokenName, tokenValue } = getAuthToken()
  const baseURL = env.VITE_API_BASE || '/api'
  const url = `${baseURL}/ai/chat/stream`
  const effectiveConversationId = resolveStreamConversationId(conversationId)
  const clientHistory = clientHistoryFor(effectiveConversationId)

  const promise = (async () => {
    const resetIdleTimer = () => {
      if (idleTimer) {
        clearTimeout(idleTimer)
      }
    if (idleTimeoutMs > 0) {
        idleTimer = setTimeout(() => {
          // 空闲超时直接 abort fetch，避免 promise 长时间 pending 卡住输入框。
          idleTimedOut = true
          controller.abort()
        }, idleTimeoutMs)
      }
    }

    const headers = {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json'
    }
    if (tokenName && tokenValue) {
      headers[tokenName] = tokenValue
    }

    // 先记录当前用户消息到前端临时历史，保证即使本轮 SSE 失败，下一轮追问仍能带上上下文。
    appendTransientHistory(effectiveConversationId, 'user', message)

    try {
      resetIdleTimer()
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
      let result = null
      let streamError = null
      const toolCalls = []
      const dataResults = []

      const parseSseEvent = (evName, evData) => {
        if (!evName || !evData) return
        resetIdleTimer()
        try {
          const data = JSON.parse(evData)
          if (evName === 'done') {
            // done 是前端最终落库/渲染的收口事件，表格数据来自之前累积的 tool_result。
            result = {
              conversationId: data.conversationId || effectiveConversationId,
              answer: data.answer || '',
              citations: Array.isArray(data.citations) ? data.citations : [],
              toolCalls,
              dataResults,
              elapsedMs: data.elapsedMs || 0
            }
          } else if (evName === 'tool_result') {
            // 工具日志只展示安全字段，原始工具码和内部摘要由 sanitizer 再兜底处理。
            toolCalls.push({
              toolName: data.toolName || '业务数据查询',
              target: data.target || '',
              result: data.displaySummary || data.result || '',
              displayToolName: data.displayToolName || '',
              displayTarget: data.displayTarget || '',
              displaySummary: data.displaySummary || ''
            })
            if (Array.isArray(data.dataGroups) && data.dataGroups.length) {
              // 新契约：多模块结果按 dataGroups 展开，避免不同模块 rows/columns 串台。
              for (const group of data.dataGroups) {
                const groupRows = Array.isArray(group.rows) ? group.rows : (Array.isArray(group.data) ? group.data : [])
                if (!groupRows.length) continue
                dataResults.push({
                  // groupId 用于前端合并 key，缺失时用中文目标生成本轮内稳定兜底。
                  groupId: group.groupId || `${group.displayTarget || data.displayTarget || data.target || 'group'}-${dataResults.length}`,
                  toolName: group.displayToolName || data.toolName || '业务数据查询',
                  target: group.displayTarget || data.target || '',
                  displayToolName: group.displayToolName || data.displayToolName || '',
                  displayTarget: group.displayTarget || data.displayTarget || '',
                  displaySummary: group.displaySummary || data.displaySummary || '',
                  summary: group.displaySummary || data.displaySummary || data.result || '',
                  columns: Array.isArray(group.columns) ? group.columns : [],
                  rows: groupRows,
                  cursorId: group.cursorId || '',
                  total: group.total ?? group.totalCount,
                  returnedCount: group.returnedCount,
                  remainingCount: group.remainingCount,
                  hasMore: group.hasMore === true,
                  nextPageHint: group.nextPageHint || ''
                })
              }
            } else {
              // 旧契约兼容：只有顶层 rows/data 时仍生成一张结果卡片。
              const rows = Array.isArray(data.rows) ? data.rows : (Array.isArray(data.data) ? data.data : [])
              if (rows.length) {
                dataResults.push({
                  toolName: data.toolName || '业务数据查询',
                  target: data.target || '',
                  displayToolName: data.displayToolName || '',
                  displayTarget: data.displayTarget || '',
                  displaySummary: data.displaySummary || '',
                  summary: data.displaySummary || data.result || '',
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

      const parser = createSseParser(({ event, data }) => parseSseEvent(event, data))

      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          // 关闭前把 decoder 剩余缓冲喂给 SSE parser，避免最后一个事件丢失。
          parser.feed(decoder.decode())
          parser.close()
          break
        }
        resetIdleTimer()
        parser.feed(decoder.decode(value, { stream: true }))
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
    } catch (error) {
      if (idleTimedOut) {
        throw new Error('AI 流式响应超时，请稍后重试')
      }
      throw error
    } finally {
      if (idleTimer) {
        clearTimeout(idleTimer)
      }
    }
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
