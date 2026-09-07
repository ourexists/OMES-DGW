<template>
  <div class="store-plugin-panel">
    <a-alert
      v-if="restartHint"
      type="warning"
      show-icon
      class="panel-banner"
      :message="restartHint"
    />

    <a-spin :spinning="loading">
      <div class="plugin-grid" :class="{ 'plugin-grid--single': visiblePlugins.length === 1 }">
        <article
          v-for="plugin in visiblePlugins"
          :key="plugin.type"
          class="plugin-card"
          :class="{ 'plugin-card--active': plugin.active }"
        >
          <div class="plugin-card__head">
            <div class="plugin-card__icon" :style="{ background: plugin.iconBg, color: plugin.iconColor }">
              <component :is="plugin.icon" />
            </div>
            <div class="plugin-card__meta">
              <div class="plugin-card__title-row">
                <h3>{{ plugin.name }}</h3>
                <a-tag :color="plugin.statusColor">{{ plugin.statusLabel }}</a-tag>
              </div>
              <p class="plugin-card__vendor">{{ plugin.vendor }}</p>
            </div>
          </div>

          <p class="plugin-card__desc">{{ plugin.description }}</p>

          <div class="plugin-card__stats">
            <div class="stat">
              <span class="stat__label">安装包</span>
              <span class="stat__value">{{ plugin.installed ? '已导入' : '未导入' }}</span>
            </div>
            <div class="stat">
              <span class="stat__label">运行时</span>
              <span class="stat__value">{{ plugin.loaded ? '已加载' : '未加载' }}</span>
            </div>
            <div class="stat">
              <span class="stat__label">启用状态</span>
              <span class="stat__value">{{ plugin.active ? '运行中' : '未启用' }}</span>
            </div>
          </div>

          <div v-if="plugin.jarName" class="plugin-card__file" :title="plugin.jarName">
            <PaperClipOutlined />
            <span>{{ plugin.jarName }}</span>
          </div>

          <div v-if="plugin.hint" class="plugin-card__hint">
            {{ plugin.hint }}
          </div>

          <div class="plugin-card__actions">
            <a-upload
              :before-upload="file => beforeUpload(plugin.type, file)"
              :show-upload-list="false"
              accept=".jar"
            >
              <a-button type="primary" :loading="plugin.uploading">
                <UploadOutlined />
                {{ plugin.installed ? '更新插件' : '上传插件' }}
              </a-button>
            </a-upload>
            <a-button
              :disabled="!plugin.installed && !plugin.loaded"
              @click="openConfig(plugin)"
            >
              <SettingOutlined />
              配置
            </a-button>
            <a-dropdown :disabled="!plugin.installed">
              <a-button>
                更多
                <DownOutlined />
              </a-button>
              <template #overlay>
                <a-menu>
                  <a-menu-item key="remove" danger @click="handleRemove(plugin.type)">
                    卸载插件文件
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
        </article>
      </div>
    </a-spin>

    <a-drawer
      v-model:open="drawerOpen"
      :title="drawerPlugin?.name ? `${drawerPlugin.name} · 配置` : '插件配置'"
      placement="right"
      :width="drawerWidth"
      destroy-on-close
      class="plugin-config-drawer"
    >
      <template v-if="drawerPlugin">
        <div class="drawer-intro">
          <a-tag :color="drawerPlugin.statusColor">{{ drawerPlugin.statusLabel }}</a-tag>
          <span class="drawer-intro__text">{{ drawerPlugin.description }}</span>
        </div>

        <a-alert
          v-if="!drawerPlugin.installed && !drawerPlugin.loaded"
          type="info"
          show-icon
          message="请先上传插件包，再填写配置。"
          style="margin-bottom: 16px"
        />

        <template v-else>
          <a-tabs v-model:activeKey="drawerTab" class="drawer-tabs">
            <a-tab-pane
              v-for="tab in drawerPlugin.configTabs"
              :key="tab.key"
              :tab="tab.label"
            />
          </a-tabs>

          <ConfigSectionForm
            :key="`${drawerPlugin.sectionId}-${drawerTab}`"
            :section-id="drawerPlugin.sectionId"
            :visible-groups="currentDrawerGroups"
            hide-description
            @saved="onConfigSaved"
          >
            <template v-if="drawerTab === 'connection'" #extra-actions>
              <a-button :loading="probing" @click="handleProbe(drawerPlugin.type)">
                探测连通性
              </a-button>
            </template>
          </ConfigSectionForm>
        </template>
      </template>
    </a-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  CloudServerOutlined,
  DatabaseOutlined,
  DownOutlined,
  PaperClipOutlined,
  SettingOutlined,
  UploadOutlined
} from '@ant-design/icons-vue'
import {
  getStorePluginStatus,
  uploadStorePlugin,
  removeStorePlugin,
  probeRedis,
  probeInflux,
  getRuntimeConfigSection
} from '@/api/system'
import ConfigSectionForm from './ConfigSectionForm.vue'

