import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearAuthToken, getAuthToken } from '../stores/auth-store'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 10000
})

http.interceptors.request.use((config) => {
  // 后端使用 Sa-Token，请求前从本地存储读取 token 并写入对应请求头。
  const { tokenName, tokenValue } = getAuthToken()
  if (tokenName && tokenValue) {
    config.headers[tokenName] = tokenValue
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return response.data
    }
    if (response.data && typeof response.data === 'object' && 'code' in response.data && 'data' in response.data) {
      return response.data.data
    }
    return response.data
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '请求失败'
    if (error.response?.status === 401) {
      // token 失效或未登录时清理本地会话，并带上原访问地址回到登录页。
      clearAuthToken()
      if (window.location.pathname !== '/login') {
        const redirect = encodeURIComponent(window.location.pathname + window.location.search)
        window.location.href = `/login?redirect=${redirect}`
      }
    }
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default http
