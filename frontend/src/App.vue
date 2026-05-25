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
        <el-menu-item index="/dashboard">
          <el-icon><DataLine /></el-icon>
          <span>运营看板</span>
        </el-menu-item>
        <el-menu-item index="/orders">
          <el-icon><Tickets /></el-icon>
          <span>运单管理</span>
        </el-menu-item>
        <el-menu-item index="/orders/create">
          <el-icon><Plus /></el-icon>
          <span>新建运单</span>
        </el-menu-item>
        <el-menu-item index="/resources">
          <el-icon><SetUp /></el-icon>
          <span>资源中心</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <div>
          <span class="eyebrow">物流业务后台</span>
          <h2>{{ $route.meta.title }}</h2>
        </div>
        <div class="topbar-actions">
          <el-tag type="success" effect="light">{{ username }}</el-tag>
          <el-button :icon="SwitchButton" @click="handleLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { SwitchButton } from '@element-plus/icons-vue'
import { logout } from './api/auth'
import { clearAuthToken, getAuthToken } from './stores/auth-store'

const router = useRouter()
const username = computed(() => getAuthToken().username || 'admin')

async function handleLogout() {
  try {
    await logout()
  } finally {
    clearAuthToken()
    ElMessage.success('已退出登录')
    router.replace('/login')
  }
}
</script>