const props = defineProps({
  /** 展示哪些插件卡片：redis / influx */
  types: {
    type: Array,
    default: () => ['redis', 'influx']
  }
})

const emit = defineEmits(['status-change'])

const loading = ref(false)
const uploadingRedis = ref(false)
const uploadingInflux = ref(false)
const removingRedis = ref(false)
const removingInflux = ref(false)
const probing = ref(false)
const status = ref(null)
const drawerOpen = ref(false)
const drawerType = ref('')
const drawerTab = ref('connection')
const drawerWidth = ref(640)

const updateDrawerWidth = () => {
  drawerWidth.value = window.innerWidth < 768 ? Math.min(window.innerWidth - 24, 560) : 640
}

const redis = computed(() => status.value?.redis)
const influx = computed(() => status.value?.influx)

const restartHint = computed(() => {
  const hints = []
  const showRedis = props.types.includes('redis')
  const showInflux = props.types.includes('influx')
  if (showRedis && redis.value?.restartRequired) hints.push(redis.value.hint)
  if (showInflux && influx.value?.restartRequired) hints.push(influx.value.hint)
  return hints.filter(Boolean)[0] || ''
})

const allPlugins = computed(() => {
  const r = redis.value || {}
  const i = influx.value || {}
  const redisActive = r.activeStoreBackend === 'redis' && r.onClasspath
  const influxActive = Boolean(i.enabled && i.onClasspath)

  return [
    {
      type: 'redis',
      sectionId: 'plugin-redis',
      name: 'Redis 缓存与刷数',
      vendor: 'omes-dgw-plugin-redis',
      description: '上传后在「配置」中填写连接与键空间。上方可先选择存储后端为 redis。',
      icon: DatabaseOutlined,
      iconBg: 'rgba(220, 38, 38, 0.1)',
      iconColor: '#dc2626',
      installed: Boolean(r.jarPresent),
      loaded: Boolean(r.onClasspath),
      active: redisActive,
      jarName: r.jarNames?.[0] || '',
      hint: r.hint || '',
      uploading: uploadingRedis.value,
      statusLabel: redisActive ? '运行中' : r.jarPresent || r.onClasspath ? '已安装' : '未安装',
      statusColor: redisActive ? 'success' : r.jarPresent || r.onClasspath ? 'processing' : 'default',
      configTabs: [
        { key: 'connection', label: '连接管理', groups: ['连接管理'] },
        { key: 'extra', label: '键空间', groups: ['键空间'] }
      ]
    },
    {
      type: 'influx',
      sectionId: 'plugin-influx',
      name: 'InfluxDB 监控采集',
      vendor: 'omes-dgw-plugin-influx',
      description: '上传后在「配置」中填写连接管理，以及历史/指标落库参数。',
      icon: CloudServerOutlined,
      iconBg: 'rgba(22, 119, 255, 0.1)',
      iconColor: '#1677ff',
      installed: Boolean(i.jarPresent),
      loaded: Boolean(i.onClasspath),
      active: influxActive,
      jarName: i.jarNames?.[0] || '',
      hint: i.hint || '',
      uploading: uploadingInflux.value,
      statusLabel: influxActive ? '运行中' : i.jarPresent || i.onClasspath ? '已安装' : '未安装',
      statusColor: influxActive ? 'success' : i.jarPresent || i.onClasspath ? 'processing' : 'default',
      configTabs: [
        { key: 'connection', label: '连接管理', groups: ['连接管理'] },
        { key: 'store', label: '落库参数', groups: ['历史落库', '指标落库'] }
      ]
    }
  ]
})

const visiblePlugins = computed(() =>
  allPlugins.value.filter(p => props.types.includes(p.type))
)

const drawerPlugin = computed(() => allPlugins.value.find(p => p.type === drawerType.value) || null)

const currentDrawerGroups = computed(() => {
  const tab = drawerPlugin.value?.configTabs?.find(t => t.key === drawerTab.value)
  return tab?.groups || null
})

const loadStatus = async () => {
  loading.value = true
  try {
    const res = await getStorePluginStatus()
    status.value = res.data
    emit('status-change', res.data)
  } finally {
    loading.value = false
  }
}

