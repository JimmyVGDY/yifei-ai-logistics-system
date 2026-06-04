<template>
  <section class="ai-page">
    <aside class="ai-sidebar">
      <div class="panel-header">
        <h3>会话</h3>
        <el-button size="small" :icon="Refresh" @click="loadConversations" />
      </div>
      <el-scrollbar height="640px">
        <button
          v-for="item in conversations"
          :key="item.conversationId"
          class="conversation-item"
          :class="{ active: item.conversationId === conversationId }"
          @click="selectConversation(item.conversationId)"
        >
          <strong>{{ item.title || '新会话' }}</strong>
          <span>{{ item.updatedAt || item.createdAt }}</span>
        </button>
      </el-scrollbar>
    </aside>

    <main class="ai-chat-panel">
      <div class="panel-header">
        <div>
          <h3>AI助手</h3>
          <p>只读问答、日志排障和系统文档检索</p>
        </div>
        <el-tag type="success" effect="light">只读</el-tag>
      </div>

      <el-scrollbar class="chat-stream" height="480px">
        <div v-for="(item, index) in messages" :key="index" class="chat-message" :class="item.role">
          <span>{{ item.role === 'user' ? '我' : 'AI' }}</span>
          <p>{{ item.content }}</p>
        </div>
      </el-scrollbar>

      <div v-if="lastResponse" class="ai-meta">
        <el-collapse>
          <el-collapse-item title="引用来源" name="citations">
            <el-empty v-if="!lastResponse.citations?.length" description="暂无引用" />
            <div v-for="item in lastResponse.citations" :key="item.reference" class="citation-item">
              <strong>{{ item.title }}</strong>
              <small>{{ item.reference }}</small>
              <p>{{ item.snippet }}</p>
            </div>
          </el-collapse-item>
          <el-collapse-item title="工具调用" name="tools">
            <el-empty v-if="!lastResponse.toolCalls?.length" description="暂无工具调用" />
            <el-tag v-for="item in lastResponse.toolCalls" :key="item.toolName + item.target" class="tool-tag">
              {{ item.toolName }} · {{ item.result }}
            </el-tag>
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
          @keydown.ctrl.enter.prevent="sendMessage"
        />
        <el-button type="primary" :icon="Promotion" :loading="chatLoading" @click="sendMessage">发送</el-button>
      </div>
    </main>

    <aside class="log-panel">
      <div class="panel-header">
        <h3>日志排障</h3>
        <el-button type="primary" size="small" :icon="Search" :loading="logLoading" @click="analyzeLogs">分析</el-button>
      </div>
      <el-form :model="logForm" label-width="96px" class="log-form">
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

      <div v-if="logResult" class="analysis-result">
        <h4>{{ logResult.summary }}</h4>
        <el-timeline>
          <el-timeline-item v-for="(item, index) in logResult.timeline" :key="index" :timestamp="item.time">
            <strong>{{ item.operation || item.uri }}</strong>
            <p>{{ item.method }} {{ item.uri }}</p>
            <el-tag size="small" :type="item.status === 'SUCCESS' ? 'success' : 'danger'">{{ item.status || '-' }}</el-tag>
            <span class="cost">{{ item.costMs }}ms</span>
            <p v-if="item.errorMessage" class="error-text">{{ item.errorMessage }}</p>
          </el-timeline-item>
        </el-timeline>
        <el-alert v-for="risk in logResult.riskPoints" :key="risk" :title="risk" type="warning" show-icon :closable="false" />
      </div>
    </aside>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion, Refresh, Search } from '@element-plus/icons-vue'
import { analyzeAiLogs, chatWithAi, fetchAiConversation, fetchAiConversations } from '../api/ai-assistant'

const conversations = ref([])
const conversationId = ref('')
const messages = ref([])
const message = ref('')
const chatLoading = ref(false)
const logLoading = ref(false)
const lastResponse = ref(null)
const logResult = ref(null)
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
}

async function sendMessage() {
  if (!message.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  const content = message.value.trim()
  messages.value.push({ role: 'user', content })
  message.value = ''
  chatLoading.value = true
  try {
    const response = await chatWithAi({
      message: content,
      conversationId: conversationId.value,
      pageContext: window.location.pathname
    })
    conversationId.value = response.conversationId
    lastResponse.value = response
    messages.value.push({ role: 'assistant', content: response.answer })
    await loadConversations()
  } finally {
    chatLoading.value = false
  }
}

async function analyzeLogs() {
  logLoading.value = true
  try {
    logResult.value = await analyzeAiLogs({ ...logForm })
  } finally {
    logLoading.value = false
  }
}

onMounted(loadConversations)
</script>

<style scoped>
.ai-page {
  display: grid;
  grid-template-columns: 260px minmax(520px, 1fr) 420px;
  gap: 16px;
  min-height: 720px;
}

.ai-sidebar,
.ai-chat-panel,
.log-panel {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 16px;
  min-width: 0;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-header h3 {
  margin: 0;
  font-size: 16px;
}

.panel-header p {
  margin: 4px 0 0;
  color: #64748b;
}

.conversation-item {
  width: 100%;
  border: 0;
  background: transparent;
  border-bottom: 1px solid #eef2f7;
  padding: 12px 8px;
  text-align: left;
  cursor: pointer;
}

.conversation-item.active,
.conversation-item:hover {
  background: #f1f5f9;
}

.conversation-item strong,
.conversation-item span {
  display: block;
}

.conversation-item span {
  margin-top: 4px;
  color: #94a3b8;
  font-size: 12px;
}

.chat-stream {
  border: 1px solid #eef2f7;
  border-radius: 6px;
  padding: 12px;
  background: #f8fafc;
}

.chat-message {
  margin-bottom: 12px;
  max-width: 88%;
}

.chat-message span {
  display: inline-block;
  margin-bottom: 4px;
  color: #64748b;
  font-size: 12px;
}

.chat-message p {
  margin: 0;
  padding: 10px 12px;
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-message.user {
  margin-left: auto;
}

.chat-message.user p {
  background: #dbeafe;
}

.chat-message.assistant p {
  background: #fff;
  border: 1px solid #e5e7eb;
}

.ai-meta {
  margin-top: 12px;
}

.citation-item {
  padding: 8px 0;
  border-bottom: 1px solid #eef2f7;
}

.citation-item small,
.citation-item p {
  display: block;
  margin: 4px 0 0;
  color: #64748b;
}

.tool-tag {
  margin: 0 8px 8px 0;
}

.composer {
  display: grid;
  grid-template-columns: 1fr 92px;
  gap: 12px;
  margin-top: 12px;
}

.log-form {
  border-bottom: 1px solid #eef2f7;
  margin-bottom: 12px;
  padding-bottom: 4px;
}

.analysis-result h4 {
  margin: 0 0 12px;
}

.cost {
  margin-left: 8px;
  color: #64748b;
}

.error-text {
  color: #dc2626;
  word-break: break-word;
}

@media (max-width: 1280px) {
  .ai-page {
    grid-template-columns: 220px 1fr;
  }

  .log-panel {
    grid-column: 1 / -1;
  }
}
</style>
