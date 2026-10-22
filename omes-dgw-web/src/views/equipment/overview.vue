<template>
  <div class="page-container equipment-overview-page">
    <div class="page-header">
      <h2>设备监控</h2>
      <a-select v-model:value="refreshInterval" class="refresh-select" @change="restartPolling">
        <a-select-option :value="5000">每5秒刷新</a-select-option>
        <a-select-option :value="10000">每10秒刷新</a-select-option>
        <a-select-option :value="30000">每30秒刷新</a-select-option>
        <a-select-option :value="60000">每60秒刷新</a-select-option>
      </a-select>
    </div>

    <div class="stat-strip" aria-label="设备状态">
      <div class="stat-item">
        <span class="stat-value">{{ statData.total }}</span>
        <span class="stat-label">设备总数</span>
      </div>
      <div class="stat-item online">
        <span class="stat-value">{{ statData.online }}</span>
        <span class="stat-label">在线</span>
      </div>
      <div class="stat-item offline">
        <span class="stat-value">{{ statData.offline }}</span>
        <span class="stat-label">离线</span>
      </div>
    </div>

    <div v-if="filteredList.length" class="equipment-grid">
      <article
        v-for="item in filteredList"
        :key="item.id"
        class="device-card"
        :class="item.status"
        tabindex="0"
        @click="viewRealtimePoints(item)"
        @keydown.enter="viewRealtimePoints(item)"
      >
        <div class="card-head">
          <img
            class="device-thumb"
            :src="deviceThumb(item)"
            :alt="item.imageUrl ? item.name : ''"
            @error="onThumbError"
          />
          <div class="card-id">
            <h3 class="device-name" :title="item.name">{{ item.name }}</h3>
            <p class="device-meta">
              <span>{{ item.code || '-' }}</span>
              <span>{{ item.protocol || '-' }}</span>
            </p>
          </div>
          <span :class="['status-pill', item.status === 'online' ? 'online' : 'offline']">
            <span class="status-dot"></span>
            {{ getStatusText(item.status) }}
          </span>
        </div>

        <div class="fact-row">
          <span class="endpoint" :title="endpointOf(item)">{{ endpointOf(item) }}</span>
          <span class="updated">{{ formatTime(item.lastUpdateTime) }}</span>
        </div>

        <div class="conn-row">
          <span :class="['status-pill', getConnectionStatusClass(item)]">
            <span class="status-dot"></span>
            {{ getConnectionStatusLabel(item) || '未知' }}
          </span>
          <a-button
            size="small"
            :danger="isConnected(item)"
            :loading="connectionLoadingId === item.id"
            :disabled="isConnectionButtonDisabled(item)"
            @click.stop="handleToggleConnection(item)"
          >
            {{ isConnected(item) ? '断开' : '连接' }}
          </a-button>
        </div>

        <p v-if="deviceMessage(item)" class="device-message" :title="deviceMessage(item)">{{ deviceMessage(item) }}</p>

        <div class="card-actions" @click.stop>
          <a-button size="small" @click="viewHistory(item)">
            <LineChartOutlined />
            采集
          </a-button>
          <a-button size="small" @click="viewCollectMetrics(item)">
            <DashboardOutlined />
            性能
          </a-button>
          <a-button v-if="item.status === 'online'" size="small" @click="openWriteModal(item)">
            <EditOutlined />
            测写
          </a-button>
        </div>
      </article>
    </div>

    <a-empty v-else class="device-empty" description="暂无设备数据" />

    <a-modal
      v-model:open="writeVisible"
      title="测试写入"
      width="560px"
      :mask-closable="false"
      ok-text="测试"
      cancel-text="取消"
      :confirm-loading="writeLoading"
      @ok="handleWriteSubmit"
      @cancel="handleWriteCancel"
    >
      <a-form layout="vertical" :model="writeForm">
        <a-form-item label="写入方式" required>
          <a-radio-group v-model:value="writeForm.mode" @change="handleWriteModeChange">
            <a-radio-button value="configured">已配置属性</a-radio-button>
            <a-radio-button value="free">自由地址</a-radio-button>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="isConfiguredWriteMode" label="写入属性" required>
          <a-select
            v-model:value="writeForm.pointCode"
            show-search
            allow-clear
            :options="writePointOptions"
            :loading="writePointLoading"
            :filter-option="filterWritePointOption"
            placeholder="请选择已配置的写入属性"
            @change="handleWritePointChange"
          />
          <div v-if="!writePointLoading && writePointOptions.length === 0" class="write-address-tip">
            当前设备未配置可写入点位，可切换到「自由地址」直接填写。
          </div>
        </a-form-item>
        <a-form-item label="写入地址" :required="!isConfiguredWriteMode">
          <a-input
            v-model:value="writeForm.address"
            :disabled="isConfiguredWriteMode"
            :placeholder="isConfiguredWriteMode ? '由设定值点位自动带出' : '请输入写入地址'"
            @blur="handleWriteAddressBlur"
          />
          <div class="write-address-tip">{{ writeAddressTip }}</div>
        </a-form-item>
        <a-form-item label="数据类型" :required="!isConfiguredWriteMode">
          <a-select
            v-model:value="writeForm.dataType"
            :disabled="isConfiguredWriteMode"
            :loading="writeDataTypeLoading"
            :options="writeDataTypeOptions"
            :placeholder="isConfiguredWriteMode ? '由写入属性自动带出' : '请选择数据类型'"
          />
        </a-form-item>
        <a-form-item label="字节序">
          <a-select
            v-model:value="writeForm.byteOrder"
            :disabled="isConfiguredWriteMode"
            :loading="writeByteOrderLoading"
            :options="writeByteOrderOptions"
            :placeholder="isConfiguredWriteMode ? '由写入属性自动带出' : '请选择字节序'"
          />
        </a-form-item>
        <a-form-item label="缩放系数">
          <a-input-number
            v-model:value="writeForm.scaleFactor"
            :disabled="isConfiguredWriteMode"
            :min="0"
            style="width: 100%"
            :placeholder="isConfiguredWriteMode ? undefined : '可选，默认 1'"
          />
        </a-form-item>
        <a-form-item label="写入值" required>
          <a-input v-model:value="writeForm.value" placeholder="请输入写入值" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  DashboardOutlined,
  EditOutlined,
  LineChartOutlined
} from '@ant-design/icons-vue'
import defaultDeviceThumb from '@/assets/device-thumb.svg'
import {
  closeDeviceRealtimeConnection,
  getDeviceDetail,
  getDevicePointByteOrders,
  getDevicePointDataTypes,
  getDeviceRealtimeList,
  openDeviceRealtimeConnection,
  writeDevicePoint
} from '@/api/equipment'

