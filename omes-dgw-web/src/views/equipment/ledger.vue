<template>
  <div class="page-container ledger-list-page">
    <div class="page-header">
      <h2>设备台账</h2>
      <a-space>
        <a-button @click="handleDownloadTemplate">
          <DownloadOutlined />
          下载模板
        </a-button>
        <a-button @click="handleTriggerImport">
          <UploadOutlined />
          导入
        </a-button>
        <a-button @click="handleExportDevices">
          <DownloadOutlined />
          导出
        </a-button>
        <a-button type="primary" @click="handleAddDevice">
          <PlusOutlined />
          新建设备
        </a-button>
      </a-space>
      <input
        ref="importInputRef"
        accept=".xlsx,.xls,.csv"
        class="hidden-file-input"
        type="file"
        @change="handleImportFileChange"
      />
    </div>

    <a-card class="filter-card" size="small" style="margin-bottom: 16px">
      <a-form layout="inline" :model="searchForm" @finish="handleSearch">
        <a-form-item name="name" label="设备名称">
          <a-input v-model:value="searchForm.name" placeholder="请输入" style="width: 150px" />
        </a-form-item>
        <a-form-item name="code" label="设备编码">
          <a-input v-model:value="searchForm.code" placeholder="请输入" style="width: 150px" />
        </a-form-item>
        <a-form-item name="protocol" label="协议类型">
          <a-select
            v-model:value="searchForm.protocol"
            :options="protocolOptions"
            :loading="protocolLoading"
            allow-clear
            show-search
            placeholder="请选择协议类型"
            option-filter-prop="label"
            style="width: 150px"
          />
        </a-form-item>
        <a-form-item name="categoryCode" label="所属分组">
          <a-select
            v-model:value="searchForm.categoryCode"
            :options="categoryOptions"
            :loading="categoryLoading"
            allow-clear
            show-search
            placeholder="请选择所属分组"
            option-filter-prop="label"
            style="width: 150px"
          />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" html-type="submit">查询</a-button>
            <a-button @click="handleReset">重置</a-button>
            <a-button html-type="button" :disabled="!hasSelection" :loading="batchOperating" @click="handleBatchEnable">
              一键启用
            </a-button>
            <a-button html-type="button" danger :disabled="!hasSelection" :loading="batchOperating" @click="handleBatchStop">
              一键停用
            </a-button>
            <span v-if="hasSelection" class="selection-hint">已选 {{ selectedRowKeys.length }} 台</span>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card class="list-card">
      <a-table
        :columns="tableColumns"
        :data-source="tableData"
        :pagination="pagination"
        :loading="tableLoading"
        :row-selection="rowSelection"
        :scroll="{ x: 1200, y: 'calc(100% - 39px)' }"
        size="small"
        row-key="id"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'protocol'">
            {{ getProtocolLabel(record.protocol) }}
          </template>
          <template v-if="column.key === 'categoryCode'">
            {{ getCategoryDisplay(record) }}
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleView(record)">详情</a-button>
              <a-button type="link" size="small" @click="handleOpenPoints(record)">点位</a-button>
              <a-button type="link" size="small" @click="handleCopyDevice(record)">复制</a-button>
              <a-button v-if="isModbusProtocol(record)" type="link" size="small" @click="handleOpenScan(record)">扫描</a-button>
              <a-button v-if="isRecordEnabled(record)" type="link" size="small" danger @click="handleStopRealtime(record)">停止</a-button>
              <a-button v-else type="link" size="small" @click="handleEnableRealtime(record)">启用</a-button>
              <a-button v-if="!isRecordEnabled(record)" type="link" size="small" @click="handleEdit(record)">编辑</a-button>
              <a-button v-if="!isRecordEnabled(record)" type="link" size="small" danger @click="handleDelete(record)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="showAddModal"
      :title="deviceModalTitle"
      width="1000px"
      :mask-closable="false"
      ok-text="确定"
      cancel-text="取消"
      @ok="handleSubmit"
      @cancel="handleCancel"
    >
      <a-alert
        v-if="copyMode"
        type="info"
        show-icon
        style="margin-bottom: 16px"
        message="正在复制设备：将保留源设备协议与点位配置，请修改名称、编码、IP 等连接参数后保存。"
      />
      <a-form layout="vertical" :model="formData" ref="formRef">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="设备名称" required>
              <a-input v-model:value="formData.name" placeholder="请输入设备名称" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="设备编码" required>
              <a-input v-model:value="formData.code" placeholder="请输入设备编码" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="协议类型" required>
              <a-select
                v-model:value="formData.protocol"
                :options="protocolOptions"
                :loading="protocolLoading"
                :disabled="editMode || copyMode"
                allow-clear
                show-search
                placeholder="请选择协议类型"
                option-filter-prop="label"
                @change="handleProtocolChange"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="所属分组">
              <a-select
                v-model:value="formData.categoryCode"
                :options="categoryOptions"
                :loading="categoryLoading"
                allow-clear
                show-search
                placeholder="请选择所属分组"
                option-filter-prop="label"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="IP地址" required>
              <a-input v-model:value="formData.ipAddress" placeholder="请输入IP地址" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="端口号" required>
              <a-input-number v-model:value="formData.port" :min="1" :max="65535" style="width: 100%" placeholder="请输入端口号" />
            </a-form-item>
          </a-col>
          <a-col v-if="showUnitIdField" :span="12">
            <a-form-item :label="unitIdLabel" :extra="unitIdExtra">
              <a-input-number
                v-model:value="formData.unitId"
                :min="0"
                :max="255"
                style="width: 100%"
                :placeholder="unitIdPlaceholder"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="默认采集周期(秒)" required>
              <a-input-number v-model:value="formData.collectIntervalSec" :min="1" style="width: 100%" placeholder="请输入默认采集周期" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="连接超时(毫秒)" required>
              <a-input-number v-model:value="formData.connectTimeoutMs" :min="500" :max="120000" style="width: 100%" placeholder="建议 3000～10000" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item
              label="是否共享查询线程"
              extra="控制写入是否复用采集轮询连接池：选「是」则共享查询线程，写入后 500ms 补读；选「否」则每次独立短连接（推荐，与轮询隔离）。针对部分PLC只能单连接场景"
            >
              <a-select
                v-model:value="formData.reusePollingConnection"
                :options="reusePollingConnectionOptions"
                placeholder="请选择"
              />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注">
              <a-textarea v-model:value="formData.remark" :rows="3" placeholder="请输入备注" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
      <template #footer>
        <div class="device-modal-footer">
          <a-button
            v-if="canTestDeviceConnection"
            :loading="connectionTesting"
            :disabled="connectionCooling"
            @click="handleTestConnection"
          >
            测试连接
          </a-button>
          <div class="device-modal-actions">
            <a-button @click="handleCancel">取消</a-button>
            <a-button type="primary" @click="handleSubmit">确定</a-button>
          </div>
        </div>
      </template>
    </a-modal>

    <a-modal
      v-model:open="detailVisible"
      title="设备详情"
      width="1000px"
      :mask-closable="false"
      :footer="null"
    >
      <a-descriptions :column="2" bordered v-if="currentDevice">
        <a-descriptions-item label="设备名称">{{ currentDevice.name || '-' }}</a-descriptions-item>
        <a-descriptions-item label="设备编码">{{ currentDevice.code || '-' }}</a-descriptions-item>
        <a-descriptions-item label="协议类型">{{ getProtocolLabel(currentDevice.protocol) }}</a-descriptions-item>
        <a-descriptions-item label="所属分组">{{ getCategoryDisplay(currentDevice) }}</a-descriptions-item>
        <a-descriptions-item label="IP地址">{{ currentDevice.ipAddress || '-' }}</a-descriptions-item>
        <a-descriptions-item label="端口号">{{ currentDevice.port ?? '-' }}</a-descriptions-item>
        <a-descriptions-item v-if="showDeviceUnitId(currentDevice)" :label="getUnitIdLabel(currentDevice.protocol)">
          {{ formatUnitId(currentDevice) }}
        </a-descriptions-item>
        <a-descriptions-item label="默认采集周期(秒)">{{ currentDevice.collectIntervalSec ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="连接超时(毫秒)">{{ currentDevice.connectTimeoutMs ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="是否共享查询线程">{{ formatReusePollingConnection(currentDevice) }}</a-descriptions-item>
        <a-descriptions-item label="备注" :span="2">{{ currentDevice.remark || '-' }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>

    <DevicePointModal
      v-model:open="pointListVisible"
      :device="currentPointDevice"
      @saved="loadDevicePage"
    />

    <ModscanScanModal
      v-model:open="scanVisible"
      :session-id="scanSessionId"
      @update:open="handleScanVisibleChange"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { DownloadOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons-vue'
import {
  copyDevice,
  createDevice,
  deleteDevice,
  downloadDeviceImportTemplate,
  enableDeviceRealtime,
  enableDeviceRealtimeBatch,
  exportDevices,
  getDeviceCategoriesAll,
  getDevicePage,
  getDeviceProtocols,
  importDevices,
  openModscanSession,
  stopDeviceRealtime,
  stopDeviceRealtimeBatch,
  testDeviceConnection,
  updateDevice
} from '@/api/equipment'
import DevicePointModal from './components/DevicePointModal.vue'
import ModscanScanModal from './components/ModscanScanModal.vue'

const searchForm = reactive({
  name: '',
  code: '',
  protocol: undefined,
  categoryCode: undefined
})

const createEmptyDeviceForm = () => ({
  id: null,
  name: '',
  code: '',
  protocol: undefined,
  categoryCode: undefined,
  ipAddress: '',
  port: null,
  unitId: null,
  collectIntervalSec: 1,
  connectTimeoutMs: 3000,
  reusePollingConnection: false,
  remark: '',
  points: []
})

const reusePollingConnectionOptions = [
  { label: '是', value: true },
  { label: '否', value: false }
]

const formData = reactive(createEmptyDeviceForm())

const showAddModal = ref(false)
const detailVisible = ref(false)
const pointListVisible = ref(false)
const scanVisible = ref(false)
const editMode = ref(false)
const copyMode = ref(false)
const copySourceDeviceId = ref(null)
const currentDevice = ref(null)
const currentPointDevice = ref(null)
const currentScanDevice = ref(null)
const scanSessionId = ref('')
const scanOpeningId = ref(null)
const importInputRef = ref()

const formRef = ref()

const deviceModalTitle = computed(() => {
  if (editMode.value) return '编辑设备'
  if (copyMode.value) return '复制设备'
  return '新建设备'
})

const tableData = ref([])
const tableLoading = ref(false)
const selectedRowKeys = ref([])
const batchOperating = ref(false)
const categoryOptions = ref([])
const categoryLoading = ref(false)
const protocolOptions = ref([])
const protocolLoading = ref(false)

const isUnitIdProtocol = protocol => protocol === 'MODBUS_TCP' || protocol === 'FINS_TCP' || protocol === 'SLMP_TCP'

const showUnitIdField = computed(() => isUnitIdProtocol(formData.protocol))

const defaultPortOf = protocol => {
  if (protocol === 'SLMP_TCP') return 5000
  if (protocol === 'FINS_TCP') return 9600
  if (protocol === 'MODBUS_TCP') return 502
  return null
}

const PROTOCOL_DEFAULT_PORTS = [502, 5000, 9600]

const handleProtocolChange = protocol => {
  const nextPort = defaultPortOf(protocol)
  if (nextPort && (formData.port == null || PROTOCOL_DEFAULT_PORTS.includes(formData.port))) {
    formData.port = nextPort
  }
  if (protocol === 'SLMP_TCP' && (formData.unitId === 1 || formData.unitId === 0)) {
    formData.unitId = null
  }
}

const unitIdLabel = computed(() => {
  if (formData.protocol === 'FINS_TCP') return '节点号(unitId)'
  if (formData.protocol === 'SLMP_TCP') return '站号(stationNo)'
  if (formData.protocol === 'MODBUS_TCP') return '从站号(unitId)'
  return 'unitId'
})

const unitIdExtra = computed(() => {
  if (formData.protocol === 'FINS_TCP') {
    return 'FINS 目标节点 DA1；留空则由握手自动获取'
  }
  if (formData.protocol === 'SLMP_TCP') {
    return 'SLMP/MC 协议 PC 站号；留空默认 255(0xFF 本站)'
  }
  if (formData.protocol === 'MODBUS_TCP') {
    return 'Modbus 从站地址；留空默认为 1'
  }
  return ''
})

const unitIdPlaceholder = computed(() => {
  if (formData.protocol === 'FINS_TCP') return '留空自动获取'
  if (formData.protocol === 'SLMP_TCP') return '默认 255'
  if (formData.protocol === 'MODBUS_TCP') return '默认 1'
  return '请输入 unitId'
})

const getUnitIdLabel = protocol => {
  if (protocol === 'FINS_TCP') return '节点号(unitId)'
  if (protocol === 'SLMP_TCP') return '站号(stationNo)'
  if (protocol === 'MODBUS_TCP') return '从站号(unitId)'
  return 'unitId'
}

const showDeviceUnitId = record => isUnitIdProtocol(record?.protocol)

const formatUnitId = record => {
  if (record?.unitId === 0) return '0'
  return record?.unitId ?? '默认'
}

const connectionTesting = ref(false)
const connectionCooling = ref(false)
let connectionCoolingTimer = null

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showQuickJumper: true,
  pageSizeOptions: ['10', '20', '50', '100'],
  showTotal: total => `共 ${total} 台`
})

const trimText = value => (typeof value === 'string' ? value.trim() : '')

const buildCopyName = name => {
  const base = trimText(name) || '设备'
  const suffix = '_副本'
  const maxLen = 200
  if (base.length + suffix.length <= maxLen) return `${base}${suffix}`
  return `${base.slice(0, maxLen - suffix.length)}${suffix}`
}

const buildCopyCode = code => {
  const base = trimText(code) || 'DEV'
  const suffix = '_COPY'
  const maxLen = 64
  if (base.length + suffix.length <= maxLen) return `${base}${suffix}`
  return `${base.slice(0, maxLen - suffix.length)}${suffix}`
}

const cleanPayload = payload => Object.fromEntries(
  Object.entries(payload).filter(([, value]) => value !== undefined && value !== null && value !== '')
)

const hasValue = value => value !== undefined && value !== null && value !== ''

const getRecordDeviceId = record => record?.deviceId ?? record?.id

const getRecordId = record => record?.id

const isRecordEnabled = record => [true, 1, '1', 'true'].includes(record?.enabled)

const hasSelection = computed(() => selectedRowKeys.value.length > 0)

const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  preserveSelectedRowKeys: true,
  onChange: keys => {
    selectedRowKeys.value = keys
  }
}))

const isModbusProtocol = record => String(record?.protocol || '').toUpperCase() === 'MODBUS_TCP'

const canTestDeviceConnection = computed(() => (
  trimText(formData.name) &&
  trimText(formData.code) &&
  hasValue(formData.protocol) &&
  trimText(formData.ipAddress) &&
  hasValue(formData.port) &&
  hasValue(formData.collectIntervalSec) &&
  hasValue(formData.connectTimeoutMs)
))

const getResponseData = response => response?.data ?? response

const resolveSessionId = data => {
  if (typeof data === 'string') return data

  return data?.sessionId || data?.id || data?.session?.sessionId || ''
}

const getResponseTotal = response => {
  const data = getResponseData(response)

  return Number(data.total)
}

const setFirstOptionIfEmpty = (target, key, options) => {
  if (!hasValue(target[key]) && options.length) {
    target[key] = options[0].value
  }
}

const getCategoryName = item => {
  return item?.name || ''
}

const getCategoryValue = item => {
  return item?.code
}

const getProtocolName = item => {
  return item?.label || ''
}

const getProtocolValue = item => {
  return item?.code
}

const normalizeOptions = (list, getLabel, getValue) => list
  .map(item => {
    const label = getLabel(item)
    const value = getValue(item)

    return label && value !== undefined && value !== null
      ? {
          label: String(label),
          value
        }
      : null
  })
  .filter(Boolean)

const loadCategoryOptions = async () => {
    categoryLoading.value = true

  try {
    const response = await getDeviceCategoriesAll()
    categoryOptions.value = normalizeOptions(getResponseData(response), getCategoryName, getCategoryValue)
  } catch (error) {
    categoryOptions.value = []
  } finally {
    categoryLoading.value = false
  }
}

const getCategoryLabel = value => {
  if (value === undefined || value === null || value === '') return '-'

  return categoryOptions.value.find(item => String(item.value) === String(value))?.label || value
}

const getCategoryDisplay = record => (
  record?.categoryName ||
  record?.groupName ||
  record?.categoryLabel ||
  record?.category ||
  getCategoryLabel(record?.categoryCode)
)

const loadProtocolOptions = async () => {
  protocolLoading.value = true

  try {
    const response = await getDeviceProtocols()
    protocolOptions.value = normalizeOptions(getResponseData(response), getProtocolName, getProtocolValue)
    setFirstOptionIfEmpty(formData, 'protocol', protocolOptions.value)
  } catch (error) {
    protocolOptions.value = []
  } finally {
    protocolLoading.value = false
  }
}

const getProtocolLabel = value => {
  if (value === undefined || value === null || value === '') return '-'

  return protocolOptions.value.find(item => String(item.value) === String(value))?.label || value
}

const resolveReusePollingConnection = record => {
  if (record?.reusePollingConnection !== undefined && record?.reusePollingConnection !== null) {
    return record.reusePollingConnection === true
  }

  return record?.collectConfig?.reusePollingConnection === true
}

const formatReusePollingConnection = record => (
  resolveReusePollingConnection(record) ? '是' : '否'
)

const tableColumns = [
  { title: '设备编码', dataIndex: 'code', key: 'code', width: 130 },
  { title: '设备名称', dataIndex: 'name', key: 'name', width: 180 },
  { title: '协议类型', key: 'protocol', width: 140 },
  { title: 'IP地址', dataIndex: 'ipAddress', key: 'ipAddress', width: 150 },
  { title: '所属分组', key: 'categoryCode', width: 140 },
  { title: '端口号', dataIndex: 'port', key: 'port', width: 100 },
  { title: '默认采集周期(秒)', dataIndex: 'collectIntervalSec', key: 'collectIntervalSec', width: 150 },
  { title: '操作', key: 'action', width: 360, fixed: 'right' }
]

const buildDevicePayload = (withId = true) => {
  const payload = cleanPayload({
    id: withId ? formData.id : undefined,
    name: trimText(formData.name),
    code: trimText(formData.code),
    protocol: formData.protocol,
    categoryCode: formData.categoryCode,
    ipAddress: trimText(formData.ipAddress),
    port: formData.port,
    collectIntervalSec: formData.collectIntervalSec,
    connectTimeoutMs: formData.connectTimeoutMs,
    remark: trimText(formData.remark)
  })
  if (showUnitIdField.value) {
    payload.unitId = formData.unitId ?? null
  }
  return {
    ...payload,
    reusePollingConnection: formData.reusePollingConnection === true
  }
}

const buildPagePayload = () => cleanPayload({
  name: trimText(searchForm.name),
  code: trimText(searchForm.code),
  protocol: searchForm.protocol,
  categoryCode: searchForm.categoryCode,
  page: pagination.current,
  size: pagination.pageSize
})

const downloadBlob = (blob, filename) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')

  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

const loadDevicePage = async () => {
  tableLoading.value = true

  try {
    const response = await getDevicePage(buildPagePayload())
    tableData.value = getResponseData(response).records
    pagination.total = getResponseTotal(response)
  } catch (error) {
    tableData.value = []
    pagination.total = 0
  } finally {
    tableLoading.value = false
  }
}

const handleAddDevice = () => {
  editMode.value = false
  copyMode.value = false
  copySourceDeviceId.value = null
  Object.assign(formData, createEmptyDeviceForm())
  setFirstOptionIfEmpty(formData, 'protocol', protocolOptions.value)
  showAddModal.value = true
}

const handleCopyDevice = record => {
  const sourceId = getRecordId(record)

  if (!hasValue(sourceId)) {
    message.warning('缺少设备ID')
    return
  }

  editMode.value = false
  copyMode.value = true
  copySourceDeviceId.value = sourceId
  Object.assign(formData, createEmptyDeviceForm(), record, {
    id: null,
    name: buildCopyName(record?.name),
    code: buildCopyCode(record?.code),
    enabled: false,
    reusePollingConnection: resolveReusePollingConnection(record)
  })
  showAddModal.value = true
}

const handleTriggerImport = () => {
  importInputRef.value?.click()
}

const handleImportFileChange = async event => {
  const file = event.target.files?.[0]

  event.target.value = ''

  if (!file) return

  try {
    const response = await importDevices(file)
    const result = getResponseData(response)
    message.success(result?.message || '导入成功')
    pagination.current = 1
    await loadDevicePage()
  } catch (error) {
    // 请求拦截器会统一提示错误。
  }
}

const handleExportDevices = async () => {
  try {
    const blob = await exportDevices(buildPagePayload())
    downloadBlob(blob, '设备台账.xlsx')
  } catch (error) {
    // 请求拦截器会统一提示错误。
  }
}

const handleDownloadTemplate = async () => {
  try {
    const blob = await downloadDeviceImportTemplate()
    downloadBlob(blob, '设备导入模板.xlsx')
  } catch (error) {
    // 请求拦截器会统一提示错误。
  }
}

const handleSearch = () => {
  pagination.current = 1
  loadDevicePage()
}

const handleReset = () => {
  searchForm.name = ''
  searchForm.code = ''
  searchForm.protocol = undefined
  searchForm.categoryCode = undefined
  selectedRowKeys.value = []
  pagination.current = 1
  loadDevicePage()
}

const handleTableChange = nextPagination => {
  const pageSizeChanged = nextPagination.pageSize !== pagination.pageSize
  pagination.current = pageSizeChanged ? 1 : nextPagination.current
  pagination.pageSize = nextPagination.pageSize
  loadDevicePage()
}

const handleEdit = (record) => {
  editMode.value = true
  copyMode.value = false
  copySourceDeviceId.value = null
  Object.assign(formData, createEmptyDeviceForm(), record, {
    reusePollingConnection: resolveReusePollingConnection(record)
  })
  showAddModal.value = true
}

const handleDelete = (record) => {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除设备「${record.name}」吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      await deleteDevice(record.id)
      if (tableData.value.length === 1 && pagination.current > 1) {
        pagination.current -= 1
      }
      await loadDevicePage()
      message.success('删除成功')
    }
  })
}

