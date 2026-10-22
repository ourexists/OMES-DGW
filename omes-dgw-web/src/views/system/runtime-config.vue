<template>
  <div class="page-container settings-page">
    <div class="page-hero">
      <div class="page-hero__text">
        <h2>系统配置</h2>
        <p>管理运行参数与存储连接配置。</p>
      </div>
      <a-space>
      <a-button :loading="loading" :disabled="restarting" @click="refreshAll">
        <ReloadOutlined />
        刷新
      </a-button>
      <a-button danger :loading="restarting" @click="confirmRestart">系统重启</a-button>
      </a-space>
    </div>

    <a-alert v-if="restartNotice" :type="restartFailed ? 'error' : 'info'" show-icon :message="restartNotice" style="margin-bottom: 16px" />

    <div class="settings-main">
      <div v-if="loading" class="settings-loading">
        <a-spin />
      </div>

      <div v-if="navItems.length" class="settings-shell">
        <aside class="settings-nav">
          <button
            v-for="item in navItems"
            :key="item.id"
            type="button"
            class="settings-nav__item"
            :class="{ 'is-active': activeTab === item.id }"
            @click="activeTab = item.id"
          >
            <span class="settings-nav__icon" :style="{ background: item.iconBg, color: item.iconColor }">
              <component :is="item.icon" />
            </span>
            <span class="settings-nav__copy">
              <span class="settings-nav__title">{{ item.title }}</span>
              <span class="settings-nav__desc">{{ item.shortDesc }}</span>
            </span>
          </button>
        </aside>

        <section class="settings-panel">
          <div class="settings-panel__head">
            <div>
              <h3>{{ currentNav?.title }}</h3>
            </div>
            <a-tag v-if="currentNav?.fieldCount" color="blue">{{ currentNav.fieldCount }} 项</a-tag>
            <a-tag v-else color="blue">连接</a-tag>
          </div>

          <div class="settings-panel__body">
            <template v-if="activeTab === 'cache-flush'">
                <ConfigSectionForm
                  :key="'cache-flush'"
                  ref="formRef"
                  section-id="cache-flush"
                  compact
                  hide-description
                  @saved="redisPluginRef?.reload?.()"
                >
                  <template #field="{ field, values }">
                    <PluginChoiceCards v-model="values[field.key]" :label="field.label" :options="cacheChoices(field)" />
                  </template>
                  <template #before-footer="{ values }">
                    <div v-if="values.COLLECT_STORE_BACKEND === 'redis'" class="backend-details">
                      <ConfigSectionForm
                        section-id="plugin-redis"
                        compact
                        hide-description
                      />
                    </div>
                  </template>
                </ConfigSectionForm>
            </template>

            <template v-else-if="activeTab === 'monitor-collect'">
              <ConfigSectionForm
                ref="formRef"
                section-id="plugin-influx"
                :visible-keys="['COLLECT_MONITOR_TYPE']"
                compact
                hide-description
              >
                <template #field="{ field, values }">
                  <PluginChoiceCards
                    v-model="values[field.key]"
                    :label="field.label"
                    :options="pluginChoices['monitor-collect']"
                  />
                </template>
                <template #before-footer="{ values }">
                  <div v-if="values.COLLECT_MONITOR_TYPE === 'influxdb'" class="backend-details">
                    <ConfigSectionForm
                      section-id="plugin-influx"
                      :visible-groups="['连接管理', '历史落库', '指标落库']"
                      compact
                      hide-description
                    />
                  </div>
                </template>
              </ConfigSectionForm>
            </template>

            <ConfigSectionForm
              v-else-if="activeTab"
              :key="activeTab"
              ref="formRef"
              :section-id="activeTab"
              compact
              hide-description
            />
          </div>
        </section>
      </div>
      <a-empty v-else-if="!loading" description="暂无可用配置分区" />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  CloudServerOutlined,
  ControlOutlined,
  DatabaseOutlined,
  HeartOutlined,
  RadarChartOutlined,
  ReloadOutlined,
  ThunderboltOutlined
} from '@ant-design/icons-vue'
import { listRuntimeConfigSections, restartSystem, getSystemRestartStatus } from '@/api/system'
import ConfigSectionForm from './components/ConfigSectionForm.vue'
import PluginChoiceCards from './components/PluginChoiceCards.vue'
import { pluginChoices } from './pluginChoices'

const loading = ref(false)
const restarting = ref(false)
const restartNotice = ref('')
const restartFailed = ref(false)
let restartTimer
let disposed = false

