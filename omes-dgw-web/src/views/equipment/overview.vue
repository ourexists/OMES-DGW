<template>
  <div class="page-container">
    <div class="page-header">
      <h2>设备总览</h2>
      <a-space>
        <a-select v-model:value="refreshInterval" style="width: 140px" @change="restartPolling">
          <a-select-option :value="5000">每5秒刷新</a-select-option>
          <a-select-option :value="10000">每10秒刷新</a-select-option>
          <a-select-option :value="30000">每30秒刷新</a-select-option>
          <a-select-option :value="60000">每60秒刷新</a-select-option>
        </a-select>
      </a-space>
    </div>

    <!-- 设备状态统计 -->
    <div class="stats-flow">
      <a-card size="small" class="stat-flow-item">
        <a-statistic
          title="设备总数"
          :value="statData.total"
          suffix="台"
          :value-style="{ color: '#1677ff' }"
        />
      </a-card>
      <a-card size="small" class="stat-flow-item">
        <a-statistic
          title="在线"
          :value="statData.online"
          suffix="台"
          :value-style="{ color: '#52c41a' }"
        />
      </a-card>
      <a-card size="small" class="stat-flow-item">
        <a-statistic
          title="离线"
          :value="statData.offline"
          suffix="台"
          :value-style="{ color: '#8c8c8c' }"
        />
      </a-card>
    </div>

    <!-- 设备卡片列表 -->
    <div class="equipment-grid">
      <a-card
        v-for="item in filteredList"
        :key="item.id"
        class="equipment-card"
        :class="getCardClass(item.status)"
        hoverable
        @click="viewRealtimePoints(item)"
      >
          <div class="card-accent" :class="item.status === 'online' ? 'accent-online' : 'accent-offline'"></div>

          <div class="device-summary">
            <div class="device-icon">
              <DesktopOutlined />
              <CheckCircleFilled v-if="item.status === 'online'" class="online-check" />
            </div>
            <div class="device-title">
              <div class="equipment-code" :title="item.name">{{ item.name }}</div>
              <div class="card-name" :title="item.code">{{ item.code }}</div>
              <div class="device-subtitle" :title="getDeviceSubtitle(item)">{{ getDeviceSubtitle(item) }}</div>
            </div>
          </div>

          <div class="card-status-bar">
            <span :class="['status-pill', item.status === 'online' ? 'online' : 'offline']">
              <span class="status-dot"></span>
              {{ getStatusText(item.status) }}
            </span>
            <span :class="['connection-pill', getConnectionStatusClass(item)]">
              <span class="connection-dot"></span>
              {{ getConnectionStatusLabel(item) }}
            </span>
            <a-button
              type="link"
              size="small"
              class="connect-btn"
              :loading="connectionLoadingId === item.id"
              :disabled="isConnectionButtonDisabled(item)"
              @click.stop="handleToggleConnection(item)"
            >
              {{ isConnected(item) ? '断开' : '连接' }}
            </a-button>
          </div>

          <div class="card-info">
            <div class="info-grid">
              <div class="info-item">
                <span class="label">IP 地址</span>
                <span class="value" :title="item.ipAddress">{{ item.ipAddress || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="label">端口</span>
                <span class="value">{{ item.port ?? '-' }}</span>
              </div>
              <div class="info-item">
                <span class="label">协议</span>
                <span class="value">{{ item.protocol || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="label">更新时间</span>
                <span class="value" :title="formatTime(item.lastUpdateTime)">{{ formatTime(item.lastUpdateTime) }}</span>
              </div>
            </div>
            <div v-if="item.message" class="device-message">{{ item.message }}</div>
          </div>
          
          <div class="card-actions">
            <a-button size="small" @click.stop="viewHistory(item)">
              <LineChartOutlined />
              采集
            </a-button>
            <a-button size="small" @click.stop="viewCollectMetrics(item)">
              <DashboardOutlined />
              性能
            </a-button>
            <a-button v-if="item.status === 'online'" size="small" @click.stop="openWriteModal(item)">
              <EditOutlined />
              测写
            </a-button>
          </div>
        </a-card>
    </div>

    <a-empty v-if="filteredList.length === 0" description="暂无设备数据" style="margin-top: 50px" />

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
  CheckCircleFilled,
  DashboardOutlined,
  DesktopOutlined,
  EditOutlined,
  LineChartOutlined
} from '@ant-design/icons-vue'
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
  SLMP_TCP: 'SLMP 地址示例：D100（数据寄存器）、M100（内部继电器）、X10/Y20（八进制 I/O）、D100.5（字内位）。'
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

const getCardClass = (status) => {
  const map = {
    online: 'card-running',
    offline: 'card-idle'
  }
  return map[status] || ''
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
  if (status === 'unsupported') return 'unsupported'

  return 'disconnected'
}

const getDeviceSubtitle = item => {
  return item.connectionStatusMessage || '-'
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
.equipment-grid {
  --card-scale: 1.2;
  --card-min-width: 336px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, var(--card-min-width)), 1fr));
  gap: clamp(14px, 1.9vw, 24px);
  width: 100%;
  align-items: stretch;
}

.equipment-card {
  position: relative;
  container-type: inline-size;
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--omes-color-border-secondary);
  box-shadow: var(--omes-shadow-card);
  height: 100%;
  width: 100%;
  min-width: 0;
  background: var(--omes-color-bg-container);
}

.equipment-card :deep(.ant-card-body) {
  padding: 0 clamp(14px, 2.4vw, 19px) 0;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.equipment-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--omes-shadow-primary-soft);
  border-color: color-mix(in srgb, var(--omes-color-primary) 28%, transparent);
}

