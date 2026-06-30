import assert from 'node:assert/strict'

globalThis.__VITE_ENV__ = {
  VITE_API_BASE: '/api',
  VITE_AI_SSE_IDLE_TIMEOUT_MS: '5'
}
globalThis.sessionStorage = {
  getItem: () => null,
  setItem: () => {},
  removeItem: () => {}
}
globalThis.window = {
  location: { pathname: '/', search: '', href: '' }
}

const { chatWithAiStream } = await import('../src/api/ai-assistant.js')

globalThis.fetch = (_url, options = {}) => new Promise((_resolve, reject) => {
  options.signal?.addEventListener('abort', () => {
    const error = new Error('aborted')
    error.name = 'AbortError'
    reject(error)
  })
})

const stream = chatWithAiStream({
  message: '测试',
  conversationId: 'conv-test',
  onEvent: () => {}
})

await assert.rejects(
  stream.promise,
  error => error.message === 'AI 流式响应超时，请稍后重试'
)

console.log('ai assistant stream tests passed')
