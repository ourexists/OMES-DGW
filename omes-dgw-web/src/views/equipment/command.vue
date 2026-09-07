<template>
  <div class="page-container command-list-page">
    <div class="page-header">
      <h2>指令记录</h2>
    </div>

    <a-card class="filter-card" size="small" style="margin-bottom: 16px">
      <a-form layout="inline" :model="searchForm" @finish="handleSearch">
        <a-form-item name="taskId" label="任务ID">
          <a-input v-model:value="searchForm.taskId" placeholder="请输入" style="width: 220px" />
        </a-form-item>
        <a-form-item name="deviceId" label="设备ID">
          <a-input v-model:value="searchForm.deviceId" placeholder="请输入" style="width: 180px" />
        </a-form-item>
        <a-form-item name="address" label="写入地址">
          <a-input v-model:value="searchForm.address" placeholder="任务含该地址" style="width: 150px" />
        </a-form-item>
        <a-form-item name="status" label="状态">
          <a-select
            v-model:value="searchForm.status"
            :options="statusOptions"
            allow-clear
            placeholder="请选择状态"
            style="width: 140px"
          />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" html-type="submit">查询</a-button>
            <a-button @click="handleReset">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card class="list-card">
      <a-table
        :columns="taskColumns"
        :data-source="tableData"
        :loading="tableLoading"
        :pagination="pagination"
        :scroll="{ x: 1100, y: 'calc(100% - 44px)' }"
        :expanded-row-keys="expandedRowKeys"
        row-key="taskId"
        size="small"
        @change="handleTableChange"
        @expand="handleExpand"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'taskId'">
            <span class="mono-text">{{ formatEmpty(record.taskId) }}</span>
          </template>
          <template v-if="column.key === 'deviceIds'">
            <span class="message-text" :title="formatEmpty(record.deviceIds)">
              {{ formatEmpty(record.deviceIds) }}
            </span>
          </template>
          <template v-if="column.key === 'progress'">
            <span>
              {{ record.successCount || 0 }}/{{ record.commandCount || 0 }}
              <span v-if="record.failedCount" class="failed-hint">失败 {{ record.failedCount }}</span>
            </span>
          </template>
          <template v-if="column.key === 'status'">
            <a-tag :color="getStatusColor(record.status)">
              {{ getStatusText(record.status) }}
            </a-tag>
          </template>
          <template v-if="column.key === 'createTime'">
            {{ formatTime(record.createTime) }}
          </template>
          <template v-if="column.key === 'lastExecutedTime'">
            {{ formatTime(record.lastExecutedTime) }}
          </template>
          <template v-if="column.key === 'action'">
            <a-button type="link" size="small" @click="toggleExpand(record)">
              {{ isExpanded(record.taskId) ? '收起' : '指令明细' }}
            </a-button>
          </template>
        </template>

        <template #expandedRowRender="{ record }">
          <a-table
            :columns="commandColumns"
            :data-source="commandCache[record.taskId] || []"
            :loading="!!commandLoadingMap[record.taskId]"
            :pagination="false"
            row-key="commandId"
            size="small"
            class="inner-command-table"
          >
            <template #bodyCell="{ column, record: command }">
              <template v-if="column.key === 'writeValue'">
                <span class="mono-text">{{ formatEmpty(command.value) }}</span>
              </template>
              <template v-if="column.key === 'status'">
                <a-tag :color="getStatusColor(command.status)">
                  {{ getStatusText(command.status) }}
                </a-tag>
              </template>
              <template v-if="column.key === 'writeTime'">
                {{ formatTime(command.executedTime) }}
              </template>
              <template v-if="column.key === 'message'">
                <span class="message-text" :title="formatEmpty(command.errorMessage)">
                  {{ formatEmpty(command.errorMessage) }}
                </span>
              </template>
              <template v-if="column.key === 'action'">
                <a-button type="link" size="small" @click="handleViewCommand(command)">详情</a-button>
              </template>
            </template>
          </a-table>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="detailVisible"
      title="指令详情"
      width="760px"
      :footer="null"
    >
      <a-descriptions v-if="currentRecord" :column="2" bordered size="small">
        <a-descriptions-item label="指令ID">{{ formatEmpty(currentRecord.commandId) }}</a-descriptions-item>
        <a-descriptions-item label="任务ID">{{ formatEmpty(currentRecord.taskId) }}</a-descriptions-item>
        <a-descriptions-item label="序号">{{ formatEmpty(currentRecord.seqNo) }}</a-descriptions-item>
        <a-descriptions-item label="设备ID">{{ formatEmpty(currentRecord.deviceId) }}</a-descriptions-item>
        <a-descriptions-item label="写入地址">{{ formatEmpty(currentRecord.address) }}</a-descriptions-item>
        <a-descriptions-item label="数据类型">{{ formatEmpty(currentRecord.dataType) }}</a-descriptions-item>
        <a-descriptions-item label="字节序">{{ formatEmpty(currentRecord.byteOrder) }}</a-descriptions-item>
        <a-descriptions-item label="缩放系数">{{ formatEmpty(currentRecord.scaleFactor) }}</a-descriptions-item>
        <a-descriptions-item label="写入值">{{ formatEmpty(currentRecord.value) }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ getStatusText(currentRecord.status) }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ formatTime(currentRecord.createTime) }}</a-descriptions-item>
        <a-descriptions-item label="执行时间">{{ formatTime(currentRecord.executedTime) }}</a-descriptions-item>
        <a-descriptions-item label="结果信息" :span="2">
          {{ formatEmpty(currentRecord.errorMessage) }}
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { getDevicePointWriteHistory, getDevicePointWriteTaskHistory } from '@/api/equipment'