const beforeUpload = (type, file) => {
  const uploading = type === 'redis' ? uploadingRedis : uploadingInflux
  uploading.value = true
  uploadStorePlugin(type, file)
    .then(res => {
      status.value = res.data
      emit('status-change', res.data)
      message.success('插件已上传，请填写连接等配置')
      drawerType.value = type
      drawerTab.value = 'connection'
      drawerOpen.value = true
    })
    .finally(() => {
      uploading.value = false
    })
  return false
}

const openConfig = plugin => {
  drawerType.value = plugin.type
  drawerTab.value = 'connection'
  drawerOpen.value = true
}

const onConfigSaved = () => {
  loadStatus()
}

const handleRemove = type => {
  const label = type === 'redis' ? 'Redis 缓存与刷数' : 'InfluxDB 监控采集'
  Modal.confirm({
    title: `卸载 ${label}？`,
    content: '将删除 plugins 目录中的 jar 文件。已保存的配置仍保留，可稍后重新上传。',
    okText: '卸载',
    okType: 'danger',
    onOk: async () => {
      const removing = type === 'redis' ? removingRedis : removingInflux
      removing.value = true
      try {
        const res = await removeStorePlugin(type)
        status.value = res.data
        emit('status-change', res.data)
        if (drawerType.value === type) drawerOpen.value = false
        message.success('插件文件已卸载')
      } finally {
        removing.value = false
      }
    }
  })
}

const handleProbe = async type => {
  probing.value = true
  try {
    if (type === 'redis') {
      const section = (await getRuntimeConfigSection('plugin-redis')).data
      const host = section?.values?.REDIS_HOST || 'localhost'
      const port = Number(section?.values?.REDIS_PORT || 6379)
      const res = await probeRedis({ host, port })
      const data = res.data
      if (data?.ok) message.success(`${data.message}（${data.latencyMs} ms）`)
      else message.error(data?.message || '探测失败')
    } else {
      const section = (await getRuntimeConfigSection('plugin-influx')).data
      const url = section?.values?.INFLUX_URL || 'http://localhost:8086'
      const res = await probeInflux({ url })
      const data = res.data
      if (data?.ok) message.success(`${data.message}（${data.latencyMs} ms）`)
      else message.error(data?.message || '探测失败')
    }
  } finally {
    probing.value = false
  }
}

watch(
  () => props.types.slice(),
  () => {
    if (drawerOpen.value && !props.types.includes(drawerType.value)) {
      drawerOpen.value = false
    }
  }
)

onMounted(() => {
  updateDrawerWidth()
  window.addEventListener('resize', updateDrawerWidth)
  loadStatus()
})

onUnmounted(() => {
  window.removeEventListener('resize', updateDrawerWidth)
})

defineExpose({ reload: loadStatus })
</script>

<style scoped>
.panel-banner {
  margin-bottom: 12px;
}

.plugin-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.plugin-grid--single {
  grid-template-columns: minmax(0, 1fr);
}

.plugin-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
  background: var(--omes-color-bg-container, #fff);
  border: 1px solid var(--omes-fluent-divider, rgba(0, 0, 0, 0.06));
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.plugin-card--active {
  border-color: rgba(82, 196, 26, 0.45);
}

.plugin-card__head {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.plugin-card__icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.plugin-card__meta {
  min-width: 0;
  flex: 1;
}

.plugin-card__title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.plugin-card__title-row h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--omes-color-text, rgba(0, 0, 0, 0.88));
}

.plugin-card__vendor {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--omes-color-text-tertiary, rgba(0, 0, 0, 0.35));
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.plugin-card__desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--omes-color-text-secondary, rgba(0, 0, 0, 0.55));
}

.plugin-card__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  padding: 12px;
  background: var(--omes-color-bg-muted, #f7f8fa);
  border-radius: 10px;
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.stat__label {
  font-size: 12px;
  color: var(--omes-color-text-tertiary, rgba(0, 0, 0, 0.35));
}

.stat__value {
  font-size: 13px;
  font-weight: 500;
  color: var(--omes-color-text, rgba(0, 0, 0, 0.88));
}

.plugin-card__file {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: 12px;
  color: var(--omes-color-text-secondary, rgba(0, 0, 0, 0.45));
}

.plugin-card__file span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plugin-card__hint {
  font-size: 12px;
  line-height: 1.5;
  color: #ad6800;
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 8px;
  padding: 8px 10px;
}

.plugin-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: auto;
  padding-top: 4px;
}

.drawer-intro {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 16px;
}

.drawer-intro__text {
  flex: 1;
  font-size: 13px;
  line-height: 1.6;
  color: var(--omes-color-text-secondary, rgba(0, 0, 0, 0.55));
}

.drawer-tabs {
  margin-bottom: 4px;
}

.drawer-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 12px;
}

@media (max-width: 900px) {
  .plugin-grid {
    grid-template-columns: 1fr;
  }
}
</style>
