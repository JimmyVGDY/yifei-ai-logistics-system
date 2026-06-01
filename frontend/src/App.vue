<template>
  <router-view v-if="$route.meta.public" />
  <el-container v-else class="app-shell">
    <el-aside width="248px" class="sidebar">
      <div class="brand">
        <div class="brand-mark">L</div>
        <div>
          <h1>物流管理系统</h1>
          <p>Logistics Admin</p>
        </div>
      </div>
      <el-menu router :default-active="$route.path" class="nav-menu">
        <template v-for="menu in menus" :key="menu.id || menu.path">
          <el-sub-menu v-if="menu.children?.length" :index="menu.path">
            <template #title>
              <el-icon><Setting /></el-icon>
              <span>{{ menu.name }}</span>
            </template>
            <el-menu-item v-for="child in menu.children" :key="child.path" :index="child.path">
              {{ child.name }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="menu.path">
            <el-icon><component :is="iconName(menu.path)" /></el-icon>
            <span>{{ menu.name }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <div>
          <span class="eyebrow">物流业务后台</span>
          <h2>{{ $route.meta.title }}</h2>
        </div>
        <div class="topbar-actions">
          <el-tag type="success" effect="light">{{ roleName }}</el-tag>
          <el-tag effect="light">{{ username }}</el-tag>
          <el-button :icon="Setting" @click="profileVisible = true">设置</el-button>
          <el-button :icon="SwitchButton" @click="handleLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>

    <!-- 个人设置弹窗 -->
    <el-dialog v-model="profileVisible" title="个人设置" width="480px" append-to-body>
      <el-tabs v-model="settingTab">
        <el-tab-pane label="修改资料" name="profile">
          <el-form :model="profileForm" label-width="80px">
            <el-form-item label="姓名">
              <el-input v-model="profileForm.realName" placeholder="真实姓名" />
            </el-form-item>
            <el-form-item label="手机">
              <el-input v-model="profileForm.mobile" placeholder="手机号" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="profileForm.email" placeholder="邮箱" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleProfileSave" :loading="saving">保存</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="修改密码" name="password">
          <el-form :model="passwordForm" label-width="80px">
            <el-form-item label="原密码">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="至少6位" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handlePasswordSave" :loading="saving">修改密码</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { computed, h, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { SwitchButton, Setting } from '@element-plus/icons-vue'
import { fetchCurrentLoginConflict, logout, rejectLoginConflict, acceptLoginConflict, updateProfile, changePassword } from './api/auth'
import { clearAuthToken, getAuthToken, isAuthenticated } from './stores/auth-store'

const router = useRouter()
const auth = getAuthToken()
const username = computed(() => auth.username || '未登录')
const roleName = computed(() => auth.roleName || '未分配角色')
const menus = computed(() => auth.menus || [])
let conflictPollTimer = null
let conflictDialogVisible = false
let conflictAutoCloseTimer = null

function iconName(path) {
  const icons = {
    '/dashboard': 'DataLine',
    '/orders': 'Tickets',
    '/customers': 'User',
    '/waybills': 'Document',
    '/dispatches': 'Guide',
    '/tasks': 'Van',
    '/tracks': 'Location',
    '/drivers': 'Avatar',
    '/vehicles': 'Ship',
    '/exceptions': 'Warning',
    '/fees': 'Money',
    '/system/permissions': 'Lock',
    '/files': 'Upload',
    '/resources': 'SetUp'
  }
  return icons[path] || 'Menu'
}

async function handleLogout() {
  try {
    await logout()
  } finally {
    clearAuthToken()
    ElMessage.success('已退出登录')
    router.replace('/login')
  }
}

async function pollLoginConflict() {
  if (!isAuthenticated() || conflictDialogVisible) {
    return
  }
  const conflict = await fetchCurrentLoginConflict()
  if (conflict?.loginStatus === 'PENDING') {
    showLoginConflictDialog(conflict)
  }
}

function showLoginConflictDialog(conflict) {
  conflictDialogVisible = true
  const remainingSeconds = conflict.remainingSeconds || 60
  conflictAutoCloseTimer = setTimeout(() => {
    ElMessageBox.close()
    conflictDialogVisible = false
  }, remainingSeconds * 1000)

  ElMessageBox.confirm(
    h('div', null, [
      h('p', null, '检测到同一账号正在其他地方登录。'),
      h('p', null, `如果你要保留当前会话，请在 ${remainingSeconds} 秒内点击“保持当前登录”。`),
      h('p', null, '如果不处理，倒计时结束后新登录会生效，当前会话会被下线。')
    ]),
    '登录冲突提醒',
    {
      confirmButtonText: '保持当前登录',
      cancelButtonText: '允许新登录',
      distinguishCancelAndClose: true,
      closeOnClickModal: false,
      closeOnPressEscape: false,
      type: 'warning'
    }
  ).then(async () => {
    await rejectLoginConflict(conflict.conflictId)
    ElMessage.success('已拒绝新的登录请求，当前会话继续保留')
  }).catch(async (action) => {
    if (action === 'cancel') {
      await acceptLoginConflict(conflict.conflictId)
      ElMessage.success('已允许新的登录请求，当前会话稍后会下线')
    }
  }).finally(() => {
    clearTimeout(conflictAutoCloseTimer)
    conflictAutoCloseTimer = null
    conflictDialogVisible = false
  })
}

 
// 个人设置
const profileVisible = ref(false)
const settingTab = ref('profile')
const saving = ref(false)
const profileForm = reactive({ realName: '', mobile: '', email: '' })
const passwordForm = reactive({ oldPassword: '', newPassword: '' })

async function handleProfileSave() {
  saving.value = true
  try {
    await updateProfile({ ...profileForm })
    ElMessage.success('个人信息已更新')
    profileVisible.value = false
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handlePasswordSave() {
  saving.value = true
  try {
    await changePassword({ ...passwordForm })
    ElMessage.success('密码已修改，请重新登录')
    profileVisible.value = false
    clearAuthToken()
    router.push('/login')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '修改失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  conflictPollTimer = setInterval(pollLoginConflict, 5000)
})

onBeforeUnmount(() => {
  clearInterval(conflictPollTimer)
  clearTimeout(conflictAutoCloseTimer)
})
</script>
