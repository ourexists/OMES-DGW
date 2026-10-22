/**
 * 前端固定侧栏菜单（不做角色菜单权限过滤）。
 * 登录仅校验 token；路由与侧栏一律走本配置。
 */
export const APP_MENUS = [
  {
    id: 'monitoring-overview',
    title: '总览',
    path: '/monitoring/overview',
    icon: 'monitor'
  },
  {
    id: 'equipment-overview',
    title: '设备监控',
    path: '/equipment/overview',
    icon: 'cluster'
  },
  {
    id: 'equipment-ledger',
    title: '设备台账',
    path: '/equipment/ledger',
    icon: 'setting'
  },
  {
    id: 'equipment-command',
    title: '指令记录',
    path: '/equipment/command',
    icon: 'profile'
  },
  {
    id: 'system-runtime-config',
    title: '系统配置',
    path: '/system/runtime-config',
    icon: 'setting'
  }
]

export const DEFAULT_HOME_PATH = APP_MENUS[0]?.path || '/equipment/ledger'
