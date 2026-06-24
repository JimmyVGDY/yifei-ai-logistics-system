<template>
  <section class="ai-page">
    <aside class="ai-sidebar">
      <div class="panel-header compact">
        <div>
          <h3>会话</h3>
          <p>{{ conversations.length }} 条记录</p>
        </div>
        <el-tooltip content="刷新会话">
          <el-button size="small" circle :icon="Refresh" @click="loadConversations" />
        </el-tooltip>
      </div>

      <div class="conversation-tools">
        <el-radio-group v-model="conversationStatus" size="small" @change="reloadConversations">
          <el-radio-button label="ACTIVE">当前</el-radio-button>
          <el-radio-button label="ARCHIVED">归档</el-radio-button>
        </el-radio-group>
        <el-input
          v-model="conversationKeyword"
          size="small"
          clearable
          placeholder="搜索会话"
          @clear="reloadConversations"
          @keyup.enter="reloadConversations"
        />
      </div>

      <el-scrollbar class="conversation-list">
        <div
          v-for="item in conversations"
          :key="item.conversationId"
          class="conversation-item"
          :class="{ active: item.conversationId === conversationId }"
          @click="selectConversation(item.conversationId)"
        >
          <div class="conversation-main">
            <span class="conversation-title">{{ item.title || '新会话' }}</span>
            <span class="conversation-time">{{ item.updatedAt || item.createdAt }}</span>
          </div>
          <div class="conversation-actions">
            <el-button
              v-if="conversationStatus === 'ACTIVE'"
              link
              size="small"
              @click.stop="archiveConversation(item.conversationId)"
            >归档</el-button>
            <el-button
              v-else
              link
              size="small"
              type="success"
              @click.stop="restoreConversation(item.conversationId)"
            >恢复</el-button>
            <el-button link size="small" type="danger" @click.stop="removeConversation(item.conversationId)">删除</el-button>
          </div>
        </div>
        <el-empty v-if="!conversations.length" :image-size="72" description="暂无会话" />
      </el-scrollbar>
      <el-button
        class="clear-conversations"
        size="small"
        text
        type="danger"
        :disabled="!conversations.length"
        @click="clearConversations"
      >清空{{ conversationStatus === 'ARCHIVED' ? '归档' : '当前' }}会话</el-button>
      <div class="memory-card">
        <div class="memory-title">
          <div>
            <h3>长期记忆</h3>
            <p>{{ memoryProfile?.memoryCount || 0 }} 条偏好</p>
          </div>
          <el-switch v-model="memoryEnabled" size="small" :loading="memoryLoading" @change="toggleMemory" />
        </div>
        <el-segmented
          v-model="memoryStatus"
          class="memory-status-tabs"
          size="small"
          :options="MEMORY_STATUS_OPTIONS"
          @change="loadMemory"
        />
        <el-scrollbar class="memory-list">
          <div v-for="item in memoryItems" :key="item.id" class="memory-item">
            <div class="memory-item-head">
              <strong>{{ item.memoryTitle }}</strong>
              <el-tag size="small" :type="memoryStatusTagType(item.status)">{{ memoryStatusLabel(item.status) }}</el-tag>
            </div>
            <p>{{ item.memorySummary }}</p>
            <div class="memory-meta">
              <span>{{ memoryScopeLabel(item.memoryScope) }}</span>
              <span>置信度 {{ formatConfidence(item.confidence) }}</span>
              <span v-if="item.evidenceCount">证据 {{ item.evidenceCount }}</span>
              <span v-if="item.conflictGroup">冲突组 {{ item.conflictGroup }}</span>
            </div>
            <div class="memory-actions">
              <el-button
                v-if="canApproveMemory(item)"
                link
                type="success"
                size="small"
                @click="approveMemory(item.id)"
              >批准</el-button>
              <el-button
                v-if="canRejectMemory(item)"
                link
                type="warning"
                size="small"
                @click="rejectMemory(item.id)"
              >拒绝</el-button>
              <el-button
                v-if="canRestoreMemory(item)"
                link
                type="primary"
                size="small"
                @click="restoreMemory(item.id)"
              >恢复</el-button>
              <el-button link type="danger" size="small" @click="removeMemory(item.id)">删除</el-button>
            </div>
          </div>
          <el-empty v-if="!memoryItems.length" :image-size="54" description="暂无长期记忆" />
        </el-scrollbar>
        <el-button size="small" text type="danger" :disabled="!memoryItems.length" @click="clearMemory">清空长期记忆</el-button>
      </div>
    </aside>

    <main class="ai-workspace">
      <div class="assistant-header">
        <div class="assistant-title">
          <span class="assistant-mark">AI</span>
          <div>
            <h2>AI助手</h2>
            <p>只读问答、日志排障和系统文档检索</p>
          </div>
        </div>
        <div class="assistant-badges">
          <el-tag type="success" effect="light">只读模式</el-tag>
          <el-tag effect="plain">可审计</el-tag>
        </div>
      </div>

      <div class="chat-shell">
        <el-scrollbar ref="chatScrollbarRef" class="chat-stream">
          <div class="welcome-strip">
            <strong>我可以帮你查看系统文档、解释业务页面、按链路排查日志。</strong>
            <span>涉及新增、修改、删除的请求只会给出建议，不会直接执行。</span>
          </div>

          <div v-for="(item, index) in messages" :key="index" class="message-row" :class="item.role">
            <div class="avatar">{{ item.role === 'user' ? '我' : 'AI' }}</div>
            <div class="message-body">
              <div class="message-meta">{{ item.role === 'user' ? '你' : '物流AI助手' }}</div>
              <p v-if="item.role === 'user'">{{ item.content }}</p>
              <div v-else class="markdown-body" v-html="renderMarkdown(item.content)"></div>
              <div v-if="item.role === 'assistant' && item.messageId" class="message-feedback">
                <button :class="{ active: item._rating === 'UP' }" title="有帮助" @click="handleFeedback(item, 'UP')">👍</button>
                <button :class="{ active: item._rating === 'DOWN' }" title="有待改进" @click="handleFeedback(item, 'DOWN')">👎</button>
              </div>
            </div>
          </div>

          <div v-if="chatLoading" class="message-row assistant pending">
            <div class="avatar">AI</div>
            <div class="message-body">
              <div class="message-meta">
                物流AI助手正在处理
                <span class="elapsed-time">{{ formatElapsed(streamElapsed) }}</span>
              </div>
              <div class="thinking-card">
                <!-- 思考 / 调用工具阶段 -->
                <div class="typing-line" v-if="streamProgress === 'thinking'">
                  <span>正在分析问题</span>
                  <i></i><i></i><i></i>
                </div>
                <!-- 工具调用日志 -->
                <div v-if="streamToolLog.length" class="stream-tool-log">
                  <div v-for="(item, i) in streamToolLog" :key="i" class="stream-tool-item" :class="item.status">
                    <span class="stream-tool-icon">{{ item.status === 'running' ? '⏳' : '✅' }}</span>
                    <span class="stream-tool-name">{{ item.name }}</span>
                    <span class="stream-tool-target">{{ item.target }}</span>
                    <span class="stream-tool-result" v-if="item.result">{{ item.result }}</span>
                  </div>
                </div>
                <!-- 进度条 -->
                <div class="stream-progress-bar" v-if="streamStepIndex > 0">
                  <div class="stream-progress-fill" :style="{ width: (streamStepIndex / streamMaxToolCalls * 100) + '%' }"></div>
                  <span class="stream-progress-text">{{ streamStepIndex }}/{{ streamMaxToolCalls }} 工具调用</span>
                </div>
              </div>
            </div>
          </div>
        </el-scrollbar>
      </div>

      <div class="ai-meta" v-if="lastResponse || chatLoading">
        <div v-if="dataResults.length" class="data-result-panel">
          <div v-for="(result, index) in dataResults" :key="dataResultKey(result, index)" class="data-result-card">
            <div class="data-result-head">
              <div>
                <strong>{{ result.toolName || '业务数据查询' }} · {{ result.target || '查询结果' }}</strong>
                <p>{{ result.summary || `已加载 ${result.rows?.length || 0} 条结构化数据` }}</p>
              </div>
              <el-button
                v-if="(result.rows?.length || 0) > DATA_PREVIEW_LIMIT"
                size="small"
                type="primary"
                plain
                @click="openDataDrawer(result)"
              >
                查看全部 {{ result.rows.length }} 条
              </el-button>
              <el-button
                v-if="result.hasMore"
                size="small"
                type="success"
                plain
                @click="continueDataResult(result)"
              >
                继续查看剩余 {{ result.remainingCount }} 条
              </el-button>
            </div>
            <el-table
              v-if="result.rows?.length"
              :data="previewRows(result)"
              size="small"
              border
              max-height="180"
              class="data-preview-table"
            >
              <el-table-column
                v-for="column in displayColumns(result)"
                :key="column.prop"
                :prop="column.prop"
                :label="column.label"
                min-width="140"
                show-overflow-tooltip
              />
            </el-table>
            <p v-if="(result.rows?.length || 0) > DATA_PREVIEW_LIMIT" class="data-preview-tip">
              默认预览前 {{ DATA_PREVIEW_LIMIT }} 条，其余数据可在完整表格中查看。
            </p>
            <p v-if="result.hasMore" class="data-preview-tip">
              {{ result.nextPageHint || `还有 ${result.remainingCount} 条记录，可继续分页查看。` }}
            </p>
          </div>
        </div>
        <el-collapse>
          <el-collapse-item name="citations">
            <template #title>
              <div class="collapse-title">
                <span>引用来源</span>
                <el-tag size="small" effect="plain">{{ lastResponse?.citations?.length || 0 }}</el-tag>
              </div>
            </template>
            <el-empty v-if="!lastResponse?.citations?.length" :image-size="64" description="暂无引用" />
            <div v-for="item in lastResponse?.citations || []" :key="item.reference" class="citation-item">
              <strong>{{ item.title }}</strong>
              <small>{{ item.reference }}</small>
              <p>{{ item.snippet }}</p>
            </div>
          </el-collapse-item>
          <el-collapse-item name="tools">
            <template #title>
              <div class="collapse-title">
                <span>工具调用</span>
                <el-tag size="small" effect="plain">{{ lastResponse?.toolCalls?.length || 0 }}</el-tag>
              </div>
            </template>
            <el-empty v-if="!lastResponse?.toolCalls?.length" :image-size="64" description="暂无工具调用" />
            <div class="tool-list">
              <el-tag v-for="item in lastResponse?.toolCalls || []" :key="item.toolName + item.target" class="tool-tag">
                {{ item.toolName }} · {{ item.target }} · {{ item.result }}
              </el-tag>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>

      <div class="composer">
        <el-input
          v-model="message"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="输入问题、traceId、operationId 或 loginSessionId"
          @keydown.enter.exact.prevent="sendMessage"
        />
        <el-button type="primary" :icon="Promotion" :loading="chatLoading" @click="sendMessage">发送</el-button>
      </div>
    </main>

    <LogAnalysisPanel
      :visible="canAnalyzeLogs"
      :form="logForm"
      :loading="logLoading"
      :result="logResult"
      @analyze="analyzeLogs"
    />

    <DataResultDrawer
      v-model:visible="dataDrawerVisible"
      :result="activeDataResult"
      :field-label="fieldLabel"
      @load-more="continueDataResult(activeDataResult)"
    />
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Promotion, Refresh, Search } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import {
  analyzeAiLogs, submitFeedback,
  approveAiMemoryItem,
  archiveAiConversation,
  chatWithAiStream,
  clearAiConversations,
  clearAiMemories,
  deleteAiConversation,
  deleteAiMemoryItem,
  fetchAiConversation,
  fetchAiConversations,
  fetchAiMemoryItems,
  fetchAiMemoryProfile,
  rejectAiMemoryItem,
  restoreAiConversation,
  restoreAiMemoryItem,
  updateAiMemorySettings
} from '../api/ai-assistant'
import { hasPermission, getAuthToken } from '../stores/auth-store'
import LogAnalysisPanel from '../components/LogAnalysisPanel.vue'
import DataResultDrawer from '../components/DataResultDrawer.vue'