const router = useRouter()

const equipmentList = ref([])
const refreshInterval = ref(5000)
const writeVisible = ref(false)
const writeLoading = ref(false)
const connectionLoadingId = ref(null)
const connectionCoolingIds = ref([])
const writeDataTypeLoading = ref(false)
const writeDataTypeOptions = ref([])
const writeByteOrderLoading = ref(false)
const writeByteOrderOptions = ref([])
const writePointLoading = ref(false)
const writePointOptions = ref([])
const writePointMap = ref({})
const writeDevice = ref(null)
let refreshTimer = null
let dataLoading = false
let pollingActive = false
let connectionCoolingTimers = []

const WRITE_ACCESS_TYPES = ['WRITE_ONLY', 'READ_WRITE']
const WRITE_MODE_CONFIGURED = 'configured'
const WRITE_MODE_FREE = 'free'

const writeForm = reactive({
  mode: WRITE_MODE_CONFIGURED,
  pointCode: undefined,
  address: undefined,
  dataType: undefined,
  byteOrder: undefined,
  scaleFactor: undefined,
  engValueMin: undefined,
  engValueMax: undefined,
  value: ''
})

const isConfiguredWriteMode = computed(() => writeForm.mode === WRITE_MODE_CONFIGURED)

const statData = computed(() => {
  const list = equipmentList.value
  return {
    total: list.length,
    online: list.filter(i => i.status === 'online').length,
    offline: list.filter(i => i.status === 'offline').length
  }
})

const filteredList = computed(() => {
  return [...equipmentList.value]
})

