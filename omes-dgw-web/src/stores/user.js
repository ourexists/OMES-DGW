import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref('')
  const tokenType = ref('')
  const expiresInMs = ref(0)
  const userInfo = ref({})
  const menus = ref([])

  // 初始化时从localStorage读取
  try {
    token.value = localStorage.getItem('token') || ''
    tokenType.value = localStorage.getItem('tokenType') || ''
    expiresInMs.value = Number(localStorage.getItem('expiresInMs') || 0)
    const savedUserInfo = localStorage.getItem('userInfo')
    const savedMenus = localStorage.getItem('menus')
    if (savedUserInfo) {
      userInfo.value = JSON.parse(savedUserInfo)
    }
    if (savedMenus) {
      const parsedMenus = JSON.parse(savedMenus)
      menus.value = Array.isArray(parsedMenus) ? parsedMenus : []
    }
  } catch (e) {
    console.error('读取本地存储失败:', e)
  }

  const login = async (username, password) => {
    const res = await loginApi({ username, password })
    const data = res?.data

    if (!data?.token || !data?.tokenType || !data?.user || !Array.isArray(data?.menus) || data.menus.length === 0) {
      throw new Error('登录响应格式错误')
    }

    token.value = data.token
    tokenType.value = data.tokenType
    expiresInMs.value = data.expiresInMs
    userInfo.value = data.user
    menus.value = data.menus

    localStorage.setItem('token', token.value)
    localStorage.setItem('tokenType', tokenType.value)
    localStorage.setItem('expiresInMs', String(expiresInMs.value))
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
    localStorage.setItem('menus', JSON.stringify(menus.value))

    return userInfo.value
  }

  const getFirstMenuPath = (menuList = menus.value, visited = new Set()) => {
    if (!Array.isArray(menuList)) return ''

    for (const item of menuList) {
      if (!item || visited.has(item)) continue

      visited.add(item)

      if (item.children?.length) {
        const childPath = getFirstMenuPath(item.children, visited)
        if (childPath) return childPath
      }

      if (typeof item.path === 'string' && item.path && item.path !== '/') {
        return item.path
      }
    }

    return ''
  }

  const logout = () => {
    token.value = ''
    tokenType.value = ''
    expiresInMs.value = 0
    userInfo.value = {}
    menus.value = []
    localStorage.removeItem('token')
    localStorage.removeItem('tokenType')
    localStorage.removeItem('expiresInMs')
    localStorage.removeItem('userInfo')
    localStorage.removeItem('menus')
  }

  return {
    token,
    tokenType,
    expiresInMs,
    userInfo,
    menus,
    login,
    getFirstMenuPath,
    logout
  }
})
