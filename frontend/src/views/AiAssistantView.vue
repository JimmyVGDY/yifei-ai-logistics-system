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

      <el-scrollbar class="conversation-list">
        <button
          v-for="item in conversations"
          :key="item.conversationId"
          class="conversation-item"
          :class="{ active: item.conversationId === conversationId }"
          @click="selectConversation(item.conversationId)"
        >
          <span class="conversation-title">{{ item.title || '新会话' }}</span>
          <span class="conversation-time">{{ item.updatedAt || item.createdAt }}</span>
        </button>
        <el-empty v-if="!conversations.length" :image-size="72" description="暂无会话" />
      </el-scrollbar>
      <div class="memory-card">
        <div class="memory-title">
          <div>
            <h3>长期记忆</h3>
            <p>{{ memoryProfile?.memoryCount || 0 }} 条偏好</p>
          </div>
          <el-switch v-model="memoryEnabled" size="small" :loading="memoryLoading" @change="toggleMemory" />
        </div>
        <el-scrollbar class="memory-list">
          <div v-for="item in memoryItems" :key="item.id" class="memory-item">
            <strong>{{ item.memoryTitle }}</strong>
            <p>{{ item.memorySummary }}</p>
            <el-button link type="danger" size="small" @click="removeMemory(item.id)">删除</el-button>
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
                  <div class="stream-progress-fill" :style="{ width: (streamStepIndex / 8 * 100) + '%' }"></div>
                  <span class="stream-progress-text">{{ streamStepIndex }}/8 工具调用</span>
                </div>
              </div>
            </div>
          </div>
        </el-scrollbar>
      </div>

      <div class="ai-meta" v-if="lastResponse || chatLoading">
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

    <aside class="log-panel">
      <div class="panel-header">
        <div>
          <h3>日志排障</h3>
          <p>按链路标识定位异常</p>
        </div>
        <el-button type="primary" size="small" :icon="Search" :loading="logLoading" @click="analyzeLogs">分析</el-button>
      </div>

      <el-scrollbar class="log-body">
      <el-form :model="logForm" label-width="82px" class="log-form">
        <el-form-item label="Trace ID">
          <el-input v-model="logForm.traceId" clearable />
        </el-form-item>
        <el-form-item label="操作ID">
          <el-input v-model="logForm.operationId" clearable />
        </el-form-item>
        <el-form-item label="会话ID">
          <el-input v-model="logForm.loginSessionId" clearable />
        </el-form-item>
        <el-form-item label="用户编号">
          <el-input v-model="logForm.userId" clearable />
        </el-form-item>
        <el-form-item label="请求地址">
          <el-input v-model="logForm.uri" clearable />
        </el-form-item>
      </el-form>

      <div v-if="logLoading" class="log-loading">
        <span>正在分析日志链路</span>
        <i></i>
        <i></i>
        <i></i>
      </div>

      <div v-if="logResult" class="analysis-result">
        <h4>{{ logResult.summary }}</h4>
        <el-alert
          v-for="risk in logResult.riskPoints"
          :key="risk"
          :title="risk"
          type="warning"
          show-icon
          :closable="false"
        />
        <el-timeline>
          <el-timeline-item v-for="(item, index) in logResult.timeline" :key="index" :timestamp="item.time">
            <strong>{{ item.operation || item.uri }}</strong>
            <p>{{ item.method }} {{ item.uri }}</p>
            <el-tag size="small" :type="item.status === 'SUCCESS' ? 'success' : 'danger'">{{ item.status || '-' }}</el-tag>
            <span class="cost">{{ item.costMs }}ms</span>
            <p v-if="item.errorMessage" class="error-text">{{ item.errorMessage }}</p>
          </el-timeline-item>
        </el-timeline>
      </div>
      </el-scrollbar>
    </aside>
  </section>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Promotion, Refresh, Search } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import {
  analyzeAiLogs,
  chatWithAiStream,
  clearAiMemories,
  deleteAiMemoryItem,
  fetchAiConversation,
  fetchAiConversations,
  fetchAiMemoryItems,
  fetchAiMemoryProfile,
  updateAiMemorySettings
} from '../api/ai-assistant'

const conversations = ref([])
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
const chatScrollbarRef = ref(null)