const protocolAddressTips = {
  MODBUS_TCP: 'Modbus TCP 地址示例：线圈 00001、离散输入 10001、输入寄存器 30001、保持寄存器 40001。',
  FINS_TCP: 'FINS 地址示例：D100（DM 字）、D100.05（DM 位）、CIO100、W0、H0、A0。',
  SLMP_TCP: 'SLMP 地址示例：D100（数据寄存器）、M100（内部继电器）、X10/Y20（八进制 I/O）、D100.5（字内位）。',
  S7_TCP: 'S7 地址示例：DB1.DBD0、DB1.DBX0.0、VW100、VD100、IB0、QB0、M0.0。',
  OPC_UA: 'OPC UA 地址为节点 ID，例如 ns=2;s=Channel.Device.Tag 或 ns=2;i=1001。'
}

const writeAddressTip = computed(() => {
  const protocol = writeDevice.value?.protocol

  return protocolAddressTips[protocol] || '请输入写入地址。'
})

const getResponseData = response => response?.data ?? response

const normalizePoints = points => Object.entries(points || {}).map(([key, point]) => ({
  key,
  ...point
}))

const getRealtimeStatus = item => {
  return item.online ? 'online' : 'offline'
}

const normalizeDevice = item => {
  const pointList = normalizePoints(item.points)

  return {
    ...item,
    id: item.deviceId,
    code: item.deviceCode,
    name: item.deviceName,
    status: getRealtimeStatus(item),
    pointList
  }
}

const loadData = async () => {
  if (dataLoading) return

  dataLoading = true
  try {
    const response = await getDeviceRealtimeList()
    equipmentList.value = getResponseData(response).map(normalizeDevice)
  } catch {
    equipmentList.value = []
  } finally {
    dataLoading = false
  }
}

const getStatusText = (status) => {
  const map = {
    online: '在线',
    offline: '离线'
  }
  return map[status] || '未知'
}

const getConnectionStatusLabel = item => {
  return item.connectionStatusLabel
}

const getConnectionStatus = item => String(item.connectionStatus || '').toLowerCase()

const isConnected = item => getConnectionStatus(item) === 'connected'

const isConnectionCooling = item => connectionCoolingIds.value.includes(item.id)

const isConnectionButtonDisabled = item => {
  return getConnectionStatus(item) === 'unsupported' || isConnectionCooling(item)
}

const getConnectionStatusClass = item => {
  const status = getConnectionStatus(item)

  if (status === 'connected') return 'connected'
  if (status === 'half_open') return 'half-open'
  if (status === 'circuit_abandoned') return 'circuit'
  if (status === 'unsupported') return 'unsupported'

  return 'disconnected'
}

// shortcut: 台账还没有图片字段，以后设备带 imageUrl 时这里会换成实图
const deviceThumb = item => item?.imageUrl || defaultDeviceThumb

const onThumbError = event => {
  const img = event.target
  if (img.dataset.fallback) return
  img.dataset.fallback = '1'
  img.src = defaultDeviceThumb
}

const endpointOf = item => {
  if (item?.ipAddress && item.port != null && item.port !== '') return `${item.ipAddress}:${item.port}`
  return item?.ipAddress || '-'
}

const deviceMessage = item => {
  const text = item?.message || item?.connectionStatusMessage || ''
  if (!text || text === item?.connectionStatusLabel) return ''
  return text
}

const handleToggleConnection = async item => {
  if (!item?.id || connectionLoadingId.value || isConnectionButtonDisabled(item)) return

  connectionLoadingId.value = item.id

  try {
    if (isConnected(item)) {
      await closeDeviceRealtimeConnection(item.id)
      message.success('断开连接成功')
    } else {
      await openDeviceRealtimeConnection(item.id)
      message.success('连接成功')
    }

    await loadData()
  } catch {
    // 错误提示已由 request 拦截器处理，此处仅避免事件处理器未捕获异常
  } finally {
    connectionLoadingId.value = null
    startConnectionCooling(item.id)
  }
}

const startConnectionCooling = deviceId => {
  if (!connectionCoolingIds.value.includes(deviceId)) {
    connectionCoolingIds.value.push(deviceId)
  }

  const timer = setTimeout(() => {
    connectionCoolingIds.value = connectionCoolingIds.value.filter(id => id !== deviceId)
    connectionCoolingTimers = connectionCoolingTimers.filter(item => item !== timer)
  }, 3000)

  connectionCoolingTimers.push(timer)
}

const formatTime = value => {
  if (!value) return '-'

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value

  return date.toLocaleString()
}

