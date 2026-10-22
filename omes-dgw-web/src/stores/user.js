import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi } from '@/api/auth'
import { APP_MENUS, DEFAULT_HOME_PATH } from '@/config/menus'

export const useUserStore = defineStore('user', () => {
  const token = ref('')
  const tokenType = ref('')
  const expiresInMs = ref(0)
  const userInfo = ref({})
  // 侧栏/动态路由用前端固定菜单，不依赖后端 menus 权限
  const menus = ref([...APP_MENUS])

  try {
    token.value = localStorage.getItem('token') || ''
    tokenType.value = localStorage.getItem('tokenType') || ''
    expiresInMs.value = Number(localStorage.getItem('expiresInMs') || 0)
    const savedUserInfo = localStorage.getItem('userInfo')
    if (savedUserInfo) {
      userInfo.value = JSON.parse(savedUserInfo)
    }
  } catch (e) {
    console.error('读取本地存储失败:', e)
  }

  const login = async (username, password) => {
    const res = await loginApi({ username, password })
    const data = res?.data

    if (!data?.token || !data?.tokenType || !data?.user) {
      throw new Error('登录响应格式错误')
    }

    token.value = data.token
    tokenType.value = data.tokenType
    expiresInMs.value = data.expiresInMs
    userInfo.value = data.user
    menus.value = [...APP_MENUS]

    localStorage.setItem('token', token.value)
    localStorage.setItem('tokenType', tokenType.value)
    localStorage.setItem('expiresInMs', String(expiresInMs.value))
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))

    return userInfo.value
  }

  const getFirstMenuPath = (menuList = menus.value, visited = new Set()) => {
    if (!Array.isArray(menuList) || menuList.length === 0) {
      return DEFAULT_HOME_PATH
    }

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

    return DEFAULT_HOME_PATH
  }

  const logout = () => {
    token.value = ''
    tokenType.value = ''
    expiresInMs.value = 0
    userInfo.value = {}
    menus.value = [...APP_MENUS]
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