const conversations = ref([])
const conversationStatus = ref('ACTIVE')
const conversationKeyword = ref('')
const conversationPage = ref(1)
const conversationTotal = ref(0)
const conversationId = ref('')
const messages = ref([])
const message = ref('')
const chatLoading = ref(false)
const logLoading = ref(false)
const lastResponse = ref(null)
const logResult = ref(null)
const memoryProfile = ref(null)
const memoryItems = ref([])
const memoryEnabled = ref(true)
const memoryLoading = ref(false)
const memoryStatus = ref('ACTIVE')
const chatScrollbarRef = ref(null)
const canAnalyzeLogs = computed(() => hasPermission('ai:log:analyze'))
const DATA_PREVIEW_LIMIT = 10

const MEMORY_STATUS_OPTIONS = [
  { value: 'ACTIVE', label: '生效中' },
  { value: 'CANDIDATE', label: '候选' },
  { value: 'CONFLICTED', label: '冲突' },
  { value: 'SUSPECTED_HALLUCINATION', label: '疑似幻觉' },
  { value: 'ARCHIVED', label: '已归档' },
  { value: 'REJECTED', label: '已拒绝' }
]

/** 数据库字段名 → 中文展示名的全局映射，用于 AI 结构化数据表格的列标题 */
const FIELD_LABEL_MAP = {
  id: 'ID', order_no: '订单号', order_id: '订单ID', task_id: '任务ID', waybill_id: '运单ID',
  dispatch_id: '调度ID', driver_id: '司机ID', vehicle_id: '车辆ID', route_id: '路线ID',
  warehouse_id: '仓库ID', customer_id: '客户ID', role_id: '角色ID', parent_id: '父级ID',
  customer_code: '客户编号', customer_name: '客户名称', contact_name: '联系人',
  contact_phone: '联系电话', province: '省份', city: '城市', address: '地址',
  order_no_alias: '订单号', customer_name_alias: '客户名称',
  sender_address: '发货地址', receiver_address: '收货地址', cargo_name: '货物名称',
  cargo_weight: '重量', cargo_volume: '体积', status: '状态',
  planned_pickup_time: '计划揽收时间', planned_delivery_time: '计划送达时间',
  warehouse_code: '仓库编码', warehouse_name: '仓库名称', manager_name: '负责人',
  capacity_cubic: '容量', driver_code: '司机编号', driver_name: '司机姓名',
  phone: '手机号', license_no: '驾驶证号', license_type: '准驾车型',
  vehicle_no: '车牌号', vehicle_type: '车辆类型', load_capacity_kg: '载重',
  volume_capacity_cubic: '容积', current_city: '当前城市',
  route_code: '路线编码', origin_city: '出发城市', destination_city: '目的城市',
  distance_km: '距离(km)', estimated_hours: '预计耗时(h)',
  waybill_no: '运单号', start_site: '始发网点', target_site: '目的网点',
  current_location: '当前位置', transport_status: '运输状态',
  planned_departure_time: '计划出发时间', planned_arrival_time: '计划到达时间',
  dispatch_status: '调度状态', task_no: '任务号', task_status: '任务状态',
  proof_url: '签收凭证', current_status: '当前状态', operator_name: '操作人',
  operation_desc: '操作说明',
  exception_type: '异常类型', exception_desc: '异常描述', exception_status: '异常状态',
  report_user: '上报人', report_time: '上报时间', handle_user: '处理人', handle_time: '处理时间',
  base_fee: '基础运费', weight_fee: '重量费用', distance_fee: '距离费用',
  additional_fee: '附加费', discount_fee: '优惠金额', payable_fee: '应付金额',
  actual_fee: '实付金额', payment_status: '付款状态',
  sku_code: 'SKU编码', sku_name: 'SKU名称', quantity: '数量', locked_quantity: '锁定数量',
  bill_no: '账单号', base_amount: '基础金额', fuel_surcharge: '燃油附加费',
  discount_amount: '优惠金额', payable_amount: '应付金额', pay_status: '支付状态',
  tracking_status: '跟踪状态', location: '位置', description: '描述', occurred_at: '发生时间',
  user_code: '用户编号', username: '登录账号', real_name: '姓名', mobile: '手机号',
  email: '邮箱', role_name: '角色名称', role_code: '角色编码',
  customer_subject_type: '客户主体类型', customer_account_type: '客户账号类型',
  menu_name: '菜单名称', menu_path: '菜单路径', permission_code: '权限编码', sort_no: '排序号',
  created_at: '创建时间', updated_at: '更新时间', create_time: '创建时间', update_time: '更新时间',
  operation_time: '操作时间', deleted: '已删除', version: '版本号'
}

