<template>
  <div class="page-container realtime-points-page">
    <div class="page-header">
      <div class="title-block">
        <a-button type="text" class="back-btn" aria-label="返回" @click="goBack">
          <LeftOutlined />
        </a-button>
        <div class="title-copy">
          <h2 :title="deviceName">{{ deviceName }}</h2>
          <div class="title-meta">
            <span :class="['status-pill', isOnline ? 'online' : 'offline']">
              <span class="status-dot"></span>
              {{ statusText }}
            </span>
            <span v-if="connectionLabel" :class="['status-pill', connectionClass]">
              <span class="status-dot"></span>
              {{ connectionLabel }}
            </span>
            <span v-if="deviceInfo.code" class="meta-text">{{ deviceInfo.code }}</span>
            <span v-if="deviceInfo.protocol" class="meta-text">{{ deviceInfo.protocol }}</span>
            <span v-if="endpointText" class="meta-text">{{ endpointText }}</span>
            <span class="meta-text">更新于 {{ formatTime(deviceInfo.lastUpdateTime) }}</span>
          </div>
          <div v-if="statusMessage" class="status-message" :title="statusMessage">{{ statusMessage }}</div>
        </div>
      </div>
      <a-select v-model:value="refreshInterval" class="refresh-select" @change="restartPolling">
        <a-select-option :value="5000">每5秒刷新</a-select-option>
        <a-select-option :value="10000">每10秒刷新</a-select-option>
        <a-select-option :value="30000">每30秒刷新</a-select-option>
        <a-select-option :value="60000">每60秒刷新</a-select-option>
      </a-select>
    </div>

    <a-card class="point-card">
      <template #title>
        <span class="card-title">实时点位<em>{{ pointList.length }}</em></span>
      </template>
      <a-empty v-if="missingDevice" class="point-empty" description="未找到该设备的实时数据" />
      <a-table
        v-else
        :columns="pointColumns"
        :data-source="pointList"
        :loading="initialLoading"
        :pagination="false"
        :scroll="{ x: 860, y: 'calc(100% - 39px)' }"
        size="small"
        row-key="key"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <span class="point-name" :title="record.name">{{ record.name || '-' }}</span>
          </template>
          <template v-if="column.key === 'code'">
            <span class="point-code" :title="record.code">{{ record.code || '-' }}</span>
          </template>
          <template v-if="column.key === 'actualAddress'">
            <span :class="['addr', { muted: !record.actualAddress }]">{{ record.actualAddress || '-' }}</span>
          </template>
          <template v-if="column.key === 'setpointAddress'">
            <span :class="['addr', { muted: !record.setpointAddress }]">{{ record.setpointAddress || '-' }}</span>
          </template>
          <template v-if="column.key === 'value'">
            <span :class="['point-value', { error: record.error, empty: isBlankPointValue(record) }]">
              {{ record.error || formatPointValue(record.value) }}
            </span>
          </template>
          <template v-if="column.key === 'time'">
            <span class="point-time">{{ formatTime(record.time || record.updateTime || record.timestamp) }}</span>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LeftOutlined } from '@ant-design/icons-vue'
import { getDeviceRealtimeList } from '@/api/equipment'

const route = useRoute()
const router = useRouter()

const deviceInfo = ref({})
const refreshInterval = ref(5000)
const initialLoading = ref(true)
let refreshTimer = null
let dataLoading = false
let pollingActive = false

const deviceName = computed(() => deviceInfo.value.name || route.query.name || route.params.id || '-')

const pointColumns = [
  { title: '点位名称', dataIndex: 'name', key: 'name', ellipsis: true },
  { title: '点位编码', dataIndex: 'code', key: 'code', width: 120, ellipsis: true },
  { title: '实际值点位', dataIndex: 'actualAddress', key: 'actualAddress', width: 130, ellipsis: true },
  { title: '设定值点位', dataIndex: 'setpointAddress', key: 'setpointAddress', width: 130, ellipsis: true },
  { title: '实时值', key: 'value', width: 160, align: 'right' },
  { title: '更新时间', key: 'time', width: 176 }
]

const getResponseData = response => response?.data ?? response

/** 实时展示只读/读写点位（有实际值点位），排除只写点位 */
const COLLECT_ACCESS_TYPES = ['READ_ONLY', 'READ_WRITE']
const WRITE_ACCESS_TYPES = ['WRITE_ONLY', 'READ_WRITE']

const isCollectAccessType = accessType => {
  const normalized = String(accessType || 'READ_ONLY').toUpperCase()
  return COLLECT_ACCESS_TYPES.includes(normalized)
}

const isWriteAccessType = accessType => (
  WRITE_ACCESS_TYPES.includes(String(accessType || '').toUpperCase())
)

const pointRowKey = (point, fallback) => point?.code || point?.name || fallback

