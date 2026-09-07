<template>
  <a-modal
    v-model:open="open"
    title="点位扫描"
    width="1200px"
    :mask-closable="false"
    :footer="null"
    @cancel="handleClose"
  >
    <a-form layout="inline" :model="scanForm" class="scan-form">
      <a-form-item label="会话ID" required>
        <a-input v-model:value="scanForm.sessionId" disabled placeholder="自动打开会话" style="width: 220px" />
      </a-form-item>
      <a-form-item label="区域" required>
        <a-select
          v-model:value="scanForm.area"
          :options="scanAreaOptions"
          :loading="scanAreaLoading"
          show-search
          placeholder="请选择区域"
          option-filter-prop="label"
          style="width: 200px"
        />
      </a-form-item>
      <a-form-item label="起始地址" required>
        <a-input-number v-model:value="scanForm.startAddress" :min="0" style="width: 120px" />
      </a-form-item>
      <a-form-item label="数量" required>
        <a-input-number v-model:value="scanForm.quantity" :min="1" style="width: 120px" />
      </a-form-item>
      <a-form-item>
        <a-button
          type="primary"
          :danger="scanButtonState.danger"
          :disabled="!scanForm.sessionId"
          @click="handleToggleScan"
        >
          {{ scanButtonState.text }}
        </a-button>
      </a-form-item>
    </a-form>

    <a-descriptions v-if="scanResult" class="scan-summary" :column="3" bordered size="small">
      <a-descriptions-item label="设备编码">{{ scanResult.deviceCode || '-' }}</a-descriptions-item>
      <a-descriptions-item label="设备名称">{{ scanResult.deviceName || '-' }}</a-descriptions-item>
      <a-descriptions-item label="区域">{{ scanResult.areaLabel || scanResult.area || '-' }}</a-descriptions-item>
      <a-descriptions-item label="连接目标">{{ scanResult.connectionTarget || '-' }}</a-descriptions-item>
      <a-descriptions-item label="返回数量">{{ scanResult.returnedCount ?? scanItems.length }}</a-descriptions-item>
      <a-descriptions-item label="延迟">{{ scanResult.latencyMs ?? '-' }}ms</a-descriptions-item>
      <a-descriptions-item label="扫描时间">{{ formatTime(scanResult.scanTime) }}</a-descriptions-item>
      <a-descriptions-item label="扫描次数">{{ scanCount }}</a-descriptions-item>
    </a-descriptions>

    <div v-if="scanItems.length" class="scan-grid">
      <div v-for="item in scanItems" :key="item.__rowKey" class="scan-item">
        <span class="scan-address">{{ item.legacyAddress || '-' }}</span>
        <span class="scan-binary">{{ item.binaryValue || '-' }}</span>
      </div>
    </div>
    <a-empty v-else description="暂无扫描数据" />
  </a-modal>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  closeModscanSession,
  getModscanAreas,
  scanModbusPoints
} from '@/api/equipment'

const props = defineProps({
  sessionId: {
    type: String,
    default: ''
  }
})

const open = defineModel('open', {
  type: Boolean,
  default: false
})

const scanAreaLoading = ref(false)
const scanLoading = ref(false)
const scanningActive = ref(false)
const scanAreaOptions = ref([])
const scanResult = ref(null)
const scanCount = ref(0)
let scanTimer = null
const closingSessionIds = new Set()

const scanForm = reactive({
  sessionId: '',
  area: undefined,
  startAddress: 0,
  quantity: 100
})

const hasValue = value => value !== undefined && value !== null && value !== ''

const trimText = value => (typeof value === 'string' ? value.trim() : '')

const getResponseData = response => response?.data ?? response

const normalizeScanAreaOptions = data => {
  const list = Array.isArray(data)
    ? data
    : Object.entries(data || {}).map(([value, label]) => ({ value, label }))

  return list
    .map(item => {
      if (typeof item === 'string') {
        return {
          label: item,
          value: item
        }
      }

      const value = item?.code ?? item?.value ?? item?.area ?? item?.name
      const label = item?.label ?? item?.areaLabel ?? item?.name ?? item?.desc ?? value

      return hasValue(value)
        ? {
            label: String(label),
            value
          }
        : null
    })
    .filter(Boolean)
}

const scanItems = computed(() => (Array.isArray(scanResult.value?.items)
  ? scanResult.value.items.map((item, index) => ({
      ...item,
      __rowKey: `${item.address || 'address'}-${item.offset ?? index}`
    }))
  : []
))

