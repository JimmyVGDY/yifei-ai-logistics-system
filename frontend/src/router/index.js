import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'
import OrdersView from '../views/OrdersView.vue'
import CreateOrderView from '../views/CreateOrderView.vue'
import ResourcesView from '../views/ResourcesView.vue'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', component: DashboardView, meta: { title: '运营看板' } },
  { path: '/orders', component: OrdersView, meta: { title: '运单管理' } },
  { path: '/orders/create', component: CreateOrderView, meta: { title: '新建运单' } },
  { path: '/resources', component: ResourcesView, meta: { title: '资源中心' } }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
