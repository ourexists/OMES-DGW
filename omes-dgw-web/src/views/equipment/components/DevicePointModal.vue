<template>
  <a-modal
    v-model:open="open"
    :title="`${currentPointDevice?.name || '设备'} - 点位列表`"
    width="1100px"
    centered
    wrap-class-name="device-point-list-modal"
    :mask-closable="false"
    ok-text="确定"
    cancel-text="取消"
    :ok-button-props="{ disabled: isPointDeviceEnabled, loading: pointSaving }"
    @ok="handleSavePointList"
    @cancel="handleClosePointList"
  >
    <a-space v-if="!isPointDeviceEnabled" class="point-toolbar" wrap>
      <a-button type="primary" @click="handleAddPoint">
        <PlusOutlined />
        添加点位
      </a-button>
      <a-button @click="handleOpenPointImport">
        <UploadOutlined />
        批量粘贴
      </a-button>
      <a-button @click="handleOpenCopyFromDevice">
        <CopyOutlined />
        从设备复制
      </a-button>
    </a-space>
    <a-table
      class="point-list-table"
      :columns="pointColumns"
      :data-source="pointList"
      :pagination="false"
      :loading="pointLoading"
      :scroll="pointListTableScroll"
      size="small"
      :row-key="getPointRowKey"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'dataType'">
          {{ getDataTypeLabel(record.dataType) }}
        </template>
        <template v-if="column.key === 'byteOrder'">
          {{ getByteOrderLabel(record.byteOrder) }}
        </template>
        <template v-if="column.key === 'actualAddress'">
          {{ isCollectAccessType(record.accessType) ? (record.actualAddress || '-') : '-' }}
        </template>
        <template v-if="column.key === 'setpointAddress'">
          {{ isWriteAccessType(record.accessType) ? (record.setpointAddress || '-') : '-' }}
        </template>
        <template v-if="column.key === 'accessType'">
          {{ getAccessTypeLabel(record.accessType) }}
        </template>
        <template v-if="column.key === 'engValueRange'">
          {{ formatEngValueRange(record) }}
        </template>
        <template v-if="column.key === 'bcdConvert'">
          {{ formatBcdConvert(record) }}
        </template>
        <template v-if="column.key === 'bitField'">
          {{ formatBitField(record) }}
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" :disabled="isPointDeviceEnabled" @click="handleEditPoint(record)">编辑</a-button>
            <a-button type="link" size="small" danger :disabled="isPointDeviceEnabled" @click="handleDeletePoint(record)">删除</a-button>
          </a-space>
        </template>
      </template>
    </a-table>
  </a-modal>

  <a-modal
    v-model:open="pointVisible"
    :title="pointFormModalTitle"
    width="800px"
    :mask-closable="false"
    ok-text="确定"
    cancel-text="取消"
    @ok="handleSubmitPoint"
    @cancel="handleCancelPoint"
  >
    <a-form layout="vertical" :model="pointForm" ref="pointFormRef">
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="点位编码" required>
            <a-input v-model:value="pointForm.code" placeholder="请输入点位编码" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="点位名称" required>
            <a-input v-model:value="pointForm.name" placeholder="请输入点位名称" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="读写权限" required>
            <a-select
              v-model:value="pointForm.accessType"
              :options="accessTypeOptions"
              :loading="accessTypeLoading"
              placeholder="请选择读写权限"
              @change="handleAccessTypeChange"
            />
          </a-form-item>
        </a-col>
        <a-col v-if="requiresActualAddress" :span="12">
          <a-form-item label="实际值点位" required>
            <a-input v-model:value="pointForm.actualAddress" :placeholder="pointAddressPlaceholder" />
          </a-form-item>
        </a-col>
        <a-col v-if="requiresSetpointAddress" :span="12">
          <a-form-item label="设定值点位" required>
            <a-input v-model:value="pointForm.setpointAddress" :placeholder="pointAddressPlaceholder" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="数据类型" required>
            <a-select
              v-model:value="pointForm.dataType"
              :options="dataTypeOptions"
              :loading="dataTypeLoading"
              allow-clear
              show-search
              placeholder="请选择数据类型"
              option-filter-prop="label"
              @change="handlePointDataTypeChange"
            />
          </a-form-item>
        </a-col>
        <a-col v-if="currentDataTypeSupportsByteOrder" :span="12">
          <a-form-item label="字节序" required>
            <a-select
              v-model:value="pointForm.byteOrder"
              :options="byteOrderOptions"
              :loading="byteOrderLoading"
              allow-clear
              show-search
              placeholder="请选择字节序"
              option-filter-prop="label"
            />
          </a-form-item>
        </a-col>
        <a-col v-if="currentDataTypeSupportsScaleFactor" :span="12">
          <a-form-item label="缩放系数" required>
            <a-input-number v-model:value="pointForm.scaleFactor" style="width: 100%" placeholder="请输入缩放系数" />
          </a-form-item>
        </a-col>
        <a-col v-if="currentDataTypeSupportsEngRange" :span="12">
          <a-form-item label="工程值最小值" extra="可选，不填则读写不限制">
            <a-input-number v-model:value="pointForm.engValueMin" style="width: 100%" placeholder="可选，不填则不限制" />
          </a-form-item>
        </a-col>
        <a-col v-if="currentDataTypeSupportsEngRange" :span="12">
          <a-form-item label="工程值最大值">
            <a-input-number v-model:value="pointForm.engValueMax" style="width: 100%" placeholder="可选，不填则不限制" />
          </a-form-item>
        </a-col>
        <a-col v-if="currentDataTypeSupportsEngRange" :span="12">
          <a-form-item label="工程单位">
            <a-input v-model:value="pointForm.engUnit" placeholder="请输入工程单位" />
          </a-form-item>
        </a-col>
        <a-col v-if="currentDataTypeSupportsDecimalPlaces" :span="12">
          <a-form-item label="小数位数" required>
            <a-input-number v-model:value="pointForm.decimalPlaces" :min="0" :max="10" style="width: 100%" placeholder="请输入小数位数" />
          </a-form-item>
        </a-col>
        <a-col v-if="currentDataTypeSupportsBcdConvert" :span="12">
          <a-form-item
            label="BCD转码"
            required
            extra="Packed BCD：整字如 0x1234→1234。与位域 BA/DC 组合时先拆分再转码（如 0x1230：BA→30、DC→12）"
          >
            <a-select
              v-model:value="pointForm.bcdConvert"
              :options="bcdOptions"
              placeholder="请选择是否转码"
            />
          </a-form-item>
        </a-col>
        <a-col v-if="currentDataTypeSupportsBitField" :span="12">
          <a-form-item
            label="位域"
            extra="直接选 A/B/C/D（各4bit）或 BA/DC（各8bit，AB/CD 同义）。同一地址可配多个点位"
          >
            <a-select
              v-model:value="pointForm.bitField"
              :options="bitFieldOptions"
              allow-clear
              show-search
              placeholder="如 A、BA、DC"
              style="width: 100%"
            />
          </a-form-item>
        </a-col>
      </a-row>
    </a-form>
  </a-modal>

  <a-modal
    v-model:open="pointImportVisible"
    :title="pointImportModalTitle"
    width="900px"
    :mask-closable="false"
    ok-text="导入到列表"
    cancel-text="取消"
    :ok-button-props="{ loading: pointImporting }"
    @ok="handleSubmitPointImport"
    @cancel="handleCancelPointImport"
  >
    <a-alert type="info" show-icon style="margin-bottom: 12px">
      <template #message>
        <div class="point-import-help">
          <div>从 Excel 复制数据区域后粘贴，列顺序与下方表格一致，无需包含表头行。</div>
          <div>只读填实际值点位，只写填设定值点位，读写两者都填。</div>
          <div v-if="pointImportAddressTip">{{ pointImportAddressTip }}</div>
          <div v-if="pointImportAccessTypeTip">读写权限可选：{{ pointImportAccessTypeTip }}</div>
          <div v-if="pointImportDataTypeTip">数据类型可选：{{ pointImportDataTypeTip }}</div>
          <div>未填写读写权限时默认：{{ defaultAccessTypeLabel }}</div>
          <div>BCD转码可选：是、否（仅无符号整型 UInt/ULong；与位域 BA/DC 组合可拆半字 BCD）</div>
          <div>位域可选：A/B/C/D/BA/DC（也可写 AB/CD、A位）</div>
        </div>
      </template>
    </a-alert>
    <div class="point-import-toolbar">
      <a-button size="small" :disabled="!pointList.length" @click="handleFillCurrentPoints">
        带入当前点位
      </a-button>
    </div>
    <div class="point-import-sheet">
      <div class="point-import-sheet-scroll">
        <div
          class="point-import-sheet-grid"
          :style="{ gridTemplateColumns: pointImportGridTemplate }"
        >
          <div class="point-import-sheet-row point-import-sheet-row--header">
            <div class="point-import-cell point-import-cell--corner" />
            <div
              v-for="label in pointImportHeaderLabels"
              :key="label"
              class="point-import-cell point-import-cell--header"
              :title="label"
            >
              {{ label }}
            </div>
          </div>
          <template v-if="pointImportPreviewRows.length && !pointImportRawEdit">
            <div
              v-for="(row, rowIndex) in pointImportPreviewRows"
              :key="rowIndex"
              class="point-import-sheet-row"
            >
              <div class="point-import-cell point-import-cell--row-num">{{ rowIndex + 1 }}</div>
              <div
                v-for="(cell, cellIndex) in row"
                :key="cellIndex"
                class="point-import-cell"
                :title="cell"
              >
                {{ cell || '\u00a0' }}
              </div>
            </div>
          </template>
          <div v-else class="point-import-sheet-editor-wrap">
            <div class="point-import-sheet-row point-import-sheet-row--editor">
              <div class="point-import-cell point-import-cell--row-num">
                {{ pointImportPreviewRows.length ? pointImportPreviewRows.length + 1 : 1 }}
              </div>
              <div class="point-import-cell point-import-cell--editor">
                <a-textarea
                  ref="pointImportInputRef"
                  v-model:value="pointImportText"
                  :bordered="false"
                  class="point-import-sheet-textarea"
                  :auto-size="{ minRows: pointImportPreviewRows.length ? 3 : 10, maxRows: 16 }"
                  :placeholder="pointImportPlaceholder"
                />
              </div>
            </div>
            <div v-if="pointImportPreviewRows.length && pointImportRawEdit" class="point-import-sheet-edit-bar">
              <a-button type="link" size="small" @click="pointImportRawEdit = false">返回表格预览</a-button>
            </div>
          </div>
        </div>
        <div
          v-if="pointImportPreviewRows.length && !pointImportRawEdit"
          class="point-import-sheet-actions"
        >
          <a-button size="small" @click="pointImportRawEdit = true">修改粘贴内容</a-button>
          <a-button size="small" @click="handleClearPointImport">清空</a-button>
          <span class="point-import-sheet-count">共 {{ pointImportPreviewRows.length }} 行</span>
        </div>
      </div>
    </div>
  </a-modal>

  <a-modal
    v-model:open="pointCopyVisible"
    title="从其他设备复制点位"
    width="560px"
    :mask-closable="false"
    ok-text="复制到列表"
    cancel-text="取消"
    :ok-button-props="{ loading: pointCopying, disabled: !pointCopySourceId }"
    @ok="handleSubmitCopyFromDevice"
    @cancel="handleCancelCopyFromDevice"
  >
    <a-alert type="info" show-icon style="margin-bottom: 16px">
      <template #message>
        仅展示与当前设备相同协议的设备。复制将覆盖当前点位列表，确认保存后才会写入设备。
      </template>
    </a-alert>
    <a-form layout="vertical">
      <a-form-item label="源设备" required>
        <a-select
          v-model:value="pointCopySourceId"
          :options="pointCopyDeviceOptions"
          :loading="pointCopyDeviceLoading"
          allow-clear
          show-search
          placeholder="请选择同协议设备"
          option-filter-prop="label"
          style="width: 100%"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { CopyOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons-vue'
import {
  getDeviceDetail,
  getDevicePage,
  getDevicePointAccessTypes,
  getDevicePointByteOrders,
  getDevicePointDataTypes,
  saveDevicePoint
} from '@/api/equipment'

const props = defineProps({
  device: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['saved'])

const open = defineModel('open', {
  type: Boolean,
  default: false
})

const createEmptyPointForm = (accessType = 'READ_ONLY') => ({
  id: null,
  code: '',
  name: '',
  accessType,
  actualAddress: '',
  setpointAddress: '',
  dataType: undefined,
  byteOrder: undefined,
  scaleFactor: 1,
  engValueMin: undefined,
  engValueMax: undefined,
  engUnit: '',
  decimalPlaces: 3,
  bcdConvert: false,
  bitField: undefined
})

const bcdOptions = [
  { label: '否', value: false },
  { label: '是', value: true }
]

const bitFieldOptions = [
  { label: 'A（低4位）', value: 'A' },
  { label: 'B（次4位）', value: 'B' },
  { label: 'C（再次4位）', value: 'C' },
  { label: 'D（高4位）', value: 'D' },
  { label: 'BA（低字节，可写AB）', value: 'BA' },
  { label: 'DC（高字节，可写CD）', value: 'DC' }
]

const COLLECT_ACCESS_TYPES = ['READ_ONLY', 'READ_WRITE']
const WRITE_ACCESS_TYPES = ['WRITE_ONLY', 'READ_WRITE']

const pointForm = reactive(createEmptyPointForm())
const pointFormRef = ref()
const currentPointDevice = ref(null)
const pointList = ref([])
const dataTypeOptions = ref([])
const accessTypeOptions = ref([])
const byteOrderOptions = ref([])
const dataTypeLoading = ref(false)
const accessTypeLoading = ref(false)
const byteOrderLoading = ref(false)
const pointLoading = ref(false)
const pointSaving = ref(false)
const pointVisible = ref(false)
const pointImportVisible = ref(false)
const pointCopyVisible = ref(false)
const editPointMode = ref(false)
const pointImporting = ref(false)
const pointCopying = ref(false)
const pointCopyDeviceLoading = ref(false)
const pointCopySourceId = ref(undefined)
const pointCopyDeviceOptions = ref([])
const pointImportText = ref('')
const pointImportRawEdit = ref(false)
const pointImportInputRef = ref()

const trimText = value => (typeof value === 'string' ? value.trim() : '')

const cleanPayload = payload => Object.fromEntries(
  Object.entries(payload).filter(([, value]) => value !== undefined && value !== null && value !== '')
)

const hasValue = value => value !== undefined && value !== null && value !== ''

const normalizeBitField = value => {
  if (!hasValue(value)) return undefined
  let key = String(value).trim().toUpperCase()
  if (key.endsWith('位')) key = key.slice(0, -1).trim()
  if (key === 'AB') return 'BA'
  if (key === 'CD') return 'DC'
  if (['A', 'B', 'C', 'D', 'BA', 'DC'].includes(key)) return key
  return String(value).trim()
}

const getRecordDeviceId = record => record?.deviceId ?? record?.id

const getRecordId = record => record?.id

const isRecordEnabled = record => [true, 1, '1', 'true'].includes(record?.enabled)

const isPointDeviceEnabled = computed(() => isRecordEnabled(currentPointDevice.value))

const isCollectAccessType = accessType => (
  COLLECT_ACCESS_TYPES.includes(String(accessType || '').toUpperCase())
)

const isWriteAccessType = accessType => (
  WRITE_ACCESS_TYPES.includes(String(accessType || '').toUpperCase())
)

const requiresActualAddress = computed(() => isCollectAccessType(pointForm.accessType))

const requiresSetpointAddress = computed(() => isWriteAccessType(pointForm.accessType))

const defaultAccessType = 'READ_ONLY'

const deviceProtocol = computed(() => String(currentPointDevice.value?.protocol || '').toUpperCase())

const pointFormModalTitle = computed(() => (editPointMode.value ? '编辑点位' : '添加点位'))

const pointImportModalTitle = '批量粘贴点位'

/** 表体纵向滚动，避免弹窗贴底裁切末行 */
const pointListTableScroll = { x: 1280, y: 'calc(100vh - 280px)' }

const isModbusDevice = computed(() => deviceProtocol.value === 'MODBUS_TCP')

const isFinsDevice = computed(() => deviceProtocol.value === 'FINS_TCP')

const isSlmpDevice = computed(() => deviceProtocol.value === 'SLMP_TCP')

const pointAddressPlaceholder = computed(() => {
  if (isSlmpDevice.value) return '例如 D100、M100、X10、Y20、D100.5'
  if (isFinsDevice.value) return '例如 D100、D100.05、CIO100'
  if (isModbusDevice.value) return '例如 40001、10001'
  return '请输入地址'
})

const pointImportAddressTip = computed(() => {
  if (isSlmpDevice.value) {
    return 'SLMP 地址示例：D100（数据寄存器）、M100（内部继电器）、X10/Y20（八进制 I/O）、D100.5（字内位）；多字类型需填字节序'
  }
  if (isFinsDevice.value) {
    return 'FINS 地址示例：D100（DM 字）、D100.05（DM 位）、CIO100、W0、H0、A0；多字类型需填字节序'
  }
  if (isModbusDevice.value) {
    return 'Modbus 地址示例：40001（保持寄存器）、10001（线圈）；多寄存器类型需填字节序'
  }
  return ''
})

const pointImportPlaceholder = computed(() => {
  if (isSlmpDevice.value) {
    return '在此 Ctrl+V 粘贴 Excel 数据（无需表头），例如：\nTEMP_01\t温度1\t只读\tD100\t\tReal\tCDAB\t1\t0\t100\t℃\t1\t'
  }
  if (isFinsDevice.value) {
    return '在此 Ctrl+V 粘贴 Excel 数据（无需表头），例如：\nTEMP_01\t温度1\t只读\tD100\t\tReal\tCDAB\t1\t0\t100\t℃\t1\t'
  }
  return '在此 Ctrl+V 粘贴 Excel 数据（无需表头），例如：\nTEMP_01\t温度1\t只读\t40001\t\tReal\tCDAB\t1\t0\t100\t℃\t1\t'
})

/** Modbus / FINS / SLMP 多字数值均支持字节序（含字交换 CDAB） */
const useByteOrderForDevice = value =>
  (isModbusDevice.value || isFinsDevice.value || isSlmpDevice.value) && supportsByteOrder(value)

const createLocalId = () => `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`

const isBooleanDataType = value => ['BOOL', 'BOOL_INV'].includes(String(value || '').toUpperCase())

const isStringDataType = value => ['TEXT8'].includes(String(value || '').toUpperCase())

const isFloatingDataType = value => ['REAL', 'LREAL'].includes(String(value || '').toUpperCase())

const selectedPointDataType = computed(() => dataTypeOptions.value.find(item => String(item.value) === String(pointForm.dataType)))

const getDataTypeOption = value => dataTypeOptions.value.find(item => String(item.value) === String(value))

const dataTypeSupports = (value, supportKey, fallback) => {
  const option = getDataTypeOption(value)

  if (option && Object.prototype.hasOwnProperty.call(option, supportKey)) {
    return option[supportKey] !== false
  }

  return fallback
}

const supportsByteOrder = value => dataTypeSupports(value, 'supportsByteOrder', true)

const supportsScaleFactor = value => dataTypeSupports(value, 'supportsScaleFactor', true)

const supportsEngRange = value => dataTypeSupports(value, 'supportsEngRange', !isStringDataType(value))

const supportsDecimalPlaces = value => dataTypeSupports(value, 'supportsDecimalPlaces', !isBooleanDataType(value))

const supportsBcdConvert = value => dataTypeSupports(
  value,
  'supportsBcdConvert',
  ['UINT', 'ULONG'].includes(String(value || '').toUpperCase())
)

const supportsBitField = value => dataTypeSupports(
  value,
  'supportsBitField',
  supportsScaleFactor(value) && !isFloatingDataType(value) && !isBooleanDataType(value)
)

const currentDataTypeSupportsByteOrder = computed(() => useByteOrderForDevice(pointForm.dataType))

const currentDataTypeSupportsScaleFactor = computed(() => supportsScaleFactor(pointForm.dataType))

const currentDataTypeSupportsEngRange = computed(() => supportsEngRange(pointForm.dataType))

const currentDataTypeSupportsDecimalPlaces = computed(() => supportsDecimalPlaces(pointForm.dataType))

const currentDataTypeSupportsBcdConvert = computed(() => supportsBcdConvert(pointForm.dataType))

const currentDataTypeSupportsBitField = computed(() => supportsBitField(pointForm.dataType))

const parseBcdValue = (value, defaultValue = false) => {
  if (typeof value === 'boolean') return value
  if (!hasValue(value)) return defaultValue

  const normalizedText = String(value).trim().replace(/\s+/g, '').toLowerCase()
  if (['true', '1', '是', 'yes', 'y'].includes(normalizedText)) return true
  if (['false', '0', '否', 'no', 'n'].includes(normalizedText)) return false
  return defaultValue
}

const formatImportOptionTip = options => options
  .map(item => `${item.label}   `)
  .join('、')

const pointImportAccessTypeTip = computed(() => formatImportOptionTip(accessTypeOptions.value))

const pointImportDataTypeTip = computed(() => formatImportOptionTip(dataTypeOptions.value))

const getDevicePoints = record => {
  if (!record) return []
  return record.collectConfig?.points || []
}

const normalizeNumber = (value, defaultValue) => {
  if (!hasValue(value)) return defaultValue

  const numberValue = Number(value)
  return Number.isNaN(numberValue) ? defaultValue : numberValue
}

const normalizePoint = (item = {}, index = 0) => {
  const point = {
    ...createEmptyPointForm(),
    ...item
  }
  const accessType = point.accessType || defaultAccessType

  return {
    ...point,
    accessType,
    actualAddress: isCollectAccessType(accessType) ? (point.actualAddress || '') : '',
    setpointAddress: isWriteAccessType(accessType) ? (point.setpointAddress || '') : '',
    scaleFactor: normalizeNumber(point.scaleFactor, 1),
    engValueMin: hasValue(point.engValueMin) ? normalizeNumber(point.engValueMin) : undefined,
    engValueMax: hasValue(point.engValueMax) ? normalizeNumber(point.engValueMax) : undefined,
    decimalPlaces: normalizeNumber(point.decimalPlaces, 3),
    bcdConvert: parseBcdValue(point.bcdConvert, false),
    bitField: normalizeBitField(point.bitField),
    __rowKey: point.__rowKey || point.id || point.pointId || `${point.code || 'point'}-${point.actualAddress || point.setpointAddress || index}-${createLocalId()}`
  }
}

const normalizePoints = list => (Array.isArray(list) ? list : []).map(normalizePoint)

const getPointRowKey = record => record.__rowKey

const buildPointPayload = point => {
  const dataType = point?.dataType
  const canUseByteOrder = useByteOrderForDevice(dataType)
  const canUseScaleFactor = supportsScaleFactor(dataType)
  const canUseEngRange = supportsEngRange(dataType)
  const canUseDecimalPlaces = supportsDecimalPlaces(dataType)
  const canUseBcdConvert = supportsBcdConvert(dataType)
  const canUseBitField = supportsBitField(dataType)

  return cleanPayload({
    code: trimText(point?.code),
    name: trimText(point?.name),
    accessType: point?.accessType,
    actualAddress: isCollectAccessType(point?.accessType) ? trimText(point?.actualAddress) : null,
    setpointAddress: isWriteAccessType(point?.accessType) ? trimText(point?.setpointAddress) : null,
    dataType,
    byteOrder: canUseByteOrder ? point?.byteOrder : null,
    scaleFactor: canUseScaleFactor ? point?.scaleFactor : null,
    engUnit: canUseEngRange ? trimText(point?.engUnit) : null,
    engValueMin: canUseEngRange ? point?.engValueMin : null,
    engValueMax: canUseEngRange ? point?.engValueMax : null,
    decimalPlaces: canUseDecimalPlaces ? point?.decimalPlaces : null,
    bcdConvert: canUseBcdConvert ? Boolean(point?.bcdConvert) : null,
    bitField: canUseBitField ? normalizeBitField(point?.bitField) : null
  })
}

const getDataTypeLabel = value => {
  if (!hasValue(value)) return '-'

  return dataTypeOptions.value.find(item => String(item.value) === String(value))?.label || value
}

const getByteOrderLabel = value => {
  if (!hasValue(value)) return '-'

  return byteOrderOptions.value.find(item => String(item.value) === String(value))?.label || value
}

const formatEngValueRange = record => {
  if (!supportsEngRange(record?.dataType)) return '-'
  const hasMin = hasValue(record?.engValueMin)
  const hasMax = hasValue(record?.engValueMax)
  if (!hasMin && !hasMax) return '-'

  const unit = record.engUnit ? ` ${record.engUnit}` : ''
  if (hasMin && hasMax) return `${record.engValueMin} ~ ${record.engValueMax}${unit}`
  if (hasMin) return `≥ ${record.engValueMin}${unit}`
  return `≤ ${record.engValueMax}${unit}`
}

const formatBcdConvert = record => {
  if (!supportsBcdConvert(record?.dataType)) return '-'
  return parseBcdValue(record?.bcdConvert, false) ? '是' : '否'
}

const formatBitField = record => {
  if (!supportsBitField(record?.dataType)) return '-'
  const code = normalizeBitField(record?.bitField)
  return code || '-'
}

const getResponseData = response => response?.data ?? response

const setFirstOptionIfEmpty = (target, key, options) => {
  if (!hasValue(target[key]) && options.length) {
    target[key] = options[0].value
  }
}

const buildPointDetailRecord = (response, fallbackRecord) => {
  const collectConfig = fallbackRecord?.collectConfig && !Array.isArray(fallbackRecord.collectConfig)
    ? fallbackRecord.collectConfig
    : {}

  return {
    ...fallbackRecord,
    collectConfig: {
      ...collectConfig,
      points: getResponseData(response)
    }
  }
}

const formatDataTypeLabel = item => {
  const label = item?.label || ''
  const description = item?.description || ''

  return description ? `${label}（${description}）` : label
}

const getDataTypeName = item => formatDataTypeLabel(item)

const getDataTypeValue = item => item?.code

const getAccessTypeName = item => item?.label || ''

const getAccessTypeValue = item => item?.code

const getByteOrderName = item => item?.label || ''

const getByteOrderValue = item => item?.code

const normalizeOptions = (list, getLabel, getValue) => (Array.isArray(list) ? list : [])
  .map(item => {
    if (typeof item === 'string') {
      return {
        label: item,
        value: item
      }
    }

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

const normalizeDataTypeOptions = list => (Array.isArray(list) ? list : [])
  .filter(item => item?.supported !== false)
  .map(item => {
    if (typeof item === 'string') {
      return {
        label: item,
        value: item
      }
    }

    const label = getDataTypeName(item)
    const value = getDataTypeValue(item)

    return label && value !== undefined && value !== null
      ? {
          ...item,
          label: String(label),
          value
        }
      : null
  })
  .filter(Boolean)

const findByteOrderValue = value => byteOrderOptions.value.find(item => String(item.value) === String(value))?.value

const applyPointDataTypeDefaults = ({ overwrite = false } = {}) => {
  const option = selectedPointDataType.value

  if (!option) return

  if (useByteOrderForDevice(pointForm.dataType)) {
    const defaultByteOrder = findByteOrderValue(option.defaultByteOrder) || byteOrderOptions.value[0]?.value
    if (overwrite || !hasValue(pointForm.byteOrder)) pointForm.byteOrder = defaultByteOrder
  } else {
    pointForm.byteOrder = undefined
  }

  if (supportsScaleFactor(pointForm.dataType)) {
    if (overwrite || !hasValue(pointForm.scaleFactor)) pointForm.scaleFactor = option.defaultScaleFactor ?? 1
  } else {
    pointForm.scaleFactor = undefined
  }

  if (supportsEngRange(pointForm.dataType)) {
    // 工程值范围可选：切换类型时清空，不自动填默认值（不填则读写不限制）
    if (overwrite) {
      pointForm.engValueMin = undefined
      pointForm.engValueMax = undefined
    }
  } else {
    pointForm.engValueMin = undefined
    pointForm.engValueMax = undefined
    pointForm.engUnit = ''
  }

  if (supportsDecimalPlaces(pointForm.dataType)) {
    if (overwrite || !hasValue(pointForm.decimalPlaces)) pointForm.decimalPlaces = option.defaultDecimalPlaces ?? 0
  } else {
    pointForm.decimalPlaces = undefined
  }

  if (supportsBcdConvert(pointForm.dataType)) {
    if (overwrite || !hasValue(pointForm.bcdConvert)) pointForm.bcdConvert = option.defaultBcdConvert ?? false
  } else {
    pointForm.bcdConvert = undefined
  }

  if (!supportsBitField(pointForm.dataType)) {
    pointForm.bitField = undefined
  }
}

const loadDataTypeOptions = async (deviceId) => {
  if (!hasValue(deviceId)) {
    dataTypeOptions.value = []
    return
  }

  dataTypeLoading.value = true

  try {
    const response = await getDevicePointDataTypes(deviceId)
    dataTypeOptions.value = normalizeDataTypeOptions(getResponseData(response))
    setFirstOptionIfEmpty(pointForm, 'dataType', dataTypeOptions.value)
    applyPointDataTypeDefaults()
  } catch (error) {
    dataTypeOptions.value = []
  } finally {
    dataTypeLoading.value = false
  }
}

const loadAccessTypeOptions = async () => {
  accessTypeLoading.value = true

  try {
    const response = await getDevicePointAccessTypes()
    accessTypeOptions.value = normalizeOptions(getResponseData(response), getAccessTypeName, getAccessTypeValue)
  } catch (error) {
    accessTypeOptions.value = []
  } finally {
    accessTypeLoading.value = false
  }
}

const loadByteOrderOptions = async (deviceId) => {
  if (!hasValue(deviceId)) {
    byteOrderOptions.value = []
    return
  }

  byteOrderLoading.value = true

  try {
    const response = await getDevicePointByteOrders(deviceId)
    byteOrderOptions.value = normalizeByteOrderOptions(getResponseData(response))
    setFirstOptionIfEmpty(pointForm, 'byteOrder', byteOrderOptions.value)
    applyPointDataTypeDefaults()
  } catch (error) {
    byteOrderOptions.value = []
  } finally {
    byteOrderLoading.value = false
  }
}

const getAccessTypeLabel = value => {
  if (!hasValue(value)) return '-'

  return accessTypeOptions.value.find(item => String(item.value) === String(value))?.label || value
}

const defaultAccessTypeLabel = computed(() => getAccessTypeLabel(defaultAccessType))

const pointImportFields = [
  'code',
  'name',
  'accessType',
  'actualAddress',
  'setpointAddress',
  'dataType',
  'byteOrder',
  'scaleFactor',
  'engValueMin',
  'engValueMax',
  'engUnit',
  'decimalPlaces',
  'bcdConvert',
  'bitField'
]

const pointImportHeaderLabels = [
  '点位编码',
  '点位名称',
  '读写权限',
  '实际值点位',
  '设定值点位',
  '数据类型',
  '字节序',
  '缩放系数',
  '工程值最小值',
  '工程值最大值',
  '工程单位',
  '小数位数',
  'BCD转码',
  '位域'
]

const pointImportGridTemplate = '36px repeat(14, minmax(72px, 1fr))'

const pointImportFieldAliases = {
  code: ['code', 'pointCode', 'point_code', '点位编码', '编码'],
  name: ['name', 'pointName', 'point_name', '点位名称', '名称'],
  accessType: ['accessType', 'access_type', 'access', '读写权限', '读写类型', '读写', '权限'],
  actualAddress: ['actualAddress', 'actual_address', 'actual', '实际值点位', '实际值', '采集地址'],
  setpointAddress: ['setpointAddress', 'setpoint_address', 'setpoint', '设定值点位', '设定值', '写入地址'],
  dataType: ['dataType', 'data_type', 'type', '数据类型', '类型'],
  byteOrder: ['byteOrder', 'byte_order', 'order', '字节序'],
  scaleFactor: ['scaleFactor', 'scale_factor', 'scale', '缩放系数', '系数'],
  engValueMin: ['engValueMin', 'eng_value_min', 'min', '工程值最小值', '工程下限', '工程值下限', '下限', '工程值', '最小值'],
  engValueMax: ['engValueMax', 'eng_value_max', 'max', '工程值最大值', '工程上限', '工程值上限', '上限', '最大值'],
  engUnit: ['engUnit', 'eng_unit', 'unit', '工程单位', '单位'],
  decimalPlaces: ['decimalPlaces', 'decimalPlace', 'decimal_places', 'precision', '小数位数', '小数位', '小数'],
  bcdConvert: ['bcdConvert', 'bcd_convert', 'bcd', 'BCD转码'],
  bitField: ['bitField', 'bit_field', 'field', '位域', '位']
}

const normalizeHeaderText = value => String(value ?? '').trim().replace(/\s+/g, '').toLowerCase()

const getImportFieldByHeader = header => {
  const normalizedHeader = normalizeHeaderText(header)

  return pointImportFields.find(field => (
    field.toLowerCase() === normalizedHeader ||
    pointImportFieldAliases[field].some(alias => normalizeHeaderText(alias) === normalizedHeader)
  ))
}

const resolveOptionValue = (value, options, fallbackMap = {}) => {
  if (!hasValue(value)) return value

  const text = String(value).trim()
  const normalizedText = normalizeHeaderText(text)
  const option = options.find(item => (
    String(item.value) === text ||
    normalizeHeaderText(item.value) === normalizedText ||
    normalizeHeaderText(item.label) === normalizedText ||
    normalizeHeaderText(item.label).startsWith(normalizedText)
  ))

  if (option) return option.value

  const mappedValue = fallbackMap[normalizedText]

  if (mappedValue !== undefined) {
    const mappedOption = options.find(item => String(item.value) === mappedValue)
    if (mappedOption || !options.length) return mappedValue
  }

  return text
}

const splitDelimitedLine = (line, delimiter) => {
  const result = []
  let current = ''
  let quoted = false

  for (let index = 0; index < line.length; index += 1) {
    const char = line[index]
    const nextChar = line[index + 1]

    if (char === '"' && quoted && nextChar === '"') {
      current += '"'
      index += 1
    } else if (char === '"') {
      quoted = !quoted
    } else if (char === delimiter && !quoted) {
      result.push(current.trim())
      current = ''
    } else {
      current += char
    }
  }

  result.push(current.trim())
  return result
}

const isImportHeaderRow = row => row
  .map(getImportFieldByHeader)
  .filter(Boolean)
  .length >= Math.min(3, row.length)

const buildImportPreviewRows = text => {
  const content = trimText(text)

  if (!content) return []

  const delimiter = content.includes('\t') ? '\t' : ','
  const parsedRows = content
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)
    .map(line => splitDelimitedLine(line, delimiter))

  if (!parsedRows.length) return []

  const dataRows = isImportHeaderRow(parsedRows[0]) ? parsedRows.slice(1) : parsedRows

  return dataRows.map(cells => pointImportFields.map((_, index) => cells[index] ?? ''))
}

const pointImportPreviewRows = computed(() => buildImportPreviewRows(pointImportText.value))

const formatPointImportCellValue = value => {
  if (value === undefined || value === null) return ''

  return String(value)
}

const serializePointToImportRow = point => {
  const dataType = point?.dataType
  const canUseByteOrder = useByteOrderForDevice(dataType)
  const canUseScaleFactor = supportsScaleFactor(dataType)
  const canUseEngRange = supportsEngRange(dataType)
  const canUseDecimalPlaces = supportsDecimalPlaces(dataType)
  const canUseBcdConvert = supportsBcdConvert(dataType)
  const canUseBitField = supportsBitField(dataType)
  const accessTypeLabel = getAccessTypeLabel(point?.accessType)

  const cells = {
    code: trimText(point?.code),
    name: trimText(point?.name),
    accessType: accessTypeLabel === '-' ? '' : accessTypeLabel,
    actualAddress: isCollectAccessType(point?.accessType) ? trimText(point?.actualAddress) : '',
    setpointAddress: isWriteAccessType(point?.accessType) ? trimText(point?.setpointAddress) : '',
    dataType: hasValue(point?.dataType) ? point.dataType : '',
    byteOrder: canUseByteOrder && hasValue(point?.byteOrder) ? point.byteOrder : '',
    scaleFactor: canUseScaleFactor ? point?.scaleFactor : '',
    engValueMin: canUseEngRange ? point?.engValueMin : '',
    engValueMax: canUseEngRange ? point?.engValueMax : '',
    engUnit: canUseEngRange ? trimText(point?.engUnit) : '',
    decimalPlaces: canUseDecimalPlaces ? point?.decimalPlaces : '',
    bcdConvert: canUseBcdConvert ? (parseBcdValue(point?.bcdConvert, false) ? '是' : '否') : '',
    bitField: canUseBitField ? (normalizeBitField(point?.bitField) || '') : ''
  }

  return pointImportFields.map(field => formatPointImportCellValue(cells[field])).join('\t')
}

const buildPointImportTextFromList = points => (Array.isArray(points) ? points : [])
  .map(serializePointToImportRow)
  .join('\n')

const parseImportTable = text => {
  const lines = text
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)

  if (!lines.length) return []

  const delimiter = lines.some(line => line.includes('\t')) ? '\t' : ','
  const rows = lines.map(line => splitDelimitedLine(line, delimiter))
  const dataRows = isImportHeaderRow(rows[0]) ? rows.slice(1) : rows

  return dataRows.map(row => {
    const item = {}

    row.forEach((value, index) => {
      const field = pointImportFields[index]
      if (field) item[field] = value
    })

    return item
  })
}

const parsePointImportText = text => {
  const content = trimText(text)

  if (!content) {
    throw new Error('请粘贴从 Excel 复制的表格内容')
  }

  return parseImportTable(content)
}

const normalizeImportPoint = item => {
  const source = {}

  Object.entries(item || {}).forEach(([key, value]) => {
    const field = getImportFieldByHeader(key)
    if (field) source[field] = value
  })

  const accessType = resolveOptionValue(source.accessType, accessTypeOptions.value, {
    readonly: 'READ_ONLY',
    read: 'READ_ONLY',
    '只读': 'READ_ONLY',
    readwrite: 'READ_WRITE',
    rw: 'READ_WRITE',
    '读写': 'READ_WRITE',
    writeonly: 'WRITE_ONLY',
    write: 'WRITE_ONLY',
    '只写': 'WRITE_ONLY'
  })
  const resolvedAccessType = hasValue(trimText(source.accessType))
    ? accessType
    : defaultAccessType
  const dataType = resolveOptionValue(source.dataType, dataTypeOptions.value, {
    bool: 'BOOL',
    boolinv: 'BOOL_INV',
    boolean: 'BOOL',
    '布尔': 'BOOL',
    '布尔型': 'BOOL',
    '布尔型(反相)': 'BOOL_INV',
    short: 'INT',
    ushort: 'UINT',
    sint: 'INT',
    usint: 'UINT',
    int8: 'INT',
    uint8: 'UINT',
    byte: 'UINT',
    int: 'INT',
    integer: 'INT',
    int16: 'INT',
    uint: 'UINT',
    uint16: 'UINT',
    word: 'UINT',
    long: 'LONG',
    ulong: 'ULONG',
    dint: 'LONG',
    udint: 'ULONG',
    int32: 'LONG',
    uint32: 'ULONG',
    dword: 'ULONG',
    // 三菱等「16位浮点数」= 占 2 个 D 字的 Real（别名仍指向 REAL，非已删类型）
    real16: 'REAL',
    '16位浮点数': 'REAL',
    '16位浮点': 'REAL',
    '浮点': 'REAL',
    real: 'REAL',
    lreal: 'LREAL',
    string: 'TEXT8',
    str: 'TEXT8',
    '字符串': 'TEXT8',
    text8: 'TEXT8',
    float: 'REAL',
    double: 'LREAL'
  })
  const byteOrder = resolveOptionValue(source.byteOrder, byteOrderOptions.value)
  const dataTypeOption = getDataTypeOption(dataType)
  const canUseByteOrder = useByteOrderForDevice(dataType)
  const canUseScaleFactor = supportsScaleFactor(dataType)
  const canUseEngRange = supportsEngRange(dataType)
  const canUseDecimalPlaces = supportsDecimalPlaces(dataType)
  const canUseBcdConvert = supportsBcdConvert(dataType)
  const canUseBitField = supportsBitField(dataType)

  return normalizePoint({
    code: trimText(source.code),
    name: trimText(source.name),
    accessType: resolvedAccessType,
    actualAddress: isCollectAccessType(resolvedAccessType) ? trimText(source.actualAddress) : '',
    setpointAddress: isWriteAccessType(resolvedAccessType) ? trimText(source.setpointAddress) : '',
    dataType,
    byteOrder: canUseByteOrder ? (byteOrder || findByteOrderValue(dataTypeOption?.defaultByteOrder) || byteOrderOptions.value[0]?.value) : undefined,
    scaleFactor: canUseScaleFactor ? normalizeNumber(source.scaleFactor, dataTypeOption?.defaultScaleFactor ?? 1) : undefined,
    engValueMin: canUseEngRange && hasValue(source.engValueMin) ? normalizeNumber(source.engValueMin) : undefined,
    engValueMax: canUseEngRange && hasValue(source.engValueMax) ? normalizeNumber(source.engValueMax) : undefined,
    engUnit: canUseEngRange ? trimText(source.engUnit) : '',
    decimalPlaces: canUseDecimalPlaces ? normalizeNumber(source.decimalPlaces, dataTypeOption?.defaultDecimalPlaces ?? 0) : undefined,
    bcdConvert: canUseBcdConvert
      ? parseBcdValue(source.bcdConvert, dataTypeOption?.defaultBcdConvert ?? false)
      : undefined,
    bitField: canUseBitField ? normalizeBitField(source.bitField) : undefined
  })
}

const validateImportPoint = point => {
  const missing = []
  const canUseByteOrder = useByteOrderForDevice(point.dataType)
  const canUseScaleFactor = supportsScaleFactor(point.dataType)
  const canUseEngRange = supportsEngRange(point.dataType)
  const canUseDecimalPlaces = supportsDecimalPlaces(point.dataType)
  const canUseBcdConvert = supportsBcdConvert(point.dataType)
  const hasKnownAccessType = !accessTypeOptions.value.length ||
    accessTypeOptions.value.some(item => String(item.value) === String(point.accessType))
  const hasKnownDataType = !dataTypeOptions.value.length ||
    dataTypeOptions.value.some(item => String(item.value) === String(point.dataType))
  const hasKnownByteOrder = !canUseByteOrder ||
    !byteOrderOptions.value.length ||
    byteOrderOptions.value.some(item => String(item.value) === String(point.byteOrder))
  const hasKnownBcdConvert = !canUseBcdConvert ||
    bcdOptions.some(item => item.value === parseBcdValue(point.bcdConvert, false))

  if (!trimText(point.code)) missing.push('点位编码')
  if (!trimText(point.name)) missing.push('点位名称')
  if (!hasValue(point.accessType)) missing.push('读写权限')
  if (hasValue(point.accessType) && !hasKnownAccessType) missing.push('有效读写权限')
  if (isCollectAccessType(point.accessType) && !trimText(point.actualAddress)) missing.push('实际值点位')
  if (isWriteAccessType(point.accessType) && !trimText(point.setpointAddress)) missing.push('设定值点位')
  if (!hasValue(point.dataType)) missing.push('数据类型')
  if (hasValue(point.dataType) && !hasKnownDataType) missing.push('有效数据类型')
  if (canUseByteOrder && !hasValue(point.byteOrder)) missing.push('字节序')
  if (canUseByteOrder && hasValue(point.byteOrder) && !hasKnownByteOrder) missing.push('有效字节序')
  if (canUseScaleFactor && !hasValue(point.scaleFactor)) missing.push('缩放系数')
  if (
    canUseEngRange &&
    hasValue(point.engValueMin) &&
    hasValue(point.engValueMax) &&
    Number(point.engValueMin) > Number(point.engValueMax)
  ) {
    missing.push('有效工程值范围（下限不能大于上限）')
  }
  if (canUseDecimalPlaces && !hasValue(point.decimalPlaces)) missing.push('小数位数')
  if (canUseBcdConvert && !hasValue(point.bcdConvert) && point.bcdConvert !== false) missing.push('BCD转码')
  if (canUseBcdConvert && hasValue(point.bcdConvert) && !hasKnownBcdConvert) missing.push('有效BCD转码')

  return missing
}

const pointColumns = [
  { title: '点位编码', dataIndex: 'code', key: 'code', width: 130 },
  { title: '点位名称', dataIndex: 'name', key: 'name', width: 160 },
  { title: '读写权限', dataIndex: 'accessType', key: 'accessType', width: 100 },
  { title: '实际值点位', dataIndex: 'actualAddress', key: 'actualAddress', width: 140 },
  { title: '设定值点位', dataIndex: 'setpointAddress', key: 'setpointAddress', width: 140 },
  { title: '数据类型', key: 'dataType', width: 120 },
  { title: '字节序', key: 'byteOrder', width: 120 },
  { title: '缩放系数', dataIndex: 'scaleFactor', key: 'scaleFactor', width: 110 },
  { title: 'BCD转码', dataIndex: 'bcdConvert', key: 'bcdConvert', width: 110 },
  { title: '位域', key: 'bitField', width: 100 },
  { title: '工程值范围', key: 'engValueRange', width: 150 },
  { title: '操作', key: 'action', width: 140, fixed: 'right' }
]

const syncPointListToDeviceRecord = detailRecord => {
  const points = normalizePoints(getDevicePoints(detailRecord))
  const collectConfig = detailRecord?.collectConfig && !Array.isArray(detailRecord.collectConfig)
    ? detailRecord.collectConfig
    : {}

  currentPointDevice.value = {
    ...detailRecord,
    collectConfig: {
      ...collectConfig,
      points
    }
  }
  pointList.value = points
}

const refreshCurrentPointDevice = async () => {
  const deviceId = getRecordDeviceId(currentPointDevice.value)

  if (!hasValue(deviceId)) return

  const response = await getDeviceDetail(deviceId)
  const detailRecord = buildPointDetailRecord(response, currentPointDevice.value)

  syncPointListToDeviceRecord(detailRecord)
}

const handleOpenPointList = async record => {
  const id = getRecordId(record)

  if (!hasValue(id)) {
    message.warning('缺少设备ID')
    open.value = false
    return
  }

  currentPointDevice.value = record
  pointList.value = []
  dataTypeOptions.value = []
  byteOrderOptions.value = []
  pointLoading.value = true

  let detailRecord = record

  try {
    const response = await getDeviceDetail(id)
    detailRecord = buildPointDetailRecord(response, record)
    syncPointListToDeviceRecord(detailRecord)
    await Promise.all([
      loadDataTypeOptions(getRecordDeviceId(detailRecord)),
      loadByteOrderOptions(getRecordDeviceId(detailRecord)),
      loadAccessTypeOptions()
    ])
  } catch (error) {
    await Promise.all([
      loadDataTypeOptions(getRecordDeviceId(record)),
      loadByteOrderOptions(getRecordDeviceId(record)),
      loadAccessTypeOptions()
    ])
  } finally {
    pointLoading.value = false
  }

  syncPointListToDeviceRecord(detailRecord)
}

const handleClosePointList = () => {
  open.value = false
  currentPointDevice.value = null
  pointList.value = []
  dataTypeOptions.value = []
  byteOrderOptions.value = []
  pointVisible.value = false
  handleCancelPointImport()
  handleCancelCopyFromDevice()
  handleCancelPoint()
}

const handleSavePointList = async () => {
  const deviceId = getRecordDeviceId(currentPointDevice.value)

  if (!hasValue(deviceId)) {
    message.warning('缺少设备ID')
    return
  }

  pointSaving.value = true

  try {
    await saveDevicePoint({
      deviceId,
      items: pointList.value.map(buildPointPayload)
    })
    await refreshCurrentPointDevice()
    emit('saved')
    message.success('保存成功')
    handleClosePointList()
  } finally {
    pointSaving.value = false
  }
}

const handleAddPoint = () => {
  if (!currentPointDevice.value) {
    message.warning('请先选择设备')
    return
  }

  if (isPointDeviceEnabled.value) {
    message.warning('设备启动状态下不允许编辑点位')
    return
  }

  editPointMode.value = false
  Object.assign(pointForm, createEmptyPointForm(defaultAccessType))
  setFirstOptionIfEmpty(pointForm, 'accessType', accessTypeOptions.value)
  if (!accessTypeOptions.value.some(item => String(item.value) === String(pointForm.accessType))) {
    pointForm.accessType = defaultAccessType
  }
  setFirstOptionIfEmpty(pointForm, 'dataType', dataTypeOptions.value)
  setFirstOptionIfEmpty(pointForm, 'byteOrder', byteOrderOptions.value)
  applyPointDataTypeDefaults({ overwrite: true })
  pointVisible.value = true
}

const handleOpenPointImport = () => {
  if (!currentPointDevice.value) {
    message.warning('请先选择设备')
    return
  }

  if (isPointDeviceEnabled.value) {
    message.warning('设备启动状态下不允许编辑点位')
    return
  }

  pointImportText.value = ''
  pointImportRawEdit.value = false
  pointImportVisible.value = true
}

const loadCopySourceDevices = async () => {
  const protocol = deviceProtocol.value
  const currentId = getRecordDeviceId(currentPointDevice.value)

  pointCopyDeviceLoading.value = true
  pointCopyDeviceOptions.value = []

  try {
    const response = await getDevicePage(cleanPayload({
      protocol: protocol || undefined,
      page: 1,
      size: 500
    }))
    const records = getResponseData(response)?.records || []
    pointCopyDeviceOptions.value = records
      .filter(item => String(getRecordDeviceId(item)) !== String(currentId))
      .filter(item => !protocol || String(item?.protocol || '').toUpperCase() === protocol)
      .map(item => ({
        label: `${item.name || '-'}（${item.code || '-'}）`,
        value: getRecordDeviceId(item)
      }))
      .filter(item => hasValue(item.value))
  } catch (error) {
    pointCopyDeviceOptions.value = []
  } finally {
    pointCopyDeviceLoading.value = false
  }
}

const handleOpenCopyFromDevice = async () => {
  if (!currentPointDevice.value) {
    message.warning('请先选择设备')
    return
  }

  if (isPointDeviceEnabled.value) {
    message.warning('设备启动状态下不允许编辑点位')
    return
  }

  if (!deviceProtocol.value) {
    message.warning('当前设备未配置通信协议')
    return
  }

  pointCopySourceId.value = undefined
  pointCopyVisible.value = true
  await loadCopySourceDevices()

  if (!pointCopyDeviceOptions.value.length) {
    message.warning('暂无同协议可复制的设备')
  }
}

const handleCancelCopyFromDevice = () => {
  pointCopyVisible.value = false
  pointCopySourceId.value = undefined
  pointCopyDeviceOptions.value = []
  pointCopying.value = false
}

const applyCopiedPoints = points => {
  pointList.value = normalizePoints(points)
  message.success(points.length ? `已复制 ${points.length} 个点位到列表，请确认后保存` : '源设备暂无点位')
  handleCancelCopyFromDevice()
}

const handleSubmitCopyFromDevice = () => {
  if (!hasValue(pointCopySourceId.value)) {
    message.warning('请选择源设备')
    return Promise.reject()
  }

  const doCopy = async () => {
    pointCopying.value = true

    try {
      const response = await getDeviceDetail(pointCopySourceId.value)
      applyCopiedPoints(getResponseData(response) || [])
    } catch (error) {
      // 请求拦截器会统一提示错误。
      return Promise.reject(error)
    } finally {
      pointCopying.value = false
    }
  }

  if (!pointList.value.length) {
    return doCopy()
  }

  return new Promise((resolve, reject) => {
    Modal.confirm({
      title: '确认覆盖',
      content: '复制将覆盖当前点位列表中的全部点位，是否继续？',
      okText: '确定',
      cancelText: '取消',
      onOk: () => doCopy().then(resolve).catch(reject),
      onCancel: () => reject()
    })
  })
}

const handleFillCurrentPoints = () => {
  if (!pointList.value.length) {
    message.warning('当前没有点位可带入')
    return
  }

  const fill = () => {
    pointImportText.value = buildPointImportTextFromList(pointList.value)
    pointImportRawEdit.value = false
    message.success(`已带入 ${pointList.value.length} 个点位`)
  }

  if (trimText(pointImportText.value)) {
    Modal.confirm({
      title: '确认带入',
      content: '粘贴区已有内容，带入将覆盖现有内容，是否继续？',
      okText: '确定',
      cancelText: '取消',
      onOk: fill
    })
    return
  }

  fill()
}

const handleClearPointImport = () => {
  pointImportText.value = ''
  pointImportRawEdit.value = false
  pointImportInputRef.value?.focus()
}

const handleCancelPointImport = () => {
  pointImportVisible.value = false
  pointImporting.value = false
  pointImportText.value = ''
  pointImportRawEdit.value = false
}

const handleSubmitPointImport = () => {
  pointImporting.value = true

  try {
    const rawPoints = parsePointImportText(pointImportText.value)
    const importedPoints = rawPoints
      .map(normalizeImportPoint)
      .filter(point => trimText(point.code) || trimText(point.name) || trimText(point.actualAddress) || trimText(point.setpointAddress))
    const invalidRows = importedPoints
      .map((point, index) => ({ row: index + 1, missing: validateImportPoint(point) }))
      .filter(item => item.missing.length)

    if (!importedPoints.length) {
      message.warning('没有解析到可导入的点位')
      return
    }

    if (invalidRows.length) {
      message.warning(`第 ${invalidRows[0].row} 行缺少：${invalidRows[0].missing.join('、')}`)
      return
    }

    pointList.value.push(...importedPoints)
    message.success(`已导入 ${importedPoints.length} 个点位，请点击确定保存`)
    handleCancelPointImport()
  } catch (error) {
    message.warning(error?.message || '导入内容解析失败')
  } finally {
    pointImporting.value = false
  }
}

const handleEditPoint = (record) => {
  if (isPointDeviceEnabled.value) {
    message.warning('设备启动状态下不允许编辑点位')
    return
  }

  editPointMode.value = true
  Object.assign(pointForm, createEmptyPointForm(defaultAccessType), normalizePoint(record))
  if (!accessTypeOptions.value.some(item => String(item.value) === String(pointForm.accessType))) {
    pointForm.accessType = defaultAccessType
  }
  applyPointDataTypeDefaults()
  pointVisible.value = true
}

const handlePointDataTypeChange = () => {
  applyPointDataTypeDefaults({ overwrite: true })
}

const handleAccessTypeChange = () => {
  if (!requiresActualAddress.value) pointForm.actualAddress = ''
  if (!requiresSetpointAddress.value) pointForm.setpointAddress = ''
}

const handleDeletePoint = (record) => {
  if (isPointDeviceEnabled.value) {
    message.warning('设备启动状态下不允许编辑点位')
    return
  }

  Modal.confirm({
    title: '确认删除',
    content: `确定要删除点位「${record.name}」吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: () => {
      const index = pointList.value.findIndex(item => item.__rowKey === record.__rowKey)

      if (index !== -1) {
        pointList.value.splice(index, 1)
      }

      message.success('删除成功')
    }
  })
}

const handleSubmitPoint = async () => {
  const code = trimText(pointForm.code)
  const name = trimText(pointForm.name)
  const actualAddress = requiresActualAddress.value ? trimText(pointForm.actualAddress) : ''
  const setpointAddress = requiresSetpointAddress.value ? trimText(pointForm.setpointAddress) : ''

  if (
    !code ||
    !name ||
    !hasValue(pointForm.accessType) ||
    (requiresActualAddress.value && !actualAddress) ||
    (requiresSetpointAddress.value && !setpointAddress) ||
    !hasValue(pointForm.dataType) ||
    (currentDataTypeSupportsByteOrder.value && !hasValue(pointForm.byteOrder)) ||
    (currentDataTypeSupportsScaleFactor.value && !hasValue(pointForm.scaleFactor)) ||
    (currentDataTypeSupportsDecimalPlaces.value && !hasValue(pointForm.decimalPlaces)) ||
    (currentDataTypeSupportsBcdConvert.value && !hasValue(pointForm.bcdConvert) && pointForm.bcdConvert !== false)
  ) {
    message.warning('请填写点位必填信息')
    return
  }

  if (
    currentDataTypeSupportsEngRange.value &&
    hasValue(pointForm.engValueMin) &&
    hasValue(pointForm.engValueMax) &&
    Number(pointForm.engValueMin) > Number(pointForm.engValueMax)
  ) {
    message.warning('工程值最小值不能大于最大值')
    return
  }

  const submitPoint = cleanPayload({
    code,
    name,
    accessType: pointForm.accessType,
    actualAddress: requiresActualAddress.value ? actualAddress : null,
    setpointAddress: requiresSetpointAddress.value ? setpointAddress : null,
    dataType: pointForm.dataType,
    byteOrder: currentDataTypeSupportsByteOrder.value ? pointForm.byteOrder : null,
    scaleFactor: currentDataTypeSupportsScaleFactor.value ? pointForm.scaleFactor : null,
    engUnit: currentDataTypeSupportsEngRange.value ? trimText(pointForm.engUnit) : null,
    engValueMin: currentDataTypeSupportsEngRange.value ? pointForm.engValueMin : null,
    engValueMax: currentDataTypeSupportsEngRange.value ? pointForm.engValueMax : null,
    decimalPlaces: currentDataTypeSupportsDecimalPlaces.value ? pointForm.decimalPlaces : null,
    bcdConvert: currentDataTypeSupportsBcdConvert.value ? Boolean(pointForm.bcdConvert) : null,
    bitField: currentDataTypeSupportsBitField.value
      ? normalizeBitField(pointForm.bitField)
      : null
  })

  const localPoint = normalizePoint({
    ...submitPoint,
    __rowKey: pointForm.__rowKey
  })

  if (editPointMode.value) {
    const index = pointList.value.findIndex(item => item.__rowKey === pointForm.__rowKey)

    if (index !== -1) {
      pointList.value.splice(index, 1, localPoint)
    }
  } else {
    pointList.value.push(localPoint)
  }

  message.success(editPointMode.value ? '编辑成功' : '添加成功')
  pointVisible.value = false
  handleCancelPoint()
}

const handleCancelPoint = () => {
  editPointMode.value = false
  Object.assign(pointForm, createEmptyPointForm(defaultAccessType))
  pointFormRef.value?.resetFields()
}

watch(open, value => {
  if (!value) return

  handleOpenPointList(props.device)
})
</script>

<style scoped>
.point-toolbar {
  margin-bottom: 12px !important;
}

.point-list-table {
  margin-bottom: 0;
}

.point-import-help {
  line-height: 1.8;
}

.point-import-toolbar {
  margin-bottom: 8px;
}

.point-import-sheet {
  border: 1px solid #c4c4c4;
  border-radius: 2px;
  background: #fff;
  overflow: hidden;
}

.point-import-sheet-scroll {
  overflow: auto;
  max-height: 420px;
}

.point-import-sheet-grid {
  display: grid;
  min-width: 1100px;
}

.point-import-sheet-row {
  display: contents;
}

.point-import-cell {
  padding: 4px 8px;
  border-right: 1px solid #e8e8e8;
  border-bottom: 1px solid #e8e8e8;
  font-size: 12px;
  line-height: 20px;
  min-height: 28px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: #fff;
}

.point-import-cell--corner {
  background: #f3f3f3;
  border-right: 1px solid #d4d4d4;
}

.point-import-cell--header {
  background: #f3f3f3;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
  text-align: center;
  border-bottom: 1px solid #c4c4c4;
}

.point-import-cell--row-num {
  background: #f3f3f3;
  color: rgba(0, 0, 0, 0.45);
  text-align: center;
  font-weight: 500;
  border-right: 1px solid #d4d4d4;
}

.point-import-sheet-editor-wrap {
  display: contents;
}

.point-import-sheet-edit-bar {
  grid-column: 1 / -1;
  padding: 4px 12px 8px;
  background: #fafafa;
  border-bottom: 1px solid #e8e8e8;
}

.point-import-cell--editor {
  grid-column: 2 / -1;
  padding: 0;
  background: #fff;
}

.point-import-sheet-textarea {
  width: 100%;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12px;
  line-height: 20px;
  border-radius: 0 !important;
  resize: none;
}

.point-import-sheet-textarea:focus {
  box-shadow: none !important;
}

.point-import-sheet-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fafafa;
  border-top: 1px solid #e8e8e8;
}

.point-import-sheet-count {
  margin-left: auto;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}
</style>

<style>
/* Modal 挂载到 body，需非 scoped；保证表体在 footer 之上完整可滚 */
.device-point-list-modal .ant-modal {
  max-width: calc(100vw - 32px);
  padding-bottom: 0;
}

.device-point-list-modal .ant-modal-content {
  max-height: calc(100vh - 32px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.device-point-list-modal .ant-modal-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding-bottom: 16px;
}
</style>