const indexRealtimePoints = points => {
  const byKey = {}
  Object.entries(points || {}).forEach(([key, point]) => {
    if (!point) return
    byKey[key] = point
    if (point.code) byKey[point.code] = point
    if (point.pointId) byKey[point.pointId] = point
  })
  return byKey
}

const toPointRow = (configPoint, realtimePoint, index) => {
  const accessType = configPoint?.accessType ?? realtimePoint?.accessType
  return {
    ...realtimePoint,
    key: pointRowKey(configPoint || realtimePoint, String(index)),
    code: configPoint?.code || realtimePoint?.code || realtimePoint?.pointId || '',
    name: configPoint?.name || realtimePoint?.name || realtimePoint?.pointName || '',
    accessType,
    actualAddress: isCollectAccessType(accessType)
      ? (configPoint?.actualAddress || realtimePoint?.actualAddress || realtimePoint?.address || '')
      : '',
    setpointAddress: isWriteAccessType(accessType) ? (configPoint?.setpointAddress || '') : ''
  }
}

const matchRealtimePoint = (realtimeByKey, configPoint) => {
  const byCode = realtimeByKey[configPoint?.code]
  if (byCode) return byCode
  const byName = configPoint?.name ? realtimeByKey[configPoint.name] : null
  if (!byName) return {}
  const realtimeCode = byName.code || byName.pointId || ''
  if (!realtimeCode || realtimeCode === configPoint.code) return byName
  return {}
}

const normalizePoints = (points, collectConfig) => {
  const realtimeByKey = indexRealtimePoints(points)
  const configPoints = collectConfig?.points || []
  const hasAccessMeta = configPoints.some(p => p?.accessType != null)

  if (configPoints.length) {
    return configPoints
      .filter(p => p && (p.name || p.code) && (!hasAccessMeta || isCollectAccessType(p.accessType)))
      .map((p, index) => toPointRow(p, matchRealtimePoint(realtimeByKey, p), index))
  }

  return Object.entries(points || {}).map(([key, point], index) => toPointRow(
    null,
    { ...point, key },
    index
  ))
}

const normalizePointsFromConfig = collectConfig => (collectConfig?.points || [])
  .filter(p => p && (p.name || p.code) && isCollectAccessType(p.accessType))
  .map((p, index) => ({
    ...toPointRow(p, {}, index),
    error: '未读取到数据'
  }))

const normalizeDevice = item => {
  let pointList = normalizePoints(item.points, item.collectConfig)
  if (pointList.length === 0 && item.collectConfig?.points?.length) {
    pointList = normalizePointsFromConfig(item.collectConfig)
  }
  return {
    ...item,
    id: item.deviceId,
    code: item.deviceCode,
    name: item.deviceName,
    status: item.online ? 'online' : 'offline',
    pointList
  }
}

const pointList = computed(() => deviceInfo.value.pointList || [])
const isOnline = computed(() => deviceInfo.value.status === 'online')
const missingDevice = computed(() => !initialLoading.value && !deviceInfo.value.id)
const statusText = computed(() => {
  if (!deviceInfo.value.id) return initialLoading.value ? '加载中' : '未找到'
  return isOnline.value ? '在线' : '离线'
})
const endpointText = computed(() => {
  const ip = deviceInfo.value.ipAddress
  const port = deviceInfo.value.port
  if (ip && port != null && port !== '') return `${ip}:${port}`
  return ip || ''
})
const connectionLabel = computed(() => deviceInfo.value.connectionStatusLabel || '')
const connectionClass = computed(() => {
  const status = String(deviceInfo.value.connectionStatus || '').toLowerCase()
  if (status === 'connected') return 'connected'
  if (status === 'unsupported') return 'unsupported'
  return 'disconnected'
})
const statusMessage = computed(() => deviceInfo.value.connectionStatusMessage || deviceInfo.value.message || '')
const isBlankPointValue = record => (
  !record?.error && (record?.value === undefined || record?.value === null || record?.value === '')
)

const formatTime = value => {
  if (!value) return '-'

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value

  return date.toLocaleString()
}

const formatPointValue = value => {
  if (value === undefined || value === null || value === '') return '-'
  if (typeof value === 'object') return JSON.stringify(value)

  return value
}

const loadData = async () => {
  if (dataLoading) return

  dataLoading = true
  try {
    const response = await getDeviceRealtimeList()
    const data = getResponseData(response)
    const list = Array.isArray(data) ? data.map(normalizeDevice) : []
    deviceInfo.value = list.find(item => String(item.id) === String(route.params.id)) || {}
  } catch (error) {
    deviceInfo.value = {}
  } finally {
    dataLoading = false
    initialLoading.value = false
  }
}

const goBack = () => {
  router.back()
}

