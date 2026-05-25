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
  if (to.meta.public) {
    return true
  }
  if (!isAuthenticated()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
