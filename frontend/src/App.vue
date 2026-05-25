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
        <el-menu-item index="/customers">
          <el-icon><User /></el-icon>
          <span>客户管理</span>
        </el-menu-item>
        <el-menu-item index="/waybills">
          <el-icon><Document /></el-icon>
          <span>运单中心</span>
        </el-menu-item>
        <el-menu-item index="/dispatches">
          <el-icon><Guide /></el-icon>
          <span>调度管理</span>
        </el-menu-item>
        <el-menu-item index="/tasks">
          <el-icon><Van /></el-icon>
          <span>运输任务</span>
        </el-menu-item>
        <el-menu-item index="/tracks">
          <el-icon><Location /></el-icon>
          <span>物流轨迹</span>
        </el-menu-item>
        <el-menu-item index="/drivers">
          <el-icon><Avatar /></el-icon>
          <span>司机管理</span>
        </el-menu-item>
        <el-menu-item index="/vehicles">
          <el-icon><Ship /></el-icon>
          <span>车辆管理</span>
        </el-menu-item>
        <el-menu-item index="/exceptions">
          <el-icon><Warning /></el-icon>
          <span>异常管理</span>
        </el-menu-item>
        <el-menu-item index="/fees">
          <el-icon><Money /></el-icon>
          <span>费用结算</span>
        </el-menu-item>
        <el-sub-menu index="/system">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/system/users">用户管理</el-menu-item>
          <el-menu-item index="/system/roles">角色管理</el-menu-item>
          <el-menu-item index="/system/operation-logs">操作日志</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/files">
          <el-icon><Upload /></el-icon>
          <span>上传文件</span>
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