const viewHistory = (item) => {
  const pointNames = item.pointList
    .map(point => point.name)
    .filter(Boolean)

  router.push({
    path: `/equipment/history/${item.id}`,
    query: {
      name: item.name || item.code || '',
      points: pointNames.join(',')
    }
  })
}

const viewCollectMetrics = (item) => {
  router.push({
    path: `/equipment/collect-metrics/${item.id}`,
    query: {
      name: item.name || item.code || ''
    }
  })
}

const viewRealtimePoints = (item) => {
  router.push({
    path: `/equipment/realtime/${item.id}`,
    query: {
      name: item.name || item.code || ''
    }
  })
}

const resetWriteForm = () => {
  writeForm.mode = WRITE_MODE_CONFIGURED
  writeForm.pointCode = undefined
  writeForm.address = undefined
  writeForm.dataType = undefined
  writeForm.byteOrder = undefined
  writeForm.scaleFactor = undefined
  writeForm.engValueMin = undefined
  writeForm.engValueMax = undefined
  writeForm.value = ''
}

const applyFreeWriteDefaults = () => {
  setDefaultWriteByteOrder()
}

const findWritablePointByAddress = address => {
  const normalized = String(address || '').trim().toLowerCase()
  if (!normalized) return null
  return Object.values(writePointMap.value).find(point => {
    if (!point) return false
    const setpoint = String(point.setpointAddress || '').trim().toLowerCase()
    const actual = String(point.actualAddress || '').trim().toLowerCase()
    return setpoint === normalized || actual === normalized
  }) || null
}

const handleWriteAddressBlur = () => {
  if (isConfiguredWriteMode.value) return
  const point = findWritablePointByAddress(writeForm.address)
  if (!point) return
  if (!writeForm.dataType && point.dataType) {
    writeForm.dataType = point.dataType
  }
  if (!writeForm.byteOrder && point.byteOrder) {
    writeForm.byteOrder = point.byteOrder
  }
  if ((writeForm.scaleFactor == null || writeForm.scaleFactor === '') && point.scaleFactor != null) {
    writeForm.scaleFactor = point.scaleFactor
  }
  writeForm.engValueMin = point.engValueMin
  writeForm.engValueMax = point.engValueMax
}

const handleWriteModeChange = event => {
  const mode = event?.target?.value ?? writeForm.mode
  writeForm.pointCode = undefined
  writeForm.address = undefined
  writeForm.dataType = undefined
  writeForm.byteOrder = undefined
  writeForm.scaleFactor = undefined
  writeForm.engValueMin = undefined
  writeForm.engValueMax = undefined

  if (mode === WRITE_MODE_FREE) {
    applyFreeWriteDefaults()
  }
}

const normalizeDataTypeOptions = list => list
  .map(item => {
    return item.code ? {
      label: item.label,
      value: item.code
    } : null
  })
  .filter(Boolean)

const normalizeByteOrderOptions = list => (Array.isArray(list) ? list : [])
  .filter(item => item?.supported !== false)
  .map(item => (
    item?.code && item?.label
      ? {
          label: String(item.label),
          value: item.code
        }
      : null
  ))
  .filter(Boolean)

const isWritableAccessType = accessType => WRITE_ACCESS_TYPES.includes(String(accessType || '').toUpperCase())

const normalizeWritePointOptions = list => (Array.isArray(list) ? list : [])
  .filter(item => item?.code && isWritableAccessType(item.accessType))
  .map(item => {
    const code = String(item.code).trim()
    const name = String(item.name || '').trim()
    return {
      label: name ? `${code}（${name}）` : code,
      value: code,
      point: item
    }
  })

const filterWritePointOption = (input, option) => {
  const keyword = String(input || '').trim().toLowerCase()
  if (!keyword) return true
  return String(option?.label || '').toLowerCase().includes(keyword)
    || String(option?.value || '').toLowerCase().includes(keyword)
}

const applyWritePoint = point => {
  if (!point) {
    writeForm.address = undefined
    writeForm.dataType = undefined
    writeForm.byteOrder = undefined
    writeForm.scaleFactor = undefined
    writeForm.engValueMin = undefined
    writeForm.engValueMax = undefined
    return
  }

  writeForm.address = point.setpointAddress || undefined
  writeForm.dataType = point.dataType || undefined
  writeForm.byteOrder = point.byteOrder || writeByteOrderOptions.value.find(item => item.value === 'CDAB')?.value
    || writeByteOrderOptions.value[0]?.value
  writeForm.scaleFactor = point.scaleFactor ?? 1
  writeForm.engValueMin = point.engValueMin
  writeForm.engValueMax = point.engValueMax
}