const handleEnableRealtime = (record) => {
  const deviceId = getRecordDeviceId(record)

  if (!hasValue(deviceId)) {
    message.warning('缺少设备ID')
    return
  }

  Modal.confirm({
    title: '确认启用',
    content: `确定要启用设备「${record.name}」吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      await enableDeviceRealtime(deviceId)
      await loadDevicePage()
      message.success('启用成功')
    }
  })
}

const handleStopRealtime = (record) => {
  const deviceId = getRecordDeviceId(record)

  if (!hasValue(deviceId)) {
    message.warning('缺少设备ID')
    return
  }

  Modal.confirm({
    title: '确认停止',
    content: `确定要停止设备「${record.name}」吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      await stopDeviceRealtime(deviceId)
      await loadDevicePage()
      message.success('停止成功')
    }
  })
}

const MAX_BATCH_DEVICES = 200

const formatBatchResult = result => {
  const data = getResponseData(result) || {}
  const summary = data.message || `成功 ${data.success || 0} 台，跳过 ${data.skipped || 0} 台，失败 ${data.failed || 0} 台`
  const failedItems = (data.items || []).filter(item => item && item.success === false)
  return { data, summary, failedItems }
}

const handleBatchEnable = () => {
  const deviceIds = selectedRowKeys.value.map(id => String(id)).filter(Boolean)
  if (!deviceIds.length) {
    message.warning('请先勾选设备')
    return
  }
  if (deviceIds.length > MAX_BATCH_DEVICES) {
    message.warning(`单次最多启用 ${MAX_BATCH_DEVICES} 台`)
    return
  }

  Modal.confirm({
    title: '确认一键启用',
    content: `将启用已勾选的 ${deviceIds.length} 台设备。建连会错开进行，避免同时打开大量连接。`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      batchOperating.value = true
      try {
        const { summary, failedItems } = formatBatchResult(await enableDeviceRealtimeBatch(deviceIds))
        selectedRowKeys.value = []
        await loadDevicePage()
        if (failedItems.length) {
          message.warning(summary)
        } else {
          message.success(summary)
        }
      } finally {
        batchOperating.value = false
      }
    }
  })
}