const searchForm = reactive({
  taskId: '',
  deviceId: '',
  address: '',
  status: undefined
})

const tableData = ref([])
const tableLoading = ref(false)
const detailVisible = ref(false)
const currentRecord = ref(null)
const expandedRowKeys = ref([])
const commandCache = reactive({})
const commandLoadingMap = reactive({})

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showQuickJumper: true,
  pageSizeOptions: ['10', '20', '50', '100'],
  showTotal: total => `共 ${total} 个任务`
})

const statusOptions = [
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
  { label: '部分失败', value: 'PARTIAL' },
  { label: '执行中', value: 'RUNNING' },
  { label: '等待中', value: 'PENDING' }
]

const taskColumns = [
  { title: '任务ID', key: 'taskId', width: 280 },
  { title: '设备ID', key: 'deviceIds', width: 220 },
  { title: '指令进度', key: 'progress', width: 140 },
  { title: '状态', key: 'status', width: 110 },
  { title: '创建时间', key: 'createTime', width: 180 },
  { title: '最近执行', key: 'lastExecutedTime', width: 180 },
  { title: '操作', key: 'action', width: 110, fixed: 'right' }
]

const commandColumns = [
  { title: '序号', dataIndex: 'seqNo', key: 'seqNo', width: 70 },
  { title: '指令ID', dataIndex: 'commandId', key: 'commandId', width: 180 },
  { title: '设备ID', dataIndex: 'deviceId', key: 'deviceId', width: 180 },
  { title: '写入地址', dataIndex: 'address', key: 'address', width: 140 },
  { title: '数据类型', dataIndex: 'dataType', key: 'dataType', width: 100 },
  { title: '写入值', key: 'writeValue', width: 120 },
  { title: '状态', key: 'status', width: 90 },
  { title: '结果信息', key: 'message', width: 180 },
  { title: '执行时间', key: 'writeTime', width: 170 },
  { title: '操作', key: 'action', width: 80 }
]

const trimText = value => (typeof value === 'string' ? value.trim() : '')

const cleanPayload = payload => Object.fromEntries(
  Object.entries(payload).filter(([, value]) => value !== undefined && value !== null && value !== '')
)

const getResponseData = response => response?.data ?? response

const buildPagePayload = () => cleanPayload({
  taskId: trimText(searchForm.taskId),
  deviceId: trimText(searchForm.deviceId),
  address: trimText(searchForm.address),
  taskStatus: searchForm.status,
  page: pagination.current,
  size: pagination.pageSize
})

