import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'
import ResourcesView from '../views/ResourcesView.vue'
import LoginView from '../views/LoginView.vue'
import ModuleListView from '../views/ModuleListView.vue'
import PermissionConfigView from '../views/PermissionConfigView.vue'
import { fetchSession } from '../api/auth'
import { canVisit, clearAuthToken, firstMenuPath, hasMenus, hasPermission, isAuthenticated, isSessionChecked, markSessionChecked, resetSessionChecked, saveAuthToken } from '../stores/auth-store'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/login', component: LoginView, meta: { title: '用户登录', public: true } },
  { path: '/dashboard', component: DashboardView, meta: { title: '运营看板', permission: 'dashboard:view' } },
  { path: '/orders', component: ModuleListView, meta: { title: '运单管理', module: 'orders', permission: 'order:query', businessCreate: true } },
  { path: '/customers', component: ModuleListView, meta: { title: '客户管理', module: 'customers', permission: 'customer:query' } },
  { path: '/waybills', component: ModuleListView, meta: { title: '运单中心', module: 'waybills', permission: 'waybill:query' } },
  { path: '/dispatches', component: ModuleListView, meta: { title: '调度管理', module: 'dispatches', permission: 'dispatch:query' } },
  { path: '/tasks', component: ModuleListView, meta: { title: '运输任务', module: 'tasks', permission: 'task:query' } },
  { path: '/tracks', component: ModuleListView, meta: { title: '物流轨迹', module: 'tracks', permission: 'track:view' } },
  { path: '/drivers', component: ModuleListView, meta: { title: '司机管理', module: 'drivers', permission: 'driver:query' } },
  { path: '/vehicles', component: ModuleListView, meta: { title: '车辆管理', module: 'vehicles', permission: 'vehicle:query' } },
  { path: '/exceptions', component: ModuleListView, meta: { title: '异常管理', module: 'exceptions', permission: 'exception:query' } },
  { path: '/fees', component: ModuleListView, meta: { title: '费用结算', module: 'fees', permission: 'fee:query' } },
  { path: '/system/users', component: ModuleListView, meta: { title: '用户管理', module: 'users', permission: 'system:user:query' } },
  { path: '/system/roles', component: ModuleListView, meta: { title: '角色管理', module: 'roles', permission: 'system:role:query' } },
  { path: '/system/permissions', component: PermissionConfigView, meta: { title: '权限配置', permission: 'system:permission:manage' } },
  { path: '/system/operation-logs', component: ModuleListView, meta: { title: '操作日志', module: 'operationLogs', permission: 'system:log:view' } },
  { path: '/files', component: ModuleListView, meta: { title: '上传文件', module: 'files', permission: 'file:query' } },
  { path: '/resources', component: ResourcesView, meta: { title: '资源中心', permission: 'resource:view' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  if (to.meta.public) {
    return true
  }
  if (!isAuthenticated()) {
    resetSessionChecked()
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (!isSessionChecked() || !hasMenus()) {
    try {
      saveAuthToken(await fetchSession())
      markSessionChecked()
    } catch (error) {
      clearAuthToken()
      resetSessionChecked()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  if (!canVisit(to.path) || !hasPermission(to.meta.permission)) {
    const target = firstMenuPath()
    return target === to.path ? { path: '/login' } : target
  }
  return true
})

export default router
