// 轻量 SSE 解析器：支持 fetch ReadableStream 分块输入，不依赖浏览器 EventSource。
export function createSseParser(onEvent) {
  let buffer = ''
  let pendingEventName = ''
  let pendingDataLines = []

  const dispatchPendingEvent = () => {
    // 空行代表一个 SSE 事件结束；没有 event 时按标准默认 message。
    if (!pendingEventName && pendingDataLines.length === 0) return
    const event = pendingEventName || 'message'
    const data = pendingDataLines.join('\n')
    pendingEventName = ''
    pendingDataLines = []
    onEvent({ event, data })
  }

  const processLine = (rawLine) => {
    // 兼容 CRLF，后端或代理可能用 \r\n 分隔。
    const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine
    if (line === '') {
      dispatchPendingEvent()
      return
    }
    if (line.startsWith(':')) return // 冒号开头是 SSE 注释/心跳行。

    const separatorIndex = line.indexOf(':')
    const field = separatorIndex >= 0 ? line.slice(0, separatorIndex) : line
    let value = separatorIndex >= 0 ? line.slice(separatorIndex + 1) : ''
    if (value.startsWith(' ')) {
      value = value.slice(1)
    }

    if (field === 'event') {
      pendingEventName = value.trim()
    } else if (field === 'data') {
      // 多行 data 要按 SSE 规范用换行拼回一个事件体。
      pendingDataLines.push(value)
    }
  }

  const processLines = (lines) => {
    for (const line of lines) {
      processLine(line)
    }
  }

  return {
    feed(chunk) {
      if (!chunk) return
      buffer += chunk
      // 保留最后一个不完整行，等待下一次网络分块补齐。
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      processLines(lines)
    },
    close() {
      if (buffer) {
        // 流结束时仍要处理缓冲区残留，否则最后一个事件可能丢失。
        const finalLines = buffer.split('\n')
        buffer = ''
        processLines(finalLines)
      }
      dispatchPendingEvent()
    }
  }
}
