import axios from 'axios'
import { ElMessage } from 'element-plus'
import { clearAuthToken, getAuthToken } from '../stores/auth-store'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 10000
})

http.interceptors.request.use((config) => {
  const { tokenName, tokenValue } = getAuthToken()
  if (tokenName && tokenValue) {
    config.headers[tokenName] = tokenValue
  }
  return config
})

http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const message = error.response?.data?.message || error.message || '请求失败'
    if (error.response?.status === 401) {
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