const handleWritePointChange = pointCode => {
  applyWritePoint(pointCode ? writePointMap.value[pointCode] : null)
}

const loadWritePoints = async deviceId => {
  writePointLoading.value = true
  writePointOptions.value = []
  writePointMap.value = {}

  try {
    const response = await getDeviceDetail(deviceId)
    const options = normalizeWritePointOptions(getResponseData(response))
    writePointOptions.value = options
    writePointMap.value = Object.fromEntries(options.map(item => [item.value, item.point]))
  } catch {
    writePointOptions.value = []
    writePointMap.value = {}
  } finally {
    writePointLoading.value = false
  }
}

const setDefaultWriteByteOrder = () => {
  if (writeForm.byteOrder || !writeByteOrderOptions.value.length) return

  writeForm.byteOrder = writeByteOrderOptions.value.find(item => item.value === 'CDAB')?.value ||
    writeByteOrderOptions.value[0].value
}

const loadWriteDataTypes = async deviceId => {
  writeDataTypeLoading.value = true
  writeDataTypeOptions.value = []

  try {
    const response = await getDevicePointDataTypes(deviceId)
    writeDataTypeOptions.value = normalizeDataTypeOptions(getResponseData(response))
  } catch (error) {
    writeDataTypeOptions.value = []
  } finally {
    writeDataTypeLoading.value = false
  }
}

const loadWriteByteOrders = async deviceId => {
  writeByteOrderLoading.value = true
  writeByteOrderOptions.value = []

  try {
    const response = await getDevicePointByteOrders(deviceId)
    writeByteOrderOptions.value = normalizeByteOrderOptions(getResponseData(response))
    setDefaultWriteByteOrder()
  } catch (error) {
    writeByteOrderOptions.value = []
  } finally {
    writeByteOrderLoading.value = false
  }
}

const openWriteModal = async (item) => {
  writeDevice.value = item
  resetWriteForm()
  writeVisible.value = true
  await Promise.all([
    loadWritePoints(item.id),
    loadWriteDataTypes(item.id),
    loadWriteByteOrders(item.id)
  ])

  if (writePointOptions.value.length === 0) {
    writeForm.mode = WRITE_MODE_FREE
    applyFreeWriteDefaults()
  }
}

const handleWriteCancel = () => {
  writeVisible.value = false
  writeDevice.value = null
  writePointOptions.value = []
  writePointMap.value = {}
  resetWriteForm()
}