const handleBatchStop = () => {
  const deviceIds = selectedRowKeys.value.map(id => String(id)).filter(Boolean)
  if (!deviceIds.length) {
    message.warning('请先勾选设备')
    return
  }
  if (deviceIds.length > MAX_BATCH_DEVICES) {
    message.warning(`单次最多停用 ${MAX_BATCH_DEVICES} 台`)
    return
  }

  Modal.confirm({
    title: '确认一键停用',
    content: `将停用已勾选的 ${deviceIds.length} 台设备，并关闭采集连接。`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      batchOperating.value = true
      try {
        const { summary, failedItems } = formatBatchResult(await stopDeviceRealtimeBatch(deviceIds))
        selectedRowKeys.value = []
        await loadDevicePage()
        if (failedItems.length) {
          message.warning(summary)
        } else {
          message.success(summary)
        }
      } finally {
        batchOperating.value = false
      }
    }
  })
}

const handleOpenPoints = record => {
  const id = getRecordId(record)

  if (!hasValue(id)) {
    message.warning('缺少设备ID')
    return
  }

  currentPointDevice.value = record
  pointListVisible.value = true
}

const handleOpenScan = async record => {
  const deviceId = getRecordDeviceId(record)

  if (!hasValue(deviceId) || scanOpeningId.value) {
    if (!hasValue(deviceId)) message.warning('缺少设备ID')
    return
  }

  scanOpeningId.value = deviceId

  try {
    const response = await openModscanSession({
      deviceId: String(deviceId)
    })
    const sessionId = resolveSessionId(getResponseData(response))

    if (!sessionId) {
      message.error('打开扫描会话失败')
      return
    }

    scanSessionId.value = String(sessionId)
    currentScanDevice.value = record
    scanVisible.value = true
  } catch (error) {
    scanSessionId.value = ''
    currentScanDevice.value = null
  } finally {
    scanOpeningId.value = null
  }
}