/** 中文标签 → snake_case 字段名的反向映射 */
const LABEL_TO_FIELD = Object.fromEntries(
  Object.entries(FIELD_LABEL_MAP).map(([field, label]) => [label, field])
)

/** 从用户的结构化权限中收集所有模块的可见列名（snake_case）全局并集 */
function allAllowedColumns() {
  const perms = getAuthToken().permissions
  if (Array.isArray(perms)) return new Set()
  const all = new Set()
  for (const [module, scope] of Object.entries(perms)) {
    if (module === '_standalone') continue
    for (const col of (scope.columns || [])) {
      all.add(col)
    }
  }
  return all
}

/**
 * 检查 displayColumns 中的列是否用户有权查看。
 * prop 可能是 snake_case（自定义 SQL 路径）或中文标签（模块查询路径）。
 */
function filterVisibleColumns(columns) {
  const allowed = allAllowedColumns()
  if (allowed.size === 0) return columns // 无列权限数据时放行
  return columns.filter(col => {
    if (allowed.has(col.prop)) return true
    // 中文标签 → snake_case 反向匹配
    const fieldName = LABEL_TO_FIELD[col.prop]
    return fieldName && allowed.has(fieldName)
  })
}

/** 将数据库字段名转为中文展示名，未映射的字段使用原字段名并去除下划线首字母大写 */
function fieldLabel(fieldName) {
  if (FIELD_LABEL_MAP[fieldName]) {
    return FIELD_LABEL_MAP[fieldName]
  }
  // 处理 statusLabel 后缀：payment_statusLabel → 付款状态
  if (fieldName.endsWith('Label') && FIELD_LABEL_MAP[fieldName.replace(/Label$/, '')]) {
    return FIELD_LABEL_MAP[fieldName.replace(/Label$/, '')]
  }
  // 通用回退：下划线分隔转首字母大写
  return fieldName.split('_').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')
}
const dataDrawerVisible = ref(false)
const activeDataResult = ref(null)
const dataResults = computed(() => normalizeDataResults(lastResponse.value?.dataResults || []))

