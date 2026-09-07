import axios from 'axios'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

const request = axios.create({
  baseURL,
  timeout: 10000
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    const userStore = useUserStore()
    if (userStore.token && userStore.tokenType) {
      config.headers.Authorization = `${userStore.tokenType} ${userStore.token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    const res = response.data
    const userStore = useUserStore()

    if (res.code === 401) {
      message.error(res.message || '登录已过期，请重新登录')
      userStore.logout()
      if (window.location.hash !== '#/login') {
        window.location.href = '/#/login'
      }
      return Promise.reject(new Error(res.message || '登录已过期，请重新登录'))
    }

    // 根据后端约定的状态码处理
    if (res.code !== undefined && ![0, 200].includes(res.code)) {
      message.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  error => {
    const status = error.response?.status
    const userStore = useUserStore()
    
    if (status === 401) {
      message.error('登录已过期，请重新登录')
      userStore.logout()
      if (window.location.hash !== '#/login') {
        window.location.href = '/#/login'
      }
    } else if (status === 403) {
      message.error('没有权限访问')
    } else if (status === 404) {
      message.error('请求的资源不存在')
    } else if (status >= 500) {
      message.error('服务器错误')
    } else {
      message.error(error.message || '网络错误')
    }
    
    return Promise.reject(error)
  }
)

export default request
