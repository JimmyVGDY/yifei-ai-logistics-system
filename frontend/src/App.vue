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
const auth = getAuthToken()
const username = computed(() => auth.username || '未登录')
const roleName = computed(() => auth.roleName || '未分配角色')
const menus = computed(() => auth.menus || [])

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
</script>
