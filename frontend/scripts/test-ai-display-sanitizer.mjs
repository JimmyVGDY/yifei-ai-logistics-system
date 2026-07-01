import assert from 'node:assert/strict'

const storage = new Map()
globalThis.sessionStorage = {
  getItem: (key) => storage.has(key) ? storage.get(key) : null,
  setItem: (key, value) => storage.set(key, String(value)),
  removeItem: (key) => storage.delete(key)
}

const authStore = await import('../src/stores/auth-store.js')
const sanitizer = await import('../src/utils/ai-display-sanitizer.js')

authStore.clearAuthToken()
authStore.saveAuthToken({
  tokenName: 'satoken',
  tokenValue: 'test-token',
  permissions: {
    task: { actions: ['query'], columns: ['task_no', 'driver_name', 'waybill_no'] }
  }
})

const result = sanitizer.normalizeDataResult({
  toolName: 'execute_readonly_sql',
  target: 'generated_sql',
  summary: '| order_no | driver_name |\n| --- | --- |',
  columns: ['order_no', 'driver_id', '司机ID', 'driver_name', 'waybill_no', 'secret_field', 'id'],
  rows: [{
    order_no: 'LO-001',
    driver_id: 260602222327046,
    司机ID: 260602222327046,
    driver_name: '张三',
    waybill_no: 'WB-001',
    secret_field: 'hidden',
    id: 1
  }]
})

assert.equal(result.toolName, '统计分析')
assert.equal(result.target, '统计结果')
assert.equal(result.summary, '查询完成，结构化结果已在下方表格展示。')
assert.deepEqual(result.columns, ['司机姓名', '运单号'])
assert.deepEqual(result.rows, [{ 司机姓名: '张三', 运单号: 'WB-001' }])

const safe = sanitizer.normalizeDataResult({
  displayToolName: '业务数据查询',
  displayTarget: '运输任务',
  displaySummary: '已查询运输任务，共匹配 1 条记录。',
  columns: ['任务号', '司机姓名'],
  rows: [{ 任务号: 'TASK-001', 司机姓名: '李四' }]
})

assert.equal(safe.toolName, '业务数据查询')
assert.equal(safe.target, '运输任务')
assert.deepEqual(safe.columns, ['任务号', '司机姓名'])
assert.deepEqual(safe.rows, [{ 任务号: 'TASK-001', 司机姓名: '李四' }])

assert.equal(
  sanitizer.sanitizeAssistantContent('select order_no from logistics_order'),
  '查询完成，结构化结果已在下方表格展示。'
)

console.log('ai display sanitizer tests passed')