const handleScanVisibleChange = value => {
  if (value) return

  scanSessionId.value = ''
  currentScanDevice.value = null
}

const handleTestConnection = async () => {
  if (!canTestDeviceConnection.value || connectionTesting.value || connectionCooling.value) return

  connectionTesting.value = true
  connectionCooling.value = true

  try {
    const response = await testDeviceConnection(buildDevicePayload(false))
    const result = getResponseData(response)

    if (result?.success) {
      message.success(result.latencyMs === null || result.latencyMs === undefined
        ? '连接测试成功'
        : `连接测试成功，延迟 ${result.latencyMs}ms`)
    } else {
      message.error(result?.message || '连接测试失败')
    }
  } catch (error) {
    // 请求拦截器会统一提示错误。
  } finally {
    connectionTesting.value = false
    clearTimeout(connectionCoolingTimer)
    connectionCoolingTimer = setTimeout(() => {
      connectionCooling.value = false
      connectionCoolingTimer = null
    }, 3000)
  }
}

const handleSubmit = async () => {
  if (
    !canTestDeviceConnection.value
  ) {
    message.warning('请填写设备名称、设备编码、协议类型、IP地址、端口号、默认采集周期和连接超时')
    return
  }

  const submitData = buildDevicePayload()

  try {
    if (editMode.value) {
      await updateDevice(submitData)
      message.success('编辑成功')
    } else if (copyMode.value) {
      if (!hasValue(copySourceDeviceId.value)) {
        message.warning('缺少源设备ID')
        return
      }
      await copyDevice({
        sourceDeviceId: copySourceDeviceId.value,
        device: {
          ...buildDevicePayload(false),
          enabled: false
        }
      })
      message.success('复制成功')
    } else {
      await createDevice(buildDevicePayload(false))
      message.success('创建成功')
    }

    showAddModal.value = false
    handleCancel()
    await loadDevicePage()
  } catch (error) {
    // 请求拦截器会统一提示错误。
  }
}

