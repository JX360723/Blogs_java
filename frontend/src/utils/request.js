import axios from 'axios'
import { ElMessage } from 'element-plus'
import { USE_MOCK, createMockAdapter } from '../mock'

// API 基址：本地开发经 vite proxy（/api → http://localhost:8080），与后端统一 /api/v1 前缀
const baseURL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

const service = axios.create({
  baseURL,
  timeout: 15000
})

// mock 开关：USE_MOCK=true 时走本地 mock 适配器；false 时走真实后端
if (USE_MOCK) {
  service.defaults.adapter = createMockAdapter()
}

// 请求拦截：附加 Bearer token
service.interceptors.request.use((config) => {
  const token = localStorage.getItem('blog_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

function handleUnauthorized() {
  localStorage.removeItem('blog_token')
  localStorage.removeItem('blog_user')
  ElMessage.error('登录已失效，请重新登录')
  if (!window.location.pathname.startsWith('/login')) {
    setTimeout(() => {
      window.location.href = '/login'
    }, 800)
  }
}

// 响应拦截：统一解包 Result<T>（code=200 成功，data 为业务数据）
service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && typeof res === 'object' && 'code' in res) {
      if (res.code === 200) {
        return res.data
      }
      if (res.code === 401) {
        handleUnauthorized()
        return Promise.reject(new Error(res.message || '未授权'))
      }
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      handleUnauthorized()
    } else if (status === 403) {
      ElMessage.error('无权限或账号已被禁用')
    } else if (status === 404) {
      ElMessage.error(error.response?.data?.message || '资源不存在')
    } else if (status === 400) {
      ElMessage.error(error.response?.data?.message || '请求参数错误')
    } else if (status >= 500) {
      ElMessage.error('服务器内部错误')
    } else {
      ElMessage.error('网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default service