const scanButtonState = computed(() => ({
  danger: scanningActive.value,
  text: scanningActive.value ? '中止扫描' : '开始扫描'
}))

const formatTime = value => {
  if (!value) return '-'

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value

  return date.toLocaleString()
}

const formatScanValue = value => {
  if (!hasValue(value)) return '-'
  if (typeof value === 'object') return JSON.stringify(value)

  return value
}

const resetScanForm = () => {
  scanForm.sessionId = props.sessionId || ''
  scanForm.area = scanForm.area || undefined
  scanForm.startAddress = 0
  scanForm.quantity = 100
  scanResult.value = null
  scanCount.value = 0
}

const loadScanAreaOptions = async () => {
  if (scanAreaOptions.value.length) return

  scanAreaLoading.value = true

  try {
    const response = await getModscanAreas()
    scanAreaOptions.value = normalizeScanAreaOptions(getResponseData(response))
    if (!scanForm.area && scanAreaOptions.value.length) {
      scanForm.area = scanAreaOptions.value[0].value
    }
  } catch (error) {
    scanAreaOptions.value = []
  } finally {
    scanAreaLoading.value = false
  }
}

const closeScanSession = async (targetSessionId = scanForm.sessionId) => {
  const sessionId = trimText(targetSessionId)

  if (!sessionId || closingSessionIds.has(sessionId)) return

  closingSessionIds.add(sessionId)

  try {
    await closeModscanSession({ sessionId })
  } catch (error) {
    // 关闭失败不阻断弹框关闭，错误由请求拦截器提示。
  } finally {
    closingSessionIds.delete(sessionId)
    if (scanForm.sessionId === sessionId) {
      scanForm.sessionId = ''
    }
  }
}

const stopScanPolling = () => {
  scanningActive.value = false

  if (scanTimer) {
    clearInterval(scanTimer)
    scanTimer = null
  }
}

const handleClose = () => {
  const sessionId = scanForm.sessionId

  stopScanPolling()
  open.value = false
  scanLoading.value = false
  scanResult.value = null
  closeScanSession(sessionId)
}

const runScanOnce = async (showSuccess = false) => {
  if (!trimText(scanForm.sessionId) || !hasValue(scanForm.area) || !hasValue(scanForm.startAddress) || !hasValue(scanForm.quantity)) {
    message.warning('请填写会话ID、区域、起始地址和数量')
    return false
  }

  if (scanLoading.value) return true

  scanLoading.value = true

  try {
    const response = await scanModbusPoints({
      sessionId: trimText(scanForm.sessionId),
      area: scanForm.area,
      startAddress: scanForm.startAddress,
      quantity: scanForm.quantity
    })
    scanResult.value = getResponseData(response) || {}
    scanCount.value += 1

    if (scanResult.value?.success === false) {
      message.error(scanResult.value.message || '扫描失败')
      return false
    } else {
      if (showSuccess) message.success('扫描已开始')
      return true
    }
  } catch (error) {
    return false
  } finally {
    scanLoading.value = false
  }
}

const handleToggleScan = async () => {
  if (scanningActive.value) {
    stopScanPolling()
    return
  }

  scanningActive.value = true
  const success = await runScanOnce(true)

  if (!success) {
    stopScanPolling()
    return
  }

  scanTimer = setInterval(() => {
    runScanOnce().catch(() => {
      stopScanPolling()
    })
  }, 3000)
}

watch(open, async value => {
  if (!value) {
    const sessionId = scanForm.sessionId

    stopScanPolling()
    closeScanSession(sessionId)
    return
  }

  resetScanForm()
  await loadScanAreaOptions()
})

onBeforeUnmount(() => {
  const sessionId = scanForm.sessionId

  stopScanPolling()
  closeScanSession(sessionId)
})
</script>

<style scoped>
.scan-form {
  margin-bottom: 12px;
  row-gap: 10px;
}

.scan-summary {
  margin-bottom: 12px;
}

.scan-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 8px;
  max-height: 440px;
  overflow: auto;
}

.scan-item {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
  padding: 8px;
  border: 1px solid #edf1f7;
  border-radius: 6px;
  background: #fff;
}

.scan-address,
.scan-binary {
  color: #1f2937;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.35;
}

.scan-address {
  white-space: nowrap;
}

.scan-binary {
  min-width: 0;
  overflow-wrap: anywhere;
  word-break: break-all;
}
</style>
