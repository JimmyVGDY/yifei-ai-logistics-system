export function createSseParser(onEvent) {
  let buffer = ''
  let pendingEventName = ''
  let pendingDataLines = []

  const dispatchPendingEvent = () => {
    if (!pendingEventName && pendingDataLines.length === 0) return
    const event = pendingEventName || 'message'
    const data = pendingDataLines.join('\n')
    pendingEventName = ''
    pendingDataLines = []
    onEvent({ event, data })
  }

  const processLine = (rawLine) => {
    const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine
    if (line === '') {
      dispatchPendingEvent()
      return
    }
    if (line.startsWith(':')) return

    const separatorIndex = line.indexOf(':')
    const field = separatorIndex >= 0 ? line.slice(0, separatorIndex) : line
    let value = separatorIndex >= 0 ? line.slice(separatorIndex + 1) : ''
    if (value.startsWith(' ')) {
      value = value.slice(1)
    }

    if (field === 'event') {
      pendingEventName = value.trim()
    } else if (field === 'data') {
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
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      processLines(lines)
    },
    close() {
      if (buffer) {
        const finalLines = buffer.split('\n')
        buffer = ''
        processLines(finalLines)
      }
      dispatchPendingEvent()
    }
  }
}
