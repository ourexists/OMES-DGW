<template>
  <a-layout class="layout-container">
    <a-layout-sider
      v-model:collapsed="collapsed"
      collapsible
      :trigger="null"
      theme="light"
      width="240"
      class="layout-sider"
    >
      <div class="logo" :class="{ 'logo--collapsed': collapsed }">
        <img src="@/assets/brand/omes-icon.png" alt="OMES DGW" class="logo-img" />
        <span class="logo-text" v-if="!collapsed">OMES DGW</span>
      </div>
      <a-menu
        v-model:selectedKeys="selectedKeys"
        v-model:openKeys="openKeys"
        theme="light"
        mode="inline"
        @click="handleMenuClick"
        class="layout-menu"
      >
        <template v-for="item in menuItems" :key="item.key">
          <a-menu-item v-if="!item.children?.length" :key="item.key">
            <template #icon>
              <component :is="item.icon" />
            </template>
            <span>{{ item.title }}</span>
          </a-menu-item>
          <a-sub-menu v-else :key="item.key">
            <template #icon>
              <component :is="item.icon" />
            </template>
            <template #title>
              <span>{{ item.title }}</span>
            </template>
            <template v-for="child in item.children" :key="child.key">
              <a-menu-item v-if="!child.children?.length" :key="child.key">
                {{ child.title }}
              </a-menu-item>
              <a-sub-menu v-else :key="child.key">
                <template #title>
                  <span>{{ child.title }}</span>
                </template>
                <a-menu-item v-for="leaf in child.children" :key="leaf.key">
                  {{ leaf.title }}
                </a-menu-item>
              </a-sub-menu>
            </template>
          </a-sub-menu>
        </template>
      </a-menu>
      <div class="sider-trigger" @click="collapsed = !collapsed">
        <component :is="collapsed ? MenuUnfoldOutlined : MenuFoldOutlined" />
      </div>
    </a-layout-sider>

    <a-layout class="layout-main">
      <a-layout-header class="header">
        <div class="header-left">
          <a-breadcrumb class="omes-breadcrumb">
            <a-breadcrumb-item v-for="item in breadcrumbItems" :key="item.key">
              {{ item.title }}
            </a-breadcrumb-item>
          </a-breadcrumb>
        </div>
        <div class="header-right">
          <a-space size="large">
            <span class="current-time">{{ currentTime }}</span>
            <a-dropdown>
              <div class="user-info">
                <UserOutlined class="user-avatar" />
                <span class="user-name">{{ userStore.userInfo.nickname || userStore.userInfo.username || '未登录' }}</span>
                <DownOutlined class="down-icon" />
              </div>
              <template #overlay>
                <a-menu class="user-menu">
                  <a-menu-item key="1">个人信息</a-menu-item>
                  <a-menu-item key="2">修改密码</a-menu-item>
                  <a-menu-divider />
                  <a-menu-item key="3" @click="handleLogout">退出登录</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </a-space>
        </div>
      </a-layout-header>

      <a-layout-content class="content">
        <router-view />
      </a-layout-content>

      <a-layout-footer class="footer">
        <span>OMES DGW ©2026</span>
      </a-layout-footer>
    </a-layout>
  </a-layout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import {
  ClusterOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  MonitorOutlined,
  ProfileOutlined,
  SettingOutlined,
  ToolOutlined,
  UserOutlined,
  DownOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined
} from '@ant-design/icons-vue'
import dayjs from 'dayjs'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const collapsed = ref(false)
const selectedKeys = ref([])
const openKeys = ref([])
const currentTime = ref(dayjs().format('YYYY-MM-DD HH:mm:ss'))

let timer = null

const updateTime = () => {
  currentTime.value = dayjs().format('YYYY-MM-DD HH:mm:ss')
}

