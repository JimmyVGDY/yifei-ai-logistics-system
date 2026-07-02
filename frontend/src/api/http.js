import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clearAuthToken, getAuthToken } from '../stores/auth-store.js'

const env = import.meta.env || globalThis.__VITE_ENV__ || {}
const http = axios.create({
  baseURL: env.VITE_API_BASE || '/api',
  timeout: 10000
})

http.interceptors.request.use((config) => {
  // 登录接口不能带旧 token，避免已失效会话影响重新登录。
  const { tokenName, tokenValue } = getAuthToken()
  const isLoginRequest = config.url === '/auth/login'
  if (!isLoginRequest && tokenName && tokenValue) {
    config.headers[tokenName] = tokenValue
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      // 文件下载直接返回二进制，不走 ApiResponse 包装拆解。
      return response.data
    }
    if (response.data && typeof response.data === 'object' && 'code' in response.data && 'data' in response.data) {
      if (response.data.code !== 200) {
        // 后端业务异常统一转成 rejected promise，让页面保持一种错误处理方式。
        ElMessage.error(response.data.message || '请求失败')
        return Promise.reject(new Error(response.data.message || '业务异常'))
      }
      return response.data.data
    }
    return response.data
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '请求失败'
    if (error.response?.status === 401) {
      // 认证失效时清理本地会话，并带 redirect 回登录页。
      clearAuthToken()
      if (window.location.pathname !== '/login') {
        const redirect = encodeURIComponent(window.location.pathname + window.location.search)
        window.location.href = `/login?redirect=${redirect}`
      }
      ElMessage.error(message)
      return Promise.reject(error)
    }
    if (error.response?.status === 403) {
      // 403 不清 token，通常是当前账号无权限，提示后回到可访问入口。
      ElMessageBox.alert(message, '无权访问', {
        confirmButtonText: '返回可访问页面',
        type: 'warning',
        callback: () => {
          window.location.href = '/'
        }
      })
      return Promise.reject(error)
    }
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default http
