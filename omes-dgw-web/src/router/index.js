import { createRouter, createWebHashHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const viewModules = import.meta.glob('../views/**/*.vue')

const dynamicRoutePaths = new Set()
const dynamicRouteFallbackName = 'DynamicRouteFallback'
const emptyRouteComponent = { render: () => null }

const redirectToFirstMenu = () => {
  const userStore = useUserStore()
  return userStore.getFirstMenuPath() || '/login'
}

const createRouteName = item => `menu:${item.path}`

const getRouteComponent = path => {
  if (typeof path !== 'string' || !path) return null

  const viewPath = path.replace(/^\/+/, '')

  return viewModules[`../views/${viewPath}.vue`] || viewModules[`../views/${viewPath}/index.vue`]
}

const getFirstChildPath = (item, visited = new Set()) => {
  if (!item?.children?.length || visited.has(item)) return ''

  visited.add(item)

  for (const child of item.children) {
    if (getRouteComponent(child.path)) return child.path

    const childPath = getFirstChildPath(child, visited)
    if (childPath) return childPath
  }

  return ''
}

export const setupDynamicRoutes = menus => {
  let hasNewRoute = false

  const addRoutes = (menuList, visited = new Set()) => {
    if (!Array.isArray(menuList)) return

    menuList.forEach(item => {
      if (!item || visited.has(item)) return

      visited.add(item)

      const component = getRouteComponent(item.path)
      const redirect = getFirstChildPath(item)

      const routeExists = item.path && (
        dynamicRoutePaths.has(item.path) ||
        router.getRoutes().some(route => route.path === item.path)
      )

      if (!routeExists && item.path && (component || redirect)) {
        const routeName = createRouteName(item)
        router.addRoute('Layout', {
          path: item.path,
          name: routeName,
          ...(component ? { component } : {}),
          redirect: redirect || undefined,
          meta: {
            title: item.title
          }
        })
        dynamicRoutePaths.add(item.path)
        hasNewRoute = true
      }

      if (item.children?.length) {
        addRoutes(item.children, visited)
      }
    })
  }

  addRoutes(menus)
  return hasNewRoute
}

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/',
    name: 'Layout',
    component: () => import('@/layout/index.vue'),
    redirect: redirectToFirstMenu,
    meta: { requiresAuth: true },
    children: [
      {
        path: '/equipment/history/:id',
        name: 'EquipmentHistoryChart',
        component: () => import('@/views/equipment/history-chart.vue'),
        meta: { title: '历史曲线', requiresAuth: true }
      },
      {
        path: '/equipment/collect-metrics/:id',
        name: 'EquipmentCollectMetrics',
        component: () => import('@/views/equipment/collect-metrics.vue'),
        meta: { title: '性能指标', requiresAuth: true }
      },
      {
        path: '/equipment/realtime/:id',
        name: 'EquipmentRealtimePoints',
        component: () => import('@/views/equipment/realtime-points.vue'),
        meta: { title: '实时点位', requiresAuth: true }
      },
      {
        path: '/:pathMatch(.*)*',
        name: dynamicRouteFallbackName,
        component: emptyRouteComponent,
        meta: { title: '页面未找到', requiresAuth: true, dynamicFallback: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  
  // 设置页面标题
  document.title = `${to.meta.title || ''} - OMES DGW`
  
  // 白名单：不需要登录的页面
  const whiteList = ['/login']
  
  // 如果在白名单内，直接放行
  if (whiteList.includes(to.path)) {
    next()
    return
  }
  
  // 其他页面需要登录
  if (!userStore.token || !userStore.tokenType) {
    next('/login')
    return
  }

  const hasNewRoute = setupDynamicRoutes(userStore.menus)
  if (hasNewRoute) {
    next({ path: to.path, query: to.query, hash: to.hash, replace: true })
    return
  }

  if (!to.matched.length || to.meta.dynamicFallback) {
    const firstMenuPath = userStore.getFirstMenuPath()
    if (firstMenuPath && firstMenuPath !== to.path) {
      next({ path: firstMenuPath, replace: true })
      return
    }

    next()
    return
  }
  
  next()
})

export default router
