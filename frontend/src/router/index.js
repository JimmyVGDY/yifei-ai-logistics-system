import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'
import ResourcesView from '../views/ResourcesView.vue'
import LoginView from '../views/LoginView.vue'
import ModuleListView from '../views/ModuleListView.vue'
import PermissionConfigView from '../views/PermissionConfigView.vue'
import StructuredLogsView from '../views/StructuredLogsView.vue'
import { canVisit, firstMenuPath, isAuthenticated } from '../stores/auth-store'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/login', component: LoginView, meta: { title: '用户登录', public: true } },
  { path: '/dashboard', component: DashboardView, meta: { title: '运营看板', permission: 'dashboard:view' } },
  { path: '/orders', component: ModuleListView, meta: { title: '运单管理', module: 'orders', permission: 'order:manage', businessCreate: true } },
  { path: '/customers', component: ModuleListView, meta: { title: '客户管理', module: 'customers', permission: 'customer:manage' } },
  { path: '/waybills', component: ModuleListView, meta: { title: '运单中心', module: 'waybills', permission: 'waybill:manage' } },
  { path: '/dispatches', component: ModuleListView, meta: { title: '调度管理', module: 'dispatches', permission: 'dispatch:manage' } },
  { path: '/tasks', component: ModuleListView, meta: { title: '运输任务', module: 'tasks', permission: 'task:manage' } },
  { path: '/tracks', component: ModuleListView, meta: { title: '物流轨迹', module: 'tracks', permission: 'track:view' } },
  { path: '/drivers', component: ModuleListView, meta: { title: '司机管理', module: 'drivers', permission: 'driver:manage' } },
  { path: '/vehicles', component: ModuleListView, meta: { title: '车辆管理', module: 'vehicles', permission: 'vehicle:manage' } },
  { path: '/exceptions', component: ModuleListView, meta: { title: '异常管理', module: 'exceptions', permission: 'exception:manage' } },
  { path: '/fees', component: ModuleListView, meta: { title: '费用结算', module: 'fees', permission: 'fee:manage' } },
  { path: '/system/users', component: ModuleListView, meta: { title: '用户管理', module: 'users', permission: 'system:user:manage' } },
  { path: '/system/roles', component: ModuleListView, meta: { title: '角色管理', module: 'roles', permission: 'system:role:manage' } },
  { path: '/system/permissions', component: PermissionConfigView, meta: { title: '权限配置', permission: 'system:permission:manage' } },
  { path: '/system/operation-logs', component: ModuleListView, meta: { title: '操作日志', module: 'operationLogs', permission: 'system:log:view' } },
  { path: '/system/structured-logs', component: StructuredLogsView, meta: { title: '结构化日志', permission: 'system:log:view' } },
  { path: '/files', component: ModuleListView, meta: { title: '上传文件', module: 'files', permission: 'file:manage' } },
  { path: '/resources', component: ResourcesView, meta: { title: '资源中心', permission: 'resource:view' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.meta.public) {
    return true
  }
  if (!isAuthenticated()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (!canVisit(to.path)) {
    return firstMenuPath()
  }
  return true
})

export default router