const handleWriteSubmit = async () => {
  if (!writeDevice.value?.id) {
    message.warning('缺少设备ID')
    return
  }

  if (writeForm.value === '') {
    message.warning('请填写写入值')
    return
  }

  const payload = {
    taskId: `collect-ui-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    deviceId: String(writeDevice.value.id),
    value: String(writeForm.value)
  }

  if (isConfiguredWriteMode.value) {
    if (!writeForm.pointCode) {
      message.warning('请选择写入属性')
      return
    }

    const numericValue = Number(writeForm.value)
    if (!Number.isNaN(numericValue)) {
      if (writeForm.engValueMin != null && writeForm.engValueMin !== '' && numericValue < Number(writeForm.engValueMin)) {
        message.warning(`写入值不能低于工程值下限 ${writeForm.engValueMin}`)
        return
      }
      if (writeForm.engValueMax != null && writeForm.engValueMax !== '' && numericValue > Number(writeForm.engValueMax)) {
        message.warning(`写入值不能高于工程值上限 ${writeForm.engValueMax}`)
        return
      }
    }

    payload.code = String(writeForm.pointCode)
    if (writeForm.scaleFactor != null && writeForm.scaleFactor !== '') {
      payload.scaleFactor = writeForm.scaleFactor
    }
  } else {
    const address = String(writeForm.address || '').trim()
    if (!address) {
      message.warning('请填写写入地址')
      return
    }
    if (!writeForm.dataType) {
      message.warning('请选择数据类型')
      return
    }

    payload.address = address
    payload.dataType = writeForm.dataType
    if (writeForm.byteOrder) {
      payload.byteOrder = writeForm.byteOrder
    }
    if (writeForm.scaleFactor != null && writeForm.scaleFactor !== '') {
      payload.scaleFactor = writeForm.scaleFactor
    }
  }

  writeLoading.value = true

  try {
    await writeDevicePoint(payload)
    message.success('测试成功')
    handleWriteCancel()
  } finally {
    writeLoading.value = false
  }
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
  connectionCoolingTimers.forEach(timer => clearTimeout(timer))
  connectionCoolingTimers = []
})
</script>

<style scoped>
.refresh-select {
  width: 140px;
}

.stat-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.stat-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
  padding: 14px 16px;
  border-radius: var(--omes-border-radius-x-large);
  background: var(--omes-color-bg-container);
  box-shadow: var(--omes-fluent-surface-shadow, var(--omes-shadow-card));
}

.stat-value {
  color: var(--omes-color-text);
  font-size: 24px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.stat-item.online .stat-value {
  color: var(--omes-color-success);
}

.stat-item.offline .stat-value {
  color: var(--omes-color-text-tertiary);
}

.stat-label {
  color: var(--omes-color-text-tertiary);
  font-size: 13px;
}

.equipment-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 360px), 1fr));
  gap: 12px;
}

.device-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  padding: 16px;
  border: 1px solid transparent;
  border-radius: var(--omes-border-radius-x-large);
  background: var(--omes-color-bg-container);
  box-shadow: var(--omes-fluent-surface-shadow, var(--omes-shadow-card));
  cursor: pointer;
}

.device-card.online {
  box-shadow: inset 3px 0 0 var(--omes-color-success), var(--omes-fluent-surface-shadow, var(--omes-shadow-card));
}

.device-card:hover {
  border-color: color-mix(in srgb, var(--omes-color-primary) 28%, transparent);
}

.device-card:focus-visible {
  outline: 2px solid var(--omes-color-primary);
  outline-offset: 2px;
}

.card-head,
.fact-row,
.conn-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.device-thumb {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: 8px;
  object-fit: cover;
  background: var(--omes-color-bg-muted);
}

.card-id {
  flex: 1;
  min-width: 0;
}

.device-name {
  margin: 0;
  overflow: hidden;
  color: var(--omes-color-text);
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.device-meta {
  display: flex;
  gap: 8px;
  margin: 2px 0 0;
  color: var(--omes-color-text-tertiary);
  font-size: 13px;
}

.device-meta span + span::before {
  content: '·';
  margin-right: 8px;
}

.endpoint,
.updated {
  min-width: 0;
  overflow: hidden;
  color: var(--omes-color-text-secondary);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.updated {
  flex-shrink: 0;
  color: var(--omes-color-text-tertiary);
}

.status-pill {
  display: inline-flex;
  flex-shrink: 0;
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

.status-pill.half-open,
.status-pill.unsupported {
  color: var(--omes-color-warning);
  background: color-mix(in srgb, var(--omes-color-warning) 12%, transparent);
}

.status-pill.circuit {
  color: var(--omes-color-error);
  background: color-mix(in srgb, var(--omes-color-error) 10%, transparent);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.device-message {
  margin: 0;
  padding: 8px 10px;
  overflow: hidden;
  border-radius: 6px;
  background: color-mix(in srgb, var(--omes-color-error) 8%, transparent);
  color: var(--omes-color-error);
  font-size: 13px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-actions {
  display: flex;
  gap: 8px;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid var(--omes-fluent-divider, rgba(0, 0, 0, 0.04));
}

.card-actions :deep(.ant-btn) {
  flex: 1;
}

.device-empty {
  margin-top: 48px;
}

.write-address-tip {
  margin-top: 6px;
  color: var(--omes-color-text-tertiary);
  font-size: 13px;
  line-height: 1.5;
}

@media (prefers-reduced-motion: no-preference) {
  .status-pill.online .status-dot,
  .status-pill.connected .status-dot {
    animation: status-pulse 1.8s ease-out infinite;
  }
}

@keyframes status-pulse {
  0% { box-shadow: 0 0 0 0 color-mix(in srgb, var(--omes-color-success) 40%, transparent); }
  100% { box-shadow: 0 0 0 6px transparent; }
}

@media screen and (max-width: 640px) {
  .stat-strip {
    grid-template-columns: 1fr;
  }

  .refresh-select {
    width: 100%;
  }
}
</style>