/** SSE 流式进度状态 */
const streamProgress = ref(null)
const streamingTokens = ref(false)
const streamStepIndex = ref(0)
const streamMaxToolCalls = ref(8)
const streamElapsed = ref(0)
const streamToolLog = ref([])
const pendingCursorId = ref('')
let streamElapsedTimer = null
let streamAbort = null
const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true
})
const logForm = reactive({
  traceId: '',
  operationId: '',
  loginSessionId: '',
  userId: '',
  uri: ''
})

async function loadConversations() {
  const page = await fetchAiConversations({
    status: conversationStatus.value,
    keyword: conversationKeyword.value || undefined,
    page: conversationPage.value,
    pageSize: 30
  })
  conversations.value = page?.records || []
  conversationTotal.value = page?.total || 0
}

async function reloadConversations() {
  conversationPage.value = 1
  await loadConversations()
}

async function selectConversation(id) {
  const conversation = await fetchAiConversation(id)
  conversationId.value = conversation?.conversationId || id
  messages.value = conversation?.messages || []
  lastResponse.value = null
  await scrollToBottom()
}

async function sendMessage() {
  if (!message.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  const content = message.value.trim()
  const cursorId = pendingCursorId.value
  pendingCursorId.value = ''
  messages.value.push({ role: 'user', content })
  message.value = ''
  lastResponse.value = null

  // 初始化流式进度 — 预推空消息占位，token 事件流式追加
  chatLoading.value = true
  streamingTokens.value = false
  streamProgress.value = 'thinking'
  streamStepIndex.value = 0
  streamMaxToolCalls.value = 8
  streamElapsed.value = 0
  streamToolLog.value = []
  startElapsedTimer()
  // 预推空消息作为流式写入目标
  const assistantMsgIndex = messages.value.length
  messages.value.push({ role: 'assistant', content: '', _streaming: true })
  await scrollToBottom()

  const stream = chatWithAiStream({
    message: content,
    conversationId: conversationId.value,
    pageContext: window.location.pathname,
    cursorId,
    onEvent: handleStreamEvent
  })
  streamAbort = stream.abort

  try {
    const response = await stream.promise
    conversationId.value = response.conversationId
    lastResponse.value = response
    // 流式完成 — 用完整 answer 替换流式内容，移除 _streaming 标记
    if (messages.value[assistantMsgIndex]) {
      messages.value[assistantMsgIndex].content = response.answer || messages.value[assistantMsgIndex].content
      messages.value[assistantMsgIndex]._streaming = false
    }
    await loadConversations()
    await loadMemory()
  } catch (err) {
    if (err.name !== 'AbortError') {
      const errorMsg = err.message || '系统响应超时，请稍后重试'
      messages.value.push({ role: 'assistant', content: errorMsg })
      ElMessage.error(errorMsg)
    }
  } finally {
    chatLoading.value = false
    streamProgress.value = null
    streamAbort = null
    stopElapsedTimer()
    await scrollToBottom()
  }
}

async function archiveConversation(id) {
  await archiveAiConversation(id)
  ElMessage.success('会话已归档')
  if (conversationId.value === id) {
    conversationId.value = ''
    messages.value = []
  }
  await loadConversations()
}

async function restoreConversation(id) {
  await restoreAiConversation(id)
  ElMessage.success('会话已恢复')
  await loadConversations()
}

async function removeConversation(id) {
  await ElMessageBox.confirm('确认删除该 AI 会话吗？删除后当前列表将不再显示。', '删除会话')
  await deleteAiConversation(id)
  ElMessage.success('会话已删除')
  if (conversationId.value === id) {
    conversationId.value = ''
    messages.value = []
    lastResponse.value = null
  }
  await loadConversations()
}

async function clearConversations() {
  const scopeText = conversationStatus.value === 'ARCHIVED' ? '已归档会话' : '当前会话'
  await ElMessageBox.confirm(`确认清空当前账号的${scopeText}吗？该操作会隐藏该范围内的会话。`, '清空会话')
  await clearAiConversations({ status: conversationStatus.value })
  ElMessage.success('会话已清空')
  conversationId.value = ''
  messages.value = []
  lastResponse.value = null
  await loadConversations()
}

function handleStreamEvent(event) {
  switch (event.type) {
    case 'thinking':
      streamProgress.value = 'thinking'
      streamStepIndex.value = 1
      break
    case 'tool_start':
      streamProgress.value = 'calling'
      streamMaxToolCalls.value = event.maxToolCalls || streamMaxToolCalls.value
      streamStepIndex.value = Math.min((event.toolCallCount || 0) + 1, streamMaxToolCalls.value)
      streamToolLog.value.push({
        name: event.toolName || '查询',
        target: event.target || '',
        result: '',
        status: 'running'
      })
      break
    case 'tool_result':
      streamMaxToolCalls.value = event.maxToolCalls || streamMaxToolCalls.value
      streamStepIndex.value = Math.min((event.toolCallCount || 0) + 1, streamMaxToolCalls.value)
      // 更新最近一条 running 的工具日志
      for (let i = streamToolLog.value.length - 1; i >= 0; i--) {
        if (streamToolLog.value[i].status === 'running') {
          streamToolLog.value[i].result = event.result || ''
          streamToolLog.value[i].status = 'done'
          break
        }
      }
      break
    case 'token':
      // 流式 token 到达，保持 loading 状态直到 done 再展示完整回答
      streamingTokens.value = true
      streamProgress.value = 'streaming'
      break
    case 'done':
      streamProgress.value = 'done'
      streamingTokens.value = false
      streamElapsed.value = event.elapsedMs || 0
      break
    case 'error':
      streamProgress.value = 'error'
      streamingTokens.value = false
      streamElapsed.value = event.elapsedMs || 0
      break
    case 'heartbeat':
      streamProgress.value = streamProgress.value || 'thinking'
      break
    default:
      break
  }
}

function startElapsedTimer() {
  streamElapsed.value = 0
  window.clearInterval(streamElapsedTimer)
  streamElapsedTimer = window.setInterval(() => {
    streamElapsed.value += 100
  }, 100)
}

function stopElapsedTimer() {
  window.clearInterval(streamElapsedTimer)
  streamElapsedTimer = null
}

async function analyzeLogs() {
  logLoading.value = true
  try {
    logResult.value = await analyzeAiLogs({ ...logForm })
  } catch (error) {
    ElMessage.error('日志分析失败：' + ((error.response?.data?.message || error.message) || '未知错误'))
    logResult.value = null
  } finally {
    logLoading.value = false
  }
}

async function loadMemory() {
  memoryLoading.value = true
  try {
    const [profile, page] = await Promise.all([
      fetchAiMemoryProfile(),
      fetchAiMemoryItems({ page: 1, pageSize: 20, status: memoryStatus.value })
    ])
    memoryProfile.value = profile
    memoryEnabled.value = profile?.memoryEnabled !== false
    memoryItems.value = page?.records || []
  } finally {
    memoryLoading.value = false
  }
}

async function toggleMemory(value) {
  memoryLoading.value = true
  try {
    memoryProfile.value = await updateAiMemorySettings({ memoryEnabled: value })
    memoryEnabled.value = memoryProfile.value?.memoryEnabled !== false
    ElMessage.success(value ? '已开启长期记忆' : '已关闭长期记忆')
  } finally {
    memoryLoading.value = false
  }
}

function memoryStatusLabel(status) {
  return MEMORY_STATUS_OPTIONS.find((item) => item.value === status)?.label || '未知'
}

function memoryStatusTagType(status) {
  if (status === 'ACTIVE') return 'success'
  if (status === 'CANDIDATE') return 'info'
  if (status === 'CONFLICTED' || status === 'SUSPECTED_HALLUCINATION') return 'warning'
  if (status === 'ARCHIVED' || status === 'REJECTED') return 'danger'
  return 'info'
}

function memoryScopeLabel(scope) {
  if (scope === 'GLOBAL') return '全局'
  if (scope === 'MODULE') return '模块'
  if (scope === 'SCENARIO') return '场景'
  return '未分组'
}

function formatConfidence(value) {
  if (value === null || value === undefined || value === '') return '-'
  return `${Math.round(Number(value) * 100)}%`
}

function canApproveMemory(item) {
  return ['CANDIDATE', 'CONFLICTED', 'SUSPECTED_HALLUCINATION'].includes(item.status)
}

function canRejectMemory(item) {
  return ['CANDIDATE', 'CONFLICTED', 'SUSPECTED_HALLUCINATION'].includes(item.status)
}

function canRestoreMemory(item) {
  return ['ARCHIVED', 'REJECTED'].includes(item.status)
}

async function approveMemory(id) {
  await approveAiMemoryItem(id)
  ElMessage.success('已批准长期记忆')
  memoryStatus.value = 'ACTIVE'
  await loadMemory()
}

async function rejectMemory(id) {
  await rejectAiMemoryItem(id)
  ElMessage.success('已拒绝长期记忆')
  await loadMemory()
}

async function restoreMemory(id) {
  await restoreAiMemoryItem(id)
  ElMessage.success('已恢复为候选记忆')
  memoryStatus.value = 'CANDIDATE'
  await loadMemory()
}

async function removeMemory(id) {
  await deleteAiMemoryItem(id)
  ElMessage.success('已删除长期记忆')
  await loadMemory()
}

async function clearMemory() {
  await ElMessageBox.confirm('确认清空当前账号的全部 AI 长期记忆吗？', '清空长期记忆')
  await clearAiMemories()
  ElMessage.success('已清空长期记忆')
  await loadMemory()
}

/** 提交 AI 回答的点赞/点踩反馈 */
async function handleFeedback(item, rating) {
  if (item._rating === rating) return // 不重复提交
  try {
    await submitFeedback({
      messageId: item.messageId,
      conversationId: item.conversationId || conversationId.value,
      rating
    })
    item._rating = rating
    ElMessage.success(rating === 'UP' ? '感谢反馈，我们会继续优化' : '感谢反馈，我们会努力改进')
  } catch {
    // 静默失败，不影响使用体验
  }
}

function renderMarkdown(content) {
  const html = markdown.render(content || '')
  return DOMPurify.sanitize(html, {
    USE_PROFILES: { html: true },
    ADD_ATTR: ['target', 'rel']
  })
}

/**
 * 将后端或 SSE 工具事件返回的结构化结果统一成前端表格可消费的格式。
 * 这里只做展示层兜底，不改变后端只读查询、权限和脱敏逻辑。
 */
function normalizeDataResults(results) {
  const merged = new Map()
  for (const item of (results || [])) {
    const rows = Array.isArray(item.rows) ? item.rows : []
    if (!rows.length) {
      continue
    }
    const columns = Array.isArray(item.columns) && item.columns.length
      ? item.columns
      : Object.keys(rows[0] || {})
    const normalized = {
      toolName: item.toolName || '业务数据查询',
      target: item.target || '查询结果',
      summary: item.summary || item.result || '',
      columns,
      rows,
      cursorId: item.cursorId || '',
      total: Number(item.total ?? rows.length),
      returnedCount: Number(item.returnedCount ?? rows.length),
      remainingCount: Number(item.remainingCount ?? 0),
      hasMore: item.hasMore === true,
      nextPageHint: item.nextPageHint || ''
    }
    /*
     * SSE 过程中可能多次收到同一查询的 tool_result，继续分页时也可能再次返回同一游标。
     * 这里按 cursorId 优先、工具名+目标兜底做替换，避免页面堆出多张重复大表格。
     */
    const key = normalized.cursorId || `${normalized.toolName}-${normalized.target}`
    merged.set(key, normalized)
  }
  return Array.from(merged.values())
}

function previewRows(result) {
  return (result?.rows || []).slice(0, DATA_PREVIEW_LIMIT)
}

function displayColumns(result) {
  const rawColumns = (Array.isArray(result?.columns) && result.columns.length)
    ? result.columns
    : Object.keys(result?.rows?.[0] || {})
  const allColumns = rawColumns.map(col => ({ prop: col, label: fieldLabel(col) }))
  // 前端兜底过滤：按 RBAC 列权限隐藏无权列
  return filterVisibleColumns(allColumns)
}

function openDataDrawer(result) {
  activeDataResult.value = result
  dataDrawerVisible.value = true
}

async function continueDataResult(result) {
  if (chatLoading.value) {
    return
  }
  if (!result?.cursorId) {
    ElMessage.warning('这条结果缺少分页游标，请重新发起查询')
    return
  }
  pendingCursorId.value = result.cursorId
  message.value = `继续查看${result?.target || '当前查询'}剩余数据`
  await sendMessage()
}

function dataResultKey(result, index) {
  return `${result.toolName || 'tool'}-${result.target || 'target'}-${index}`
}

function formatElapsed(ms) {
  if (ms < 1000) return ms + 'ms'
  return (ms / 1000).toFixed(1) + 's'
}

async function scrollToBottom() {
  await nextTick()
  const scrollbar = chatScrollbarRef.value
  if (scrollbar?.setScrollTop) {
    scrollbar.setScrollTop(999999)
  }
}

onMounted(async () => {
  await Promise.all([loadConversations(), loadMemory()])
})
onBeforeUnmount(() => {
  window.clearInterval(streamElapsedTimer)
  if (streamAbort) streamAbort()
})
</script>

<style scoped>
.ai-page {
  display: grid;
  grid-template-columns: 260px minmax(620px, 1fr) 420px;
  gap: 16px;
  height: calc(100vh - 112px);
  overflow: hidden;
}

.ai-sidebar,
.ai-workspace,
.log-panel {
  min-width: 0;
  border: 1px solid #dfe7f2;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.ai-sidebar {
  display: flex;
  flex-direction: column;
  padding: 16px;
  overflow: hidden;
}

.log-panel {
  display: flex;
  flex-direction: column;
  padding: 16px;
  overflow: hidden;
}

.ai-workspace {
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}

.panel-header,
.assistant-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-header {
  margin-bottom: 14px;
}

.panel-header h3,
.assistant-header h2 {
  margin: 0;
  color: #0f172a;
}

.panel-header h3 {
  font-size: 16px;
}

.panel-header p,
.assistant-header p {
  margin: 4px 0 0;
  color: #64748b;
}

.panel-header p {
  font-size: 12px;
}

.memory-card {
  flex-shrink: 0;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid #edf2f7;
}

.memory-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.memory-title h3 {
  margin: 0;
  font-size: 15px;
  color: #0f172a;
}

.memory-title p {
  margin: 3px 0 0;
  font-size: 12px;
  color: #64748b;
}

.memory-list {
  height: 220px;
  padding-right: 4px;
}

.memory-status-tabs {
  width: 100%;
  margin-bottom: 8px;
}

.memory-status-tabs :deep(.el-segmented__item) {
  min-width: 0;
  padding: 0 6px;
}

.memory-item {
  padding: 10px;
  margin-bottom: 8px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fbff;
}

.memory-item-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.memory-item strong {
  display: block;
  min-width: 0;
  font-size: 13px;
  color: #1e3a8a;
  overflow-wrap: anywhere;
}

.memory-item p {
  margin: 6px 0;
  font-size: 12px;
  line-height: 1.5;
  color: #475569;
  overflow-wrap: anywhere;
}

.memory-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  margin: 4px 0 6px;
  font-size: 11px;
  color: #64748b;
}

