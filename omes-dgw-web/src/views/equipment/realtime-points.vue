<template>
  <div class="page-container realtime-points-page">
    <div class="page-header">
      <h2>实时点位 - {{ deviceName }}</h2>
      <a-space>
        <a-select v-model:value="refreshInterval" style="width: 140px" @change="restartPolling">
          <a-select-option :value="5000">每5秒刷新</a-select-option>
          <a-select-option :value="10000">每10秒刷新</a-select-option>
          <a-select-option :value="30000">每30秒刷新</a-select-option>
          <a-select-option :value="60000">每60秒刷新</a-select-option>
        </a-select>
        <a-button @click="goBack">
          <LeftOutlined />
          返回
        </a-button>
      </a-space>
    </div>

    <a-card class="device-card" size="small" style="margin-bottom: 16px">
      <a-descriptions :column="4" bordered size="small">
        <a-descriptions-item label="设备编码">{{ deviceInfo.code || '-' }}</a-descriptions-item>
        <a-descriptions-item label="设备名称">{{ deviceInfo.name || '-' }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="deviceInfo.status === 'online' ? 'green' : 'default'">
            {{ deviceInfo.status === 'online' ? '在线' : '离线' }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="更新时间">{{ formatTime(deviceInfo.lastUpdateTime) }}</a-descriptions-item>
      </a-descriptions>
    </a-card>

    <a-card class="point-card" title="实时点位">
      <a-table
        :columns="pointColumns"
        :data-source="pointList"
        :pagination="false"
        size="small"
        row-key="key"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'actualAddress'">
            {{ record.actualAddress || '-' }}
          </template>
          <template v-if="column.key === 'setpointAddress'">
            {{ record.setpointAddress || '-' }}
          </template>
          <template v-if="column.key === 'value'">
            <span :class="['point-value', { error: record.error }]">
              {{ record.error || formatPointValue(record.value) }}
            </span>
          </template>
          <template v-if="column.key === 'time'">
            {{ formatTime(record.time || record.updateTime || record.timestamp) }}
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
let refreshTimer = null
let dataLoading = false
let pollingActive = false

const deviceName = computed(() => deviceInfo.value.name || route.query.name || route.params.id || '-')

const pointColumns = [
  { title: '点位名称', dataIndex: 'name', key: 'name', width: 180 },
  { title: '点位编码', dataIndex: 'code', key: 'code', width: 160 },
  { title: '实际值点位', dataIndex: 'actualAddress', key: 'actualAddress', width: 160 },
  { title: '设定值点位', dataIndex: 'setpointAddress', key: 'setpointAddress', width: 160 },
  { title: '实时值', key: 'value', width: 140 },
  { title: '更新时间', key: 'time', width: 180 }
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

.realtime-points-page .page-header,
.realtime-points-page .device-card {
  flex-shrink: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #0078d4;
  border-left: 4px solid #0078d4;
  padding-left: 12px;
  height: 24px;
  line-height: 24px;
}

.point-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.point-card :deep(.ant-card-body) {
  flex: 1;
  min-height: 0;
  padding: 10px;
  overflow: auto;
}

.point-value {
  color: #1677ff;
  font-weight: 700;
}

.point-value.error {
  color: #f5222d;
}
</style>
