<template>
  <main class="login-page">
    <section class="login-panel">
      <div class="login-brand">
        <div class="brand-mark">L</div>
        <div>
          <h1>物流管理系统</h1>
          <p>后台账号登录</p>
        </div>
      </div>

      <el-form :model="form" label-position="top" @submit.prevent="handleLogin">
        <el-alert
          v-if="conflict.waiting"
          type="warning"
          :closable="false"
          show-icon
          class="login-conflict-alert"
          :title="`该账号已在其他地方登录，正在等待原会话确认，剩余 ${conflict.remainingSeconds} 秒`"
        />
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="admin" autocomplete="username">
            <template #prefix>
              <el-icon><User /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入登录密码"
            autocomplete="current-password"
            show-password
          >
            <template #prefix>
              <el-icon><Lock /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" class="login-button">
          {{ conflict.waiting ? '等待确认中' : '登录' }}
        </el-button>
      </el-form>
    </section>
  </main>
</template>

<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchLoginConflictStatus, login } from '../api/auth'
import { firstMenuPath, saveAuthToken, markSessionChecked } from '../stores/auth-store'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
let conflictTimer = null
const form = reactive({
  username: 'admin',
  password: ''
})
const conflict = reactive({
  waiting: false,
  conflictId: '',
  remainingSeconds: 0
})

async function handleLogin() {
  if (loading.value) {
    return
  }
  loading.value = true
  try {
    const response = await login(form)
    if (response?.loginStatus === 'PENDING') {
      startConflictPolling(response)
      return
    }
    saveAuthToken(response)
    markSessionChecked()
    ElMessage.success('登录成功')
    // 硬跳转确保绕过路由守卫的任何残留状态
    window.location.href = route.query.redirect || firstMenuPath()
  } finally {
    if (!conflict.waiting) {
      loading.value = false
    }
  }
}

function startConflictPolling(response) {
  conflict.waiting = true
  conflict.conflictId = response.conflictId
  conflict.remainingSeconds = response.remainingSeconds || 60
  ElMessage.warning(response.message || '该账号已在其他地方登录，正在等待原会话确认')
  clearConflictTimer()
  conflictTimer = setInterval(checkConflictStatus, 2000)
}

async function checkConflictStatus() {
  const response = await fetchLoginConflictStatus(conflict.conflictId)
  conflict.remainingSeconds = response.remainingSeconds || 0
  if (response.loginStatus === 'PENDING') {
    return
  }
  clearConflictTimer()
  loading.value = false
  conflict.waiting = false
  if (response.loginStatus === 'TAKEN_OVER' && response.loginResponse) {
    saveAuthToken(response.loginResponse)
    ElMessage.success('原会话未拒绝，新登录已生效')
    router.replace(route.query.redirect || firstMenuPath())
    return
  }
  ElMessage.warning(response.message || '原会话已拒绝新的登录请求')
}

function clearConflictTimer() {
  if (conflictTimer) {
    clearInterval(conflictTimer)
    conflictTimer = null
  }
}

onBeforeUnmount(clearConflictTimer)
</script>