const waitForRestart = (previousId, deadline) => {
  restartTimer = window.setTimeout(async () => {
    if (disposed) return
    try {
      const { data } = await getSystemRestartStatus()
      if (disposed) return
      if (data.failure) {
        restarting.value = false
        restartFailed.value = true
        restartNotice.value = data.failure
        return
      }
      if (data.instanceId !== previousId && !data.restarting) {
        restarting.value = false
        restartNotice.value = ''
        message.success('系统重启完成，已重新加载配置')
        await refreshAll()
        return
      }
    } catch (error) {
      // Connection failures are expected while the application is restarting.
      if (error.response?.status === 401 || error.response?.status === 403) {
        restarting.value = false
        restartFailed.value = true
        restartNotice.value = '无法验证重启状态，请重新登录后检查服务。'
        return
      }
    }
    if (disposed) return
    if (Date.now() >= deadline) {
      restarting.value = false
      restartFailed.value = true
      restartNotice.value = '尚未检测到重启完成，请检查服务日志；若修改了访问地址或端口，请使用新地址访问。'
      return
    }
    waitForRestart(previousId, deadline)
  }, 2000)
}

const confirmRestart = () => {
  Modal.confirm({
    title: '确认重启系统？',
    content: '请先保存配置。重启将退出当前 Java 进程并重新启动，加载启动配置。期间采集与页面访问会暂时中断，未保存修改不会生效。',
    okText: '确认重启',
    cancelText: '取消',
    okType: 'danger',
    onOk: async () => {
      restarting.value = true
      restartFailed.value = false
      try {
        const { data } = await restartSystem()
        restartNotice.value = '正在重启系统并重新加载配置，请稍候…'
        waitForRestart(data.instanceId, Date.now() + 120000)
      } catch (error) {
        restarting.value = false
        restartFailed.value = true
        restartNotice.value = '未能确认重启请求，请检查服务状态后再操作。'
        throw error
      }
    }
  })
}

onUnmounted(() => {
  disposed = true
  window.clearTimeout(restartTimer)
})
const sections = ref([])
const activeTab = ref('')
const formRef = ref(null)
const monitorEnabled = values => values.COLLECT_MONITOR_TYPE === 'influxdb'
const cacheChoices = field => (field.options || []).map(value =>
  pluginChoices['cache-flush'].find(option => option.value === value) || { value, label: value, description: '' }
)

const metaMap = {
  'data-store': {
    icon: DatabaseOutlined,
    iconBg: 'rgba(82, 196, 26, 0.12)',
    iconColor: '#389e0d',
    shortDesc: 'SQLite 路径与迁移'
  },
  'cache-flush': {
    icon: ThunderboltOutlined,
    iconBg: 'rgba(220, 38, 38, 0.1)',
    iconColor: '#dc2626',
    shortDesc: '缓存后端与 Redis 连接',
    description: '选择实时缓存后端，安装和配置对应插件，保存后在线生效。采集队列由主服务持有。'
  },
  'monitor-collect': {
    icon: CloudServerOutlined,
    iconBg: 'rgba(22, 119, 255, 0.1)',
    iconColor: '#1677ff',
    shortDesc: 'Influx 连接与落库',
    title: '监控采集',
    description: '选择监控采集插件，管理安装、连接与落库配置。',
    synthetic: true
  },
  'collect-runtime': {
    icon: RadarChartOutlined,
    iconBg: 'rgba(22, 119, 255, 0.12)',
    iconColor: '#1677ff',
    shortDesc: '调度 / 连接 / 协议 / 离线'
  },
  observability: {
    icon: HeartOutlined,
    iconBg: 'rgba(250, 173, 20, 0.14)',
    iconColor: '#d48806',
    shortDesc: '诊断日志与运行指标'
  },
  'status-control': {
    icon: ControlOutlined,
    iconBg: 'rgba(114, 46, 209, 0.12)',
    iconColor: '#722ed1',
    shortDesc: '在线事件与点位写入'
  }
}

const builtinSections = computed(() =>
  (sections.value || []).filter(s => s.group === 'builtin')
)

const navItems = computed(() => {
  const items = builtinSections.value.map(item => {
    const meta = metaMap[item.id] || {
      icon: ControlOutlined,
      iconBg: 'rgba(0,0,0,0.06)',
      iconColor: '#595959',
      shortDesc: item.description || ''
    }
    return {
      ...item,
      ...meta,
      fieldCount: item.fields?.length || 0
    }
  })

  const cacheIdx = items.findIndex(i => i.id === 'cache-flush')
  const monitor = {
    id: 'monitor-collect',
    ...metaMap['monitor-collect'],
    fieldCount: 0
  }
  if (cacheIdx >= 0) {
    items.splice(cacheIdx + 1, 0, monitor)
  } else {
    items.push(monitor)
  }
  return items
})

