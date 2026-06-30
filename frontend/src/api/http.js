import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clearAuthToken, getAuthToken } from '../stores/auth-store.js'

const env = import.meta.env || globalThis.__VITE_ENV__ || {}
const http = axios.create({
  baseURL: env.VITE_API_BASE || '/api',
  timeout: 10000
})

http.interceptors.request.use((config) => {
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
      return response.data
    }
    if (response.data && typeof response.data === 'object' && 'code' in response.data && 'data' in response.data) {
      if (response.data.code !== 200) {
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
      clearAuthToken()
      if (window.location.pathname !== '/login') {
        const redirect = encodeURIComponent(window.location.pathname + window.location.search)
        window.location.href = `/login?redirect=${redirect}`
      }
      ElMessage.error(message)
      return Promise.reject(error)
    }
    if (error.response?.status === 403) {
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
