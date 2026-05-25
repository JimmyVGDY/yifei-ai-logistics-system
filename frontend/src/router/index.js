import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'
import OrdersView from '../views/OrdersView.vue'
import CreateOrderView from '../views/CreateOrderView.vue'
import ResourcesView from '../views/ResourcesView.vue'
import LoginView from '../views/LoginView.vue'
import { isAuthenticated } from '../stores/auth-store'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/login', component: LoginView, meta: { title: '管理员登录', public: true } },
  { path: '/dashboard', component: DashboardView, meta: { title: '运营看板' } },
  { path: '/orders', component: OrdersView, meta: { title: '运单管理' } },
  { path: '/orders/create', component: CreateOrderView, meta: { title: '新建运单' } },
  { path: '/resources', component: ResourcesView, meta: { title: '资源中心' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  // 登录页等公开路由不做 token 校验，避免未登录时出现跳转循环。
  if (to.meta.public) {
    return true
  }
  if (!isAuthenticated()) {
    // 记录原目标地址，登录成功后可以回到用户最初想访问的页面。
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
