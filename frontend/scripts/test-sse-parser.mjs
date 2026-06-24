import assert from 'node:assert/strict'

import { createSseParser } from '../src/api/sse-parser.js'

function parseChunks(chunks) {
  const events = []
  const parser = createSseParser(event => events.push(event))
  for (const chunk of chunks) {
    parser.feed(chunk)
  }
  parser.close()
  return events
}

{
  const events = parseChunks([
    'event: thinking\n',
    'data: {"message":"loading"}\n\n'
  ])
  assert.deepEqual(events, [
    { event: 'thinking', data: '{"message":"loading"}' }
  ])
}

{
  const events = parseChunks([
    'eve',
    'nt: tool_result\r\n',
    'data: {"rows":[1]}\r\n\r\n'
  ])
  assert.deepEqual(events, [
    { event: 'tool_result', data: '{"rows":[1]}' }
  ])
}

{
  const events = parseChunks([
    'event: done\n',
    'data: {"answer":"line1',
    '\\nline2"}'
  ])
  assert.deepEqual(events, [
    { event: 'done', data: '{"answer":"line1\\nline2"}' }
  ])
}

{
  const events = parseChunks([
    'event: error\n',
    'data: {"message":"failed"}\n\n'
  ])
  assert.equal(events[0].event, 'error')
  assert.equal(events[0].data, '{"message":"failed"}')
}

{
  const events = parseChunks([
    ': heartbeat\n\n',
    'data: first\n',
    'data: second\n\n'
  ])
  assert.deepEqual(events, [
    { event: 'message', data: 'first\nsecond' }
  ])
}

console.log('sse parser tests passed')