onMounted(() => {
  selectedKeys.value = [route.path]
  const pathParts = route.path.split('/').filter(Boolean)
  if (pathParts.length > 1) {
    openKeys.value = ['/' + pathParts[0]]
  }
  timer = setInterval(updateTime, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

const findMenuTrail = (menus, path, trail = []) => {
  for (const item of menus) {
    const currentTrail = [...trail, item]
    if (item.path === path) {
      return currentTrail
    }

    if (item.children?.length) {
      const childTrail = findMenuTrail(item.children, path, currentTrail)
      if (childTrail.length) return childTrail
    }
  }

  return []
}

const breadcrumbItems = computed(() => {
  const menuTrail = findMenuTrail(userStore.menus, route.path)
  if (menuTrail.length) {
    return menuTrail.map(item => ({
      key: item.path || item.id,
      title: item.title
    }))
  }

  return [
    {
      key: route.path,
      title: route.meta.title || '页面'
    }
  ]
})

const iconMap = {
  cluster: ClusterOutlined,
  dashboard: DashboardOutlined,
  database: DatabaseOutlined,
  monitor: MonitorOutlined,
  profile: ProfileOutlined,
  setting: SettingOutlined,
  tool: ToolOutlined
}

const resolveMenuIcon = item => {
  if (item.path === '/monitoring/overview') return MonitorOutlined
  if (item.path === '/equipment/overview') return ClusterOutlined
  return iconMap[item.icon] || ToolOutlined
}

const normalizeMenus = menus => menus.map(item => ({
  key: item.path || item.id,
  title: item.title,
  icon: resolveMenuIcon(item),
  children: item.children?.length ? normalizeMenus(item.children) : []
}))

const menuItems = computed(() => normalizeMenus(userStore.menus))

const syncMenuState = () => {
  selectedKeys.value = [route.path]
  const pathParts = route.path.split('/').filter(Boolean)
  openKeys.value = pathParts.length > 1 ? ['/' + pathParts[0]] : []
}

watch(
  () => route.path,
  syncMenuState
)

const handleMenuClick = ({ key }) => {
  router.push(key)
}

const handleLogout = () => {
  userStore.logout()
  message.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
  min-height: 0;
  background: var(--omes-color-bg-layout);
}

.layout-sider {
  background: var(--omes-fluent-mica, linear-gradient(180deg, #fafafa 0%, #f3f3f3 100%)) !important;
  box-shadow: inset -1px 0 0 var(--omes-fluent-divider, rgba(0, 0, 0, 0.04));
  position: relative;
}

.logo {
  height: 64px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 10px;
  border-bottom: 1px solid var(--omes-fluent-divider, rgba(0, 0, 0, 0.04));
  overflow: hidden;
}

.logo--collapsed {
  justify-content: center;
  padding: 0;
}

.logo-img {
  height: 32px;
  width: 32px;
  border-radius: 6px;
  object-fit: cover;
  flex-shrink: 0;
}

.logo-text {
  color: var(--omes-color-text);
  font-size: 16px;
  font-weight: var(--omes-font-weight-semibold);
  letter-spacing: 0.3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.3;
}

.layout-menu {
  height: calc(100% - 112px);
  overflow-x: hidden;
  overflow-y: auto;
  background: transparent !important;
  border-right: none !important;
  padding-bottom: 8px;
}

.layout-menu :deep(.ant-menu-item),
.layout-menu :deep(.ant-menu-submenu-title) {
  margin: 4px 8px;
  width: calc(100% - 16px);
  border-radius: var(--omes-border-radius-medium);
}

.layout-menu :deep(.ant-menu-item-selected) {
  background: var(--omes-color-primary-bg) !important;
  color: var(--omes-color-primary) !important;
  box-shadow: none;
}

.layout-menu :deep(.ant-menu-item:hover:not(.ant-menu-item-selected)),
.layout-menu :deep(.ant-menu-submenu-title:hover:not(.ant-menu-submenu-open)) {
  background: var(--omes-color-bg-muted) !important;
}

.sider-trigger {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 48px;
  line-height: 48px;
  text-align: center;
  color: var(--omes-color-text-tertiary);
  cursor: pointer;
  transition: all 0.2s;
  border-top: 1px solid var(--omes-fluent-divider, rgba(0, 0, 0, 0.04));
}

.sider-trigger:hover {
  color: var(--omes-color-primary);
  background: var(--omes-color-primary-bg);
}

.layout-main {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.header {
  background: var(--omes-color-bg-container);
  padding: 0 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--omes-fluent-divider, rgba(0, 0, 0, 0.04));
  height: 64px;
  flex-shrink: 0;
  backdrop-filter: saturate(180%) blur(20px);
}

.header-left {
  flex: 1;
}

.omes-breadcrumb :deep(.ant-breadcrumb-link) {
  color: var(--omes-color-text-secondary);
  font-size: 14px;
}

.omes-breadcrumb :deep(.ant-breadcrumb-link:last-child) {
  color: var(--omes-color-text);
  font-weight: var(--omes-font-weight-semibold);
}

.header-right {
  display: flex;
  align-items: center;
}

.current-time {
  color: var(--omes-color-text-secondary);
  font-size: 14px;
  font-family: 'Cascadia Mono', 'Consolas', monospace;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: var(--omes-border-radius-medium);
  transition: background 0.2s;
}

.user-info:hover {
  background: var(--omes-color-bg-muted);
}

.user-avatar {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 32px;
  height: 32px;
  background: var(--omes-gradient-primary);
  color: var(--omes-color-white);
  border-radius: 50%;
  font-size: 14px;
}

.user-name {
  color: var(--omes-color-text);
  font-weight: 500;
}

.down-icon {
  color: var(--omes-color-text-tertiary);
  font-size: 12px;
}

.content {
  flex: 1 1 0%;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--omes-color-bg-layout);
}

.footer {
  text-align: center;
  color: var(--omes-color-text-tertiary);
  padding: 12px 50px;
  font-size: 13px;
  background: transparent;
  border-top: none;
  flex-shrink: 0;
}

.footer-sep {
  margin: 0 12px;
  color: var(--omes-color-border-secondary);
}

@media screen and (max-width: 1280px) {
  .header {
    padding: 0 16px;
    height: 56px;
  }

  .current-time {
    font-size: 13px;
  }

  .footer {
    padding: 10px 30px;
    font-size: 12px;
  }

}

@media screen and (max-width: 1024px) {
  .layout-sider {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 999;
    transform: translateX(-100%);
    transition: transform 0.3s;
  }

  .layout-sider:deep(.ant-layout-sider-collapsed) {
    transform: translateX(0);
  }

  .header {
    padding: 0 12px;
  }

  .current-time {
    display: none;
  }

  .footer {
    padding: 8px 16px;
    font-size: 11px;
  }

  .footer-sep {
    margin: 0 6px;
  }
}
</style>