/** SSE 流式进度状态 */
const streamProgress = ref(null)
const streamStepIndex = ref(0)
const streamElapsed = ref(0)
const streamToolLog = ref([])
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
  conversations.value = await fetchAiConversations()
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
  messages.value.push({ role: 'user', content })
  message.value = ''
  lastResponse.value = null

  // 初始化流式进度
  chatLoading.value = true
  streamProgress.value = 'thinking'
  streamStepIndex.value = 0
  streamElapsed.value = 0
  streamToolLog.value = []
  startElapsedTimer()
  await scrollToBottom()

  const stream = chatWithAiStream({
    message: content,
    conversationId: conversationId.value,
    pageContext: window.location.pathname,
    onEvent: handleStreamEvent
  })
  streamAbort = stream.abort

  try {
    const response = await stream.promise
    conversationId.value = response.conversationId
    lastResponse.value = response
    messages.value.push({ role: 'assistant', content: response.answer })
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

function handleStreamEvent(event) {
  switch (event.type) {
    case 'thinking':
      streamProgress.value = 'thinking'
      streamStepIndex.value = 1
      break
    case 'tool_start':
      streamProgress.value = 'calling'
      streamStepIndex.value = Math.min((event.toolCallCount || 0) + 1, 8)
      streamToolLog.value.push({
        name: event.toolName || '查询',
        target: event.target || '',
        result: '',
        status: 'running'
      })
      break
    case 'tool_result':
      streamStepIndex.value = Math.min((event.toolCallCount || 0) + 1, 8)
      // 更新最近一条 running 的工具日志
      for (let i = streamToolLog.value.length - 1; i >= 0; i--) {
        if (streamToolLog.value[i].status === 'running') {
          streamToolLog.value[i].result = event.result || ''
          streamToolLog.value[i].status = 'done'
          break
        }
      }
      break
    case 'done':
      streamProgress.value = 'done'
      streamElapsed.value = event.elapsedMs || 0
      break
    case 'error':
      streamProgress.value = 'error'
      streamElapsed.value = event.elapsedMs || 0
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
  } finally {
    logLoading.value = false
  }
}

async function loadMemory() {
  memoryLoading.value = true
  try {
    const [profile, page] = await Promise.all([
      fetchAiMemoryProfile(),
      fetchAiMemoryItems({ page: 1, pageSize: 5 })
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

function startThinking() {
  chatLoading.value = true
  thinkingStepIndex.value = 0
  window.clearInterval(thinkingTimer)
  thinkingTimer = window.setInterval(() => {
    thinkingStepIndex.value = Math.min(thinkingStepIndex.value + 1, thinkingSteps.length - 1)
  }, 900)
}

function stopThinking() {
  chatLoading.value = false
  window.clearInterval(thinkingTimer)
  thinkingTimer = null
}

function renderMarkdown(content) {
  const html = markdown.render(content || '')
  return DOMPurify.sanitize(html, {
    USE_PROFILES: { html: true },
    ADD_ATTR: ['target', 'rel']
  })
}

function formatElapsed(ms) {
  if (ms < 1000) return (ms / 1000).toFixed(1) + 's'
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

.memory-item {
  padding: 10px;
  margin-bottom: 8px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fbff;
}

.memory-item strong {
  display: block;
  font-size: 13px;
  color: #1e3a8a;
}

.memory-item p {
  margin: 6px 0;
  font-size: 12px;
  line-height: 1.5;
  color: #475569;
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

.conversation-item {
  width: 100%;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  padding: 12px;
  text-align: left;
  cursor: pointer;
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
  flex: 1;
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
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 6px 8px;
  margin-bottom: 4px;
  border-radius: 6px;
  background: #f8fafc;
  font-size: 12px;
  line-height: 1.5;
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
  flex: 0 0 auto;
  padding: 1px 6px;
  border-radius: 4px;
  background: #dbeafe;
  color: #1d4ed8;
  font-weight: 600;
  white-space: nowrap;
}

.stream-tool-target {
  flex: 1 1 auto;
  min-width: 0;
  color: #64748b;
  overflow-wrap: break-word;
  word-break: break-word;
}

.stream-tool-result {
  flex: 0 0 auto;
  color: #64748b;
  font-size: 11px;
  white-space: nowrap;
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
  padding: 0 16px;
  background: #fff;
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
</style>