const handleCancel = () => {
  showAddModal.value = false
  editMode.value = false
  copyMode.value = false
  copySourceDeviceId.value = null
  clearTimeout(connectionCoolingTimer)
  connectionCoolingTimer = null
  connectionTesting.value = false
  connectionCooling.value = false
  Object.assign(formData, createEmptyDeviceForm())
  formRef.value?.resetFields()
}

const handleView = (record) => {
  currentDevice.value = { ...record }
  detailVisible.value = true
}

onMounted(() => {
  loadCategoryOptions()
  loadProtocolOptions()
  loadDevicePage()
})
</script>

<style scoped>
.ledger-list-page {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.ledger-list-page .page-header,
.ledger-list-page .filter-card {
  flex-shrink: 0;
}

.list-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.list-card :deep(.ant-card-body) {
  flex: 1;
  min-height: 0;
  padding: 10px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.list-card :deep(.ant-table-wrapper),
.list-card :deep(.ant-spin-nested-loading),
.list-card :deep(.ant-spin-container) {
  flex: 1;
  height: 100%;
  min-height: 0;
}

.list-card :deep(.ant-spin-container) {
  display: flex;
  flex-direction: column;
}

.list-card :deep(.ant-table) {
  flex: 1;
  height: calc(100% - 64px);
  min-height: 0;
  overflow: hidden;
}

.list-card :deep(.ant-table-container) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.list-card :deep(.ant-table-header) {
  flex-shrink: 0;
}

.list-card :deep(.ant-table-body) {
  flex: 1;
  min-height: 0;
  overflow: auto !important;
}

.list-card :deep(.ant-table-pagination) {
  flex-shrink: 0;
  margin: 0;
  padding: 12px 0 4px;
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

.hidden-file-input {
  display: none;
}

.selection-hint {
  color: #666;
  font-size: 13px;
}

.device-modal-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.device-modal-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
</style>