.memory-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
}

.assistant-header {
  padding: 18px 20px;
  border-bottom: 1px solid #e8eef7;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.assistant-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.assistant-mark {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  border-radius: 8px;
  color: #fff;
  font-weight: 700;
  background: #2563eb;
}

.assistant-badges {
  display: flex;
  gap: 8px;
}

.conversation-list {
  flex: 1;
  min-height: 0;
  height: auto;
}

.conversation-tools {
  display: grid;
  gap: 8px;
  margin-bottom: 10px;
}

.conversation-item {
  width: 100%;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  padding: 12px;
  text-align: left;
  cursor: pointer;
}

.conversation-main {
  min-width: 0;
}

.conversation-actions {
  display: flex;
  gap: 4px;
  margin-top: 8px;
}

.clear-conversations {
  margin-top: 8px;
  justify-content: flex-start;
}

.conversation-item + .conversation-item {
  margin-top: 8px;
}

.conversation-item.active,
.conversation-item:hover {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.conversation-title,
.conversation-time {
  display: block;
}

.conversation-title {
  color: #0f172a;
  font-weight: 600;
}

.conversation-time {
  margin-top: 6px;
  color: #94a3b8;
  font-size: 12px;
}

.chat-shell {
  flex: 1 1 auto;
  min-height: 0;
  padding: 16px;
  background: #f4f7fb;
}

.chat-stream {
  height: 100%;
  padding: 16px;
  border: 1px solid #e5edf7;
  border-radius: 8px;
  background: #f8fbff;
}

.welcome-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin-bottom: 16px;
  padding: 12px 14px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #eff6ff;
  color: #334155;
}

.welcome-strip span {
  color: #64748b;
}

.message-row {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.message-row.user {
  flex-direction: row-reverse;
}

.avatar {
  display: grid;
  flex: 0 0 34px;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 8px;
  background: #e0f2fe;
  color: #0369a1;
  font-size: 12px;
  font-weight: 700;
}

.message-row.user .avatar {
  background: #dbeafe;
  color: #1d4ed8;
}

.message-body {
  max-width: min(820px, 82%);
}

.message-meta {
  margin-bottom: 5px;
  color: #64748b;
  font-size: 12px;
}

.message-row.user .message-meta {
  text-align: right;
}

.message-body > p,
.markdown-body,
.thinking-card {
  margin: 0;
  padding: 12px 14px;
  border-radius: 8px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.message-row.assistant .markdown-body,
.thinking-card {
  border: 1px solid #e2e8f0;
  background: #fff;
  overflow: hidden;
  overflow-wrap: break-word;
  word-break: break-word;
}

.message-row.user .message-body > p {
  background: #2563eb;
  color: #fff;
}

.markdown-body {
  overflow-x: auto;
  overflow-wrap: break-word;
  word-break: break-word;
  color: #0f172a;
}

.markdown-body :deep(p) {
  margin: 0 0 10px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(strong) {
  color: #0f172a;
  font-weight: 700;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 8px 0 10px 20px;
  padding: 0;
}

.markdown-body :deep(li + li) {
  margin-top: 4px;
}

.markdown-body :deep(table) {
  width: 100%;
  margin: 10px 0 12px;
  border-collapse: collapse;
  font-size: 13px;
  white-space: normal;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 8px 10px;
  border: 1px solid #dbe4ef;
  text-align: left;
  vertical-align: top;
}

.markdown-body :deep(th) {
  background: #f1f5f9;
  color: #334155;
  font-weight: 700;
}

.markdown-body :deep(code) {
  padding: 2px 5px;
  border-radius: 4px;
  background: #f1f5f9;
  color: #0f172a;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}

.markdown-body :deep(pre) {
  overflow-x: auto;
  margin: 10px 0;
  padding: 12px;
  border-radius: 8px;
  background: #0f172a;
}

.markdown-body :deep(pre code) {
  padding: 0;
  background: transparent;
  color: #e2e8f0;
}

.markdown-body :deep(a) {
  color: #2563eb;
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

.typing-line,
.log-loading {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #334155;
  margin-bottom: 10px;
}

.elapsed-time {
  margin-left: 8px;
  padding: 0 6px;
  border-radius: 4px;
  background: #e0f2fe;
  color: #0369a1;
  font-size: 11px;
  font-weight: 600;
}

.typing-line i,
.log-loading i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #2563eb;
  animation: dotPulse 1.1s infinite ease-in-out;
}

.typing-line i:nth-child(3),
.log-loading i:nth-child(3) {
  animation-delay: 0.15s;
}

.typing-line i:nth-child(4),
.log-loading i:nth-child(4) {
  animation-delay: 0.3s;
}

/* 工具调用日志 */
.stream-tool-log {
  margin-top: 8px;
}

.stream-tool-item {
  display: grid;
  grid-template-columns: 16px auto minmax(0, 1fr);
  align-items: flex-start;
  gap: 6px;
  padding: 6px 8px;
  margin-bottom: 4px;
  border-radius: 6px;
  background: #f8fafc;
  font-size: 12px;
  line-height: 1.5;
  min-width: 0;
  overflow-wrap: break-word;
  word-break: break-word;
}

.stream-tool-item.running {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
}

.stream-tool-item.done {
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
}

.stream-tool-icon {
  flex: 0 0 16px;
  font-size: 12px;
}

.stream-tool-name {
  min-width: 0;
  max-width: 140px;
  padding: 1px 6px;
  border-radius: 4px;
  background: #dbeafe;
  color: #1d4ed8;
  font-weight: 600;
  overflow-wrap: break-word;
  word-break: break-word;
}

.stream-tool-target {
  min-width: 0;
  color: #64748b;
  overflow-wrap: break-word;
  word-break: break-word;
}

.stream-tool-result {
  grid-column: 2 / -1;
  min-width: 0;
  color: #64748b;
  font-size: 11px;
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-word;
}

/* 进度条 */
.stream-progress-bar {
  position: relative;
  height: 20px;
  margin-top: 10px;
  border-radius: 10px;
  background: #e2e8f0;
  overflow: hidden;
}

.stream-progress-fill {
  height: 100%;
  border-radius: 10px;
  background: linear-gradient(90deg, #3b82f6, #2563eb);
  transition: width 0.4s ease;
  min-width: 32px;
}

.stream-progress-text {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
}

.ai-meta {
  flex: 0 0 auto;
  max-height: min(360px, 36vh);
  overflow: hidden;
  padding: 0 16px;
  border-top: 1px solid #e8eef7;
  background: #fff;
}

.data-result-panel {
  max-height: min(300px, 30vh);
  overflow-y: auto;
  padding-right: 6px;
  padding: 12px 0;
  border-bottom: 1px solid #eef2f7;
}

.data-result-card {
  padding: 12px;
  border: 1px solid #dbe7f6;
  border-radius: 8px;
  background: #f8fbff;
  min-width: 0;
}

.data-result-card + .data-result-card {
  margin-top: 12px;
}

.data-result-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
  min-width: 0;
}

.data-result-head > div {
  min-width: 0;
}

.data-result-head strong {
  color: #0f172a;
}

.data-result-head p,
.data-preview-tip,
.data-drawer-summary {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.data-preview-table,
.data-full-table {
  width: 100%;
}

.data-preview-table :deep(.cell),
.data-full-table :deep(.cell) {
  white-space: nowrap;
}

.data-drawer-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.data-drawer-header span {
  color: #64748b;
  font-size: 13px;
}

.data-drawer-summary {
  margin: 0 0 12px;
}

.collapse-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.citation-item {
  padding: 10px 0;
  border-bottom: 1px solid #eef2f7;
}

.citation-item small,
.citation-item p {
  display: block;
  margin: 4px 0 0;
  color: #64748b;
}

.tool-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.composer {
  display: grid;
  grid-template-columns: 1fr 96px;
  gap: 12px;
  padding: 12px 16px 16px;
  border-top: 1px solid #e8eef7;
  background: #fff;
}

.log-form {
  border-bottom: 1px solid #eef2f7;
  margin-bottom: 12px;
  padding-bottom: 4px;
}

.log-body {
  flex: 1;
  min-height: 0;
}

.log-loading {
  justify-content: center;
  margin: 10px 0 16px;
  padding: 12px;
  border-radius: 8px;
  background: #eff6ff;
}

.analysis-result h4 {
  margin: 0 0 12px;
}

.analysis-result :deep(.el-alert) {
  margin-bottom: 10px;
}

.cost {
  margin-left: 8px;
  color: #64748b;
}

.error-text {
  color: #dc2626;
  word-break: break-word;
}

@keyframes dotPulse {
  0%,
  80%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

@media (max-width: 1360px) {
  .ai-page {
    grid-template-columns: 230px 1fr;
    height: auto;
    min-height: calc(100vh - 112px);
    overflow: visible;
  }

  .log-panel {
    grid-column: 1 / -1;
    max-height: 420px;
  }
}

@media (max-width: 900px) {
  .ai-page {
    grid-template-columns: 1fr;
    height: auto;
    min-height: calc(100vh - 112px);
    overflow: visible;
  }

  .ai-sidebar {
    max-height: 360px;
  }

  .log-panel {
    max-height: 420px;
  }

  .composer {
    grid-template-columns: 1fr;
  }

  .message-body {
    max-width: 86%;
  }
}

.message-feedback {
  display: flex;
  gap: 6px;
  margin-top: 6px;
}

.message-feedback button {
  background: transparent;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 2px 8px;
  cursor: pointer;
  font-size: 13px;
  opacity: 0.5;
  transition: all 0.2s;
}

.message-feedback button:hover {
  opacity: 1;
  border-color: #94a3b8;
}

.message-feedback button.active {
  opacity: 1;
  border-color: #2563eb;
  background: #eff6ff;
}
</style>