const formatEmpty = value => {
  if (value === undefined || value === null || value === '') return '-'

  return value
}

const formatTime = value => {
  if (!value) return '-'

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value

  return date.toLocaleString()
}

const getStatusText = status => {
  const statusTextMap = {
    SUCCESS: '成功',
    FAILED: '失败',
    PARTIAL: '部分失败',
    RUNNING: '执行中',
    PENDING: '等待中'
  }

  const key = String(status || '').toUpperCase()

  return statusTextMap[key] || formatEmpty(status)
}

const getStatusColor = status => {
  const key = String(status || '').toUpperCase()

  if (key === 'SUCCESS') return 'success'
  if (key === 'FAILED') return 'error'
  if (key === 'PARTIAL') return 'warning'
  if (key === 'RUNNING') return 'processing'
  if (key === 'PENDING') return 'default'

  return 'default'
}

const isExpanded = taskId => expandedRowKeys.value.includes(taskId)

const loadTaskHistory = async () => {
  tableLoading.value = true

  try {
    const response = await getDevicePointWriteTaskHistory(buildPagePayload())
    const data = getResponseData(response)
    const records = Array.isArray(data?.records) ? data.records : []

    tableData.value = records
    pagination.total = Number(data?.total) || 0
    expandedRowKeys.value = []
  } catch (error) {
    tableData.value = []
    pagination.total = 0
    expandedRowKeys.value = []
  } finally {
    tableLoading.value = false
  }
}

const loadTaskCommands = async taskId => {
  if (!taskId || commandCache[taskId]) return

  commandLoadingMap[taskId] = true

  try {
    const response = await getDevicePointWriteHistory({
      taskId,
      page: 1,
      size: 200
    })
    const data = getResponseData(response)
    const records = Array.isArray(data?.records) ? data.records : []
    commandCache[taskId] = [...records].sort((a, b) => (a.seqNo || 0) - (b.seqNo || 0))
  } catch (error) {
    commandCache[taskId] = []
  } finally {
    commandLoadingMap[taskId] = false
  }
}

const handleExpand = (expanded, record) => {
  const taskId = record?.taskId
  if (!taskId) return

  if (expanded) {
    if (!expandedRowKeys.value.includes(taskId)) {
      expandedRowKeys.value = [...expandedRowKeys.value, taskId]
    }
    loadTaskCommands(taskId)
  } else {
    expandedRowKeys.value = expandedRowKeys.value.filter(id => id !== taskId)
  }
}

const toggleExpand = record => {
  const taskId = record?.taskId
  if (!taskId) return

  const expanded = !isExpanded(taskId)
  handleExpand(expanded, record)
}

const handleSearch = () => {
  pagination.current = 1
  Object.keys(commandCache).forEach(key => delete commandCache[key])
  loadTaskHistory()
}

const handleReset = () => {
  searchForm.taskId = ''
  searchForm.deviceId = ''
  searchForm.address = ''
  searchForm.status = undefined
  pagination.current = 1
  Object.keys(commandCache).forEach(key => delete commandCache[key])
  loadTaskHistory()
}

const handleTableChange = nextPagination => {
  pagination.current = nextPagination.current
  pagination.pageSize = nextPagination.pageSize
  loadTaskHistory()
}

const handleViewCommand = record => {
  currentRecord.value = { ...record }
  detailVisible.value = true
}

onMounted(() => {
  loadTaskHistory()
})
</script>

<style scoped>
.command-list-page {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.command-list-page .page-header,
.command-list-page .filter-card {
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

.list-card :deep(.ant-table-container),
.list-card :deep(.ant-table-body) {
  height: 100%;
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

.mono-text {
  font-family: Consolas, 'Courier New', monospace;
}

.message-text {
  display: inline-block;
  max-width: 210px;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
}

.failed-hint {
  margin-left: 6px;
  color: #cf1322;
  font-size: 12px;
}

.inner-command-table {
  margin: 4px 0 8px 24px;
}

.inner-command-table :deep(.ant-table) {
  height: auto;
  overflow: visible;
}
</style>
