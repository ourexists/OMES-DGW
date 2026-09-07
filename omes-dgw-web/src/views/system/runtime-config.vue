<template>
  <div class="page-container settings-page">
    <div class="page-hero">
      <div class="page-hero__text">
        <h2>系统配置</h2>
        <p>通用运行参数与扩展插件统一在此管理：先选能力，再上传插件并在二级窗口配置连接。</p>
      </div>
      <a-button :loading="loading" @click="refreshAll">
        <ReloadOutlined />
        刷新
      </a-button>
    </div>

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
              <p>{{ currentNav?.description || currentNav?.shortDesc }}</p>
            </div>
            <a-tag v-if="currentNav?.fieldCount" color="blue">{{ currentNav.fieldCount }} 项</a-tag>
            <a-tag v-else color="purple">插件</a-tag>
          </div>

          <div class="settings-panel__body">
            <template v-if="activeTab === 'cache-flush'">
              <div class="panel-block">
                <h4 class="panel-block__title">通用开关</h4>
                <ConfigSectionForm
                  :key="'cache-flush'"
                  ref="formRef"
                  section-id="cache-flush"
                  compact
                  hide-description
                />
              </div>
              <div class="panel-block">
                <h4 class="panel-block__title">Redis 插件</h4>
                <StorePluginPanel ref="redisPluginRef" :types="['redis']" />
              </div>
            </template>

            <template v-else-if="activeTab === 'monitor-collect'">
              <div class="panel-block">
                <h4 class="panel-block__title">InfluxDB 插件</h4>
                <p class="panel-block__hint">连接管理与历史/指标落库均在插件「配置」二级窗口中设置。</p>
                <StorePluginPanel ref="influxPluginRef" :types="['influx']" />
              </div>
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
import { computed, onMounted, ref } from 'vue'
import {
  CloudServerOutlined,
  ControlOutlined,
  DatabaseOutlined,
  HeartOutlined,
  RadarChartOutlined,
  ReloadOutlined,
  ThunderboltOutlined
} from '@ant-design/icons-vue'
import { listRuntimeConfigSections } from '@/api/system'
import ConfigSectionForm from './components/ConfigSectionForm.vue'
import StorePluginPanel from './components/StorePluginPanel.vue'

const loading = ref(false)
const sections = ref([])
const activeTab = ref('')
const formRef = ref(null)
const redisPluginRef = ref(null)
const influxPluginRef = ref(null)

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
    shortDesc: '后端开关 + Redis 插件'
  },
  'monitor-collect': {
    icon: CloudServerOutlined,
    iconBg: 'rgba(22, 119, 255, 0.1)',
    iconColor: '#1677ff',
    shortDesc: '监控采集 Influx 插件',
    title: '监控采集',
    description: '上传 Influx 插件后配置连接与落库参数',
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
  redisPluginRef.value?.reload?.()
  influxPluginRef.value?.reload?.()
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