.card-accent {
  height: 5px;
  margin: 0 clamp(-19px, -2.4vw, -14px);
  flex-shrink: 0;
}

.card-accent.accent-online {
  background: linear-gradient(90deg, var(--omes-color-success), var(--omes-color-chart-palette-5));
}

.card-accent.accent-offline {
  background: linear-gradient(90deg, var(--omes-color-text-quaternary), var(--omes-color-text-placeholder));
}

.device-summary {
  display: flex;
  align-items: center;
  gap: clamp(14px, 2.4vw, 20px);
  padding-top: clamp(17px, 3vw, 23px);
  margin-bottom: clamp(12px, 2.2vw, 17px);
}

.device-icon {
  width: clamp(67px, 17cqw, 80px);
  min-width: clamp(67px, 17cqw, 80px);
  height: clamp(67px, 17cqw, 80px);
  border-radius: 17px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--omes-color-success-bg);
  color: var(--omes-color-success);
  font-size: clamp(31px, 7.2cqw, 37px);
  position: relative;
  flex-shrink: 0;
}

.card-idle .device-icon {
  background: var(--omes-color-bg-muted);
  color: var(--omes-color-text-secondary);
}

.online-check {
  position: absolute;
  right: -6px;
  bottom: -6px;
  color: var(--omes-color-success);
  background: #fff;
  border-radius: 50%;
  font-size: 23px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.12);
}

.device-title {
  min-width: 0;
  flex: 1;
}

.equipment-code {
  font-weight: 600;
  font-size: 19px;
  color: var(--omes-color-text);
  line-height: 1.5;
  margin-bottom: 5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-name {
  font-size: 16px;
  color: var(--omes-color-primary);
  margin-bottom: 6px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.device-subtitle {
  font-size: 14px;
  color: var(--omes-color-text-tertiary);
  line-height: 1.5;
  min-height: 22px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-status-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 17px;
}

.connect-btn {
  margin-left: auto;
  padding: 0 5px;
  height: auto;
  font-size: 14px;
}

.connection-pill,
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 31px;
  padding: 0 12px;
  border-radius: 16px;
  font-size: 14px;
  font-weight: 500;
}

.connection-pill.connected,
.status-pill.online {
  color: var(--omes-color-success);
  background: var(--omes-color-success-bg);
}

.connection-pill.disconnected,
.status-pill.offline {
  color: var(--omes-color-text-secondary);
  background: var(--omes-color-bg-muted);
}

.connection-pill.unsupported {
  color: var(--omes-color-warning);
  background: color-mix(in srgb, var(--omes-color-warning) 12%, transparent);
}

.connection-dot,
.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.connection-pill.connected .connection-dot,
.status-pill.online .status-dot {
  background: var(--omes-color-success);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--omes-color-success) 20%, transparent);
}

.connection-pill.disconnected .connection-dot,
.status-pill.offline .status-dot {
  background: var(--omes-color-text-quaternary);
}

.connection-pill.unsupported .connection-dot {
  background: var(--omes-color-warning);
}

.card-info {
  margin-bottom: 17px;
  flex: 1;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 144px), 1fr));
  gap: clamp(10px, 1.8vw, 12px) clamp(12px, 1.8vw, 14px);
  padding: clamp(12px, 2.2vw, 14px) clamp(14px, 2.4vw, 17px);
  background: var(--omes-color-bg-layout);
  border-radius: 10px;
  border: 1px solid var(--omes-color-border-secondary);
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.info-item .label {
  font-size: 14px;
  color: var(--omes-color-text-tertiary);
}

.info-item .value {
  font-size: 16px;
  color: var(--omes-color-text);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.device-message {
  margin-top: 12px;
  padding: 8px 14px;
  color: var(--omes-color-error);
  font-size: 14px;
  background: color-mix(in srgb, var(--omes-color-error) 8%, transparent);
  border-radius: 7px;
  line-height: 1.5;
}

.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: clamp(10px, 1.4vw, 12px);
  margin-top: auto;
  padding-bottom: clamp(17px, 2.4vw, 20px);
  border-top: 1px solid var(--omes-color-border-secondary);
  padding-top: clamp(12px, 2.2vw, 17px);
}

.card-actions button {
  flex: 1 1 calc(33.333% - 10px);
  min-width: 86px;
  height: clamp(41px, 9.6cqw, 46px);
  color: var(--omes-color-primary);
  font-size: clamp(14px, 3.6cqw, 16px);
  font-weight: 500;
  border-color: var(--omes-color-border-secondary);
  border-radius: 7px;
  background: #fff;
}

.card-actions button:hover {
  color: var(--omes-color-primary-hover);
  border-color: var(--omes-color-primary-hover);
  background: var(--omes-color-primary-bg);
}

.write-address-tip {
  margin-top: 6px;
  color: var(--omes-color-text-tertiary);
  font-size: 13px;
  line-height: 1.5;
}

@container (max-width: 384px) {
  .device-summary {
    flex-direction: column;
    text-align: center;
  }

  .device-title {
    width: 100%;
  }

  .card-status-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .connect-btn {
    margin-left: 0;
    width: 100%;
    text-align: center;
  }

  .connection-pill,
  .status-pill {
    width: 100%;
    justify-content: center;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }

  .card-actions button {
    flex: 1 1 100%;
  }
}

@media screen and (max-width: 576px) {
  .equipment-grid {
    --card-min-width: 100%;
  }
}
</style>