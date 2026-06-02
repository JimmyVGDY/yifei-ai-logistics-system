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
        <el-alert
          v-if="captchaRequired"
          type="warning"
          :closable="false"
          show-icon
          class="login-conflict-alert"
          title="检测到异常登录设备，请完成图形验证码验证"
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
        <!-- 图形验证码：仅在检测到异常设备时显示 -->
        <el-form-item v-if="captchaRequired" label="验证码">
          <div class="captcha-row">
            <el-input
              v-model="form.captchaCode"
              placeholder="请输入图片中的算式结果"
              class="captcha-input"
              autocomplete="off"
            />
            <img
              :src="captchaImage"
              alt="验证码"
              class="captcha-image"
              title="点击刷新验证码"
              @click="refreshCaptcha"
            />
          </div>
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" class="login-button">
          {{ conflict.waiting ? '等待确认中' : captchaRequired ? '验证并登录' : '登录' }}
        </el-button>
      </el-form>
    </section>
  </main>
</template>

<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchCaptcha, fetchLoginConflictStatus, login } from '../api/auth'
import { firstMenuPath, saveAuthToken, markSessionChecked } from '../stores/auth-store'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
let conflictTimer = null
const form = reactive({
  username: 'admin',
  password: '',
  captchaId: '',
  captchaCode: ''
})
const conflict = reactive({
  waiting: false,
  conflictId: '',
  remainingSeconds: 0
})
// 验证码状态
const captchaRequired = ref(false)
const captchaImage = ref('')

async function handleLogin() {
  if (loading.value) {
    return
  }
  loading.value = true
  try {
    const payload = {
      username: form.username,
      password: form.password
    }
    // 如果需要验证码，携带 captchaId 和 captchaCode
    if (captchaRequired.value) {
      payload.captchaId = form.captchaId
      payload.captchaCode = form.captchaCode
    }
    const response = await login(payload)
    // 异常设备登录：后端要求图形验证码
    if (response?.captchaRequired) {
      captchaRequired.value = true
      captchaImage.value = response.captchaImage
      form.captchaId = response.captchaId
      form.captchaCode = ''
      ElMessage.warning(response.message || '检测到异常登录设备，请完成图形验证码验证')
      loading.value = false
      return
    }
    if (response?.loginStatus === 'PENDING') {
      // 清除验证码状态（可能在第一次提交时触发了验证码）
      captchaRequired.value = false
      startConflictPolling(response)
      return
    }
    saveAuthToken(response)
    markSessionChecked()
    ElMessage.success('登录成功')
    router.replace(route.query.redirect || firstMenuPath())
  } finally {
    if (!conflict.waiting) {
      loading.value = false
    }
  }
}

/** 刷新图形验证码 */
async function refreshCaptcha() {
  try {
    const response = await fetchCaptcha()
    if (response?.captchaId) {
      captchaImage.value = response.captchaImage
      form.captchaId = response.captchaId
      form.captchaCode = ''
    }
  } catch (e) {
    ElMessage.error('验证码刷新失败')
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