const stopPolling = () => {
  pollingActive = false
  if (refreshTimer) {
    clearTimeout(refreshTimer)
    refreshTimer = null
  }
}

const scheduleNextPoll = () => {
  if (!pollingActive) return

  refreshTimer = setTimeout(async () => {
    refreshTimer = null
    await loadData()
    scheduleNextPoll()
  }, refreshInterval.value)
}

const startPolling = () => {
  stopPolling()
  pollingActive = true
  scheduleNextPoll()
}

const restartPolling = () => {
  startPolling()
}

onMounted(async () => {
  await loadData()
  startPolling()
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<style scoped>
.realtime-points-page {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.realtime-points-page .page-header {
  flex-shrink: 0;
  align-items: flex-start;
  gap: 16px;
}

.title-block {
  display: flex;
  flex: 1;
  align-items: flex-start;
  gap: 4px;
  min-width: 0;
}

.back-btn {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  margin-top: -4px;
  color: var(--omes-color-text-secondary);
}

.title-copy {
  min-width: 0;
}

.title-copy h2 {
  height: auto;
  padding-left: 0;
  border-left: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.title-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.meta-text {
  color: var(--omes-color-text-tertiary);
  font-size: 13px;
  line-height: 22px;
}

.meta-text + .meta-text::before {
  content: '·';
  margin-right: 8px;
  color: var(--omes-color-text-quaternary);
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 22px;
  padding: 0 8px;
  border-radius: 11px;
  font-size: 12px;
  font-weight: 600;
}

.status-pill.online,
.status-pill.connected {
  color: var(--omes-color-success);
  background: var(--omes-color-success-bg);
}

.status-pill.offline,
.status-pill.disconnected {
  color: var(--omes-color-text-secondary);
  background: var(--omes-color-bg-muted);
}

.status-pill.unsupported {
  color: var(--omes-color-warning);
  background: color-mix(in srgb, var(--omes-color-warning) 12%, transparent);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.status-pill.online .status-dot {
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--omes-color-success) 20%, transparent);
}

.status-message {
  margin-top: 6px;
  color: var(--omes-color-error);
  font-size: 13px;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.refresh-select {
  width: 140px;
  flex-shrink: 0;
}

.point-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.card-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.card-title em {
  min-width: 22px;
  padding: 0 6px;
  border-radius: 10px;
  background: var(--omes-color-primary-bg);
  color: var(--omes-color-primary);
  font-style: normal;
  font-size: 12px;
  font-weight: 600;
  line-height: 20px;
  text-align: center;
}

.point-card :deep(.ant-card-body) {
  flex: 1;
  min-height: 0;
  padding: 0 12px 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.point-card :deep(.ant-table-wrapper),
.point-card :deep(.ant-spin-nested-loading),
.point-card :deep(.ant-spin-container) {
  flex: 1;
  height: 100%;
  min-height: 0;
}

.point-card :deep(.ant-spin-container) {
  display: flex;
  flex-direction: column;
}

.point-card :deep(.ant-table) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.point-card :deep(.ant-table-container) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.point-card :deep(.ant-table-header) {
  flex-shrink: 0;
}

.point-card :deep(.ant-table-body) {
  flex: 1;
  min-height: 0;
  overflow: auto !important;
}

.point-name,
.point-code,
.addr {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.point-name {
  color: var(--omes-color-text);
  font-weight: 600;
}

.point-code,
.point-time,
.addr {
  font-variant-numeric: tabular-nums;
}

.point-code,
.addr {
  font-family: "Cascadia Mono", "Segoe UI Mono", Consolas, monospace;
  font-size: 12px;
}

.addr.muted,
.point-time {
  color: var(--omes-color-text-tertiary);
}

.point-value {
  display: inline-flex;
  justify-content: flex-end;
  min-width: 72px;
  padding: 1px 8px;
  border-radius: 6px;
  background: var(--omes-color-primary-bg);
  color: var(--omes-color-primary);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.point-value.error {
  background: color-mix(in srgb, var(--omes-color-error) 10%, transparent);
  color: var(--omes-color-error);
}

.point-value.empty {
  background: transparent;
  color: var(--omes-color-text-quaternary);
  font-weight: 400;
}

.point-empty {
  margin: auto;
}

@media (prefers-reduced-motion: no-preference) {
  .status-pill.online .status-dot {
    animation: status-pulse 1.8s ease-out infinite;
  }
}

@keyframes status-pulse {
  0% { box-shadow: 0 0 0 0 color-mix(in srgb, var(--omes-color-success) 40%, transparent); }
  100% { box-shadow: 0 0 0 6px transparent; }
}

@media screen and (max-width: 1024px) {
  .realtime-points-page .page-header {
    flex-direction: column;
  }

  .refresh-select {
    width: 100%;
  }
}
</style>