const currentNav = computed(() => navItems.value.find(i => i.id === activeTab.value) || null)

const loadSections = async () => {
  loading.value = true
  try {
    const res = await listRuntimeConfigSections({ group: 'builtin' })
    sections.value = res.data || []
    const ids = navItems.value.map(i => i.id)
    if (!activeTab.value && ids.length) {
      activeTab.value = ids[0]
    } else if (activeTab.value && !ids.includes(activeTab.value)) {
      activeTab.value = ids[0] || ''
    }
  } finally {
    loading.value = false
  }
}

const refreshAll = async () => {
  await loadSections()
  formRef.value?.reload?.()
}

onMounted(loadSections)
</script>

<style scoped>
.settings-page {
  max-width: 1180px;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden !important;
  box-sizing: border-box;
}

.page-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  flex: 0 0 auto;
}

.page-hero__text h2 {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 600;
  color: var(--omes-color-text, rgba(0, 0, 0, 0.88));
}

.page-hero__text p {
  margin: 0;
  max-width: 680px;
  color: var(--omes-color-text-secondary, rgba(0, 0, 0, 0.45));
  font-size: 14px;
  line-height: 1.6;
}

.settings-main {
  position: relative;
  flex: 1 1 0;
  min-height: 0;
  overflow: hidden;
}

.settings-loading {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.55);
}

.settings-shell {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  grid-template-rows: minmax(0, 1fr);
  gap: 16px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.settings-shell > * {
  min-height: 0;
  max-height: 100%;
}

.settings-nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px;
  background: var(--omes-color-bg-container, #fff);
  border: 1px solid var(--omes-fluent-divider, rgba(0, 0, 0, 0.06));
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  overflow: auto;
}

.settings-nav__item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
  padding: 10px 10px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease;
}

.settings-nav__item:hover {
  background: var(--omes-color-bg-muted, #f7f8fa);
}

.settings-nav__item.is-active {
  background: var(--omes-color-primary-bg, rgba(22, 119, 255, 0.08));
}

.settings-nav__icon {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 16px;
}

.settings-nav__copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.settings-nav__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--omes-color-text, rgba(0, 0, 0, 0.88));
}

.settings-nav__desc {
  font-size: 12px;
  color: var(--omes-color-text-tertiary, rgba(0, 0, 0, 0.4));
  line-height: 1.4;
}

.settings-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  height: 100%;
  background: var(--omes-color-bg-container, #fff);
  border: 1px solid var(--omes-fluent-divider, rgba(0, 0, 0, 0.06));
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  overflow: hidden;
}

.settings-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 20px 14px;
  border-bottom: 1px solid var(--omes-fluent-divider, rgba(0, 0, 0, 0.06));
  background: linear-gradient(180deg, #fcfcfd 0%, #fff 100%);
  flex: 0 0 auto;
}

.settings-panel__head h3 {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 600;
  color: var(--omes-color-text, rgba(0, 0, 0, 0.88));
}

.settings-panel__head p {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--omes-color-text-secondary, rgba(0, 0, 0, 0.45));
}

.settings-panel__body {
  flex: 1 1 0;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 8px 20px 28px;
  -webkit-overflow-scrolling: touch;
}

.panel-block + .panel-block {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--omes-fluent-divider, rgba(0, 0, 0, 0.06));
}


.backend-details {
  margin-bottom: 16px;
}

.panel-block__title {
  margin: 4px 0 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--omes-color-text-secondary, rgba(0, 0, 0, 0.65));
}

.panel-block__hint {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--omes-color-text-tertiary, rgba(0, 0, 0, 0.45));
}

@media (max-width: 960px) {
  .settings-page {
    overflow: auto !important;
    height: auto;
  }

  .settings-main {
    flex: none;
    overflow: visible;
    min-height: 420px;
  }

  .settings-shell {
    grid-template-columns: 1fr;
    height: auto;
    overflow: visible;
  }

  .settings-nav {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .settings-panel {
    height: auto;
  }

  .settings-panel__body {
    overflow: visible;
  }

  .page-hero {
    flex-direction: column;
  }
}

@media (max-width: 640px) {
  .settings-nav {
    grid-template-columns: 1fr;
  }
}
</style>
