<template>
  <div class="page-container history-chart-page">
    <div class="page-header">
      <h2>历史曲线 - {{deviceName}}</h2>
      <a-space>
        <a-button @click="goBack">
          <LeftOutlined /> 返回
        </a-button>
      </a-space>
    </div>
    <a-card class="toolbar-card" size="small">
      <a-form class="filter-form" layout="inline" :model="searchForm">
        <a-form-item v-if="analogPointOptions.length" label="曲线点位">
          <a-select
            v-model:value="searchForm.pointNames"
            mode="multiple"
            allow-clear
            max-tag-count="responsive"
            :options="analogPointOptions"
            placeholder="请选择曲线点位"
            style="min-width: 180px; max-width: 280px; width: 220px"
          />
        </a-form-item>
        <a-form-item v-if="boolPointOptions.length" label="开关量">
          <a-select
            v-model:value="searchForm.boolPointNames"
            mode="multiple"
            allow-clear
            max-tag-count="responsive"
            :options="boolPointOptions"
            placeholder="请选择开关量点位"
            style="min-width: 180px; max-width: 280px; width: 220px"
          />
        </a-form-item>
        <a-form-item label="时间段">
          <a-range-picker
            v-model:value="searchForm.dateRange"
            show-time
            format="YYYY-MM-DD HH:mm"
            style="width: 340px"
          />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" :loading="chartLoading" @click="handleSearch">查询</a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card
      class="content-card"
      :tab-list="viewTabs"
      :active-tab-key="activeTab"
      @tab-change="handleTabChange"
    >
      <a-spin :spinning="chartLoading">
        <template v-if="activeTab === 'chart'">
          <div v-if="!chartData.length && !ganttRows.length" class="chart-empty">
            <a-empty description="暂无曲线数据" />
          </div>
          <div
            v-else
            class="chart-workspace"
            :class="{ 'has-lines': chartData.length, 'has-gantt': ganttRows.length }"
          >
            <section v-if="chartData.length" class="chart-panel line-panel">
              <div class="chart-panel-head">数值曲线</div>
              <div class="line-charts" :class="{ single: chartData.length === 1 }">
                <div
                  v-for="(item, index) in chartData"
                  :key="`${item.key || item.name}-${index}`"
                  class="line-chart-item"
                >
                  <div class="line-chart-title">
                    {{ item.unit ? `${item.name}（${item.unit}）` : (item.title || item.name) }}
                  </div>
                  <div :ref="el => setChartRef(el, index)" class="temperature-chart"></div>
                </div>
              </div>
            </section>
            <section v-if="ganttRows.length" class="chart-panel gantt-panel">
              <div class="chart-panel-head">
                <span>开关量甘特图</span>
                <span class="gantt-legend">
                  <i class="gantt-swatch on"></i>开
                  <i class="gantt-swatch off"></i>关
                </span>
              </div>
              <div ref="ganttEl" class="gantt-chart" :style="{ height: ganttHeight }"></div>
            </section>
          </div>
        </template>
        <div v-else class="history-table-wrap">
          <a-table
            :columns="historyColumns"
            :data-source="historyList"
            :pagination="{ pageSize: 20, showSizeChanger: true, showTotal: total => `共 ${total} 条` }"
            :scroll="{ x: historyTableScrollX, y: 'calc(100% - 44px)' }"
            sticky
            size="small"
            row-key="key"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'time'">
                {{ formatTableTime(record.time) }}
              </template>
              <template v-if="column.pointName">
                {{ formatValue(record.values?.[column.pointName], column.meta) }}
              </template>
            </template>
          </a-table>
        </div>
      </a-spin>
    </a-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { LeftOutlined } from '@ant-design/icons-vue'
import * as echarts from 'echarts'
import dayjs from 'dayjs'
import { getDeviceDetail, getDeviceSnapshotChar, getDeviceSnapshotHistory } from '@/api/equipment'
import { parseSwitchOn } from '@/utils/switch-status'

const BOOL_DATA_TYPES = new Set(['BOOL', 'BOOL_INV'])
const COLLECT_ACCESS_TYPES = ['READ_ONLY', 'READ_WRITE']
const GANTT_ON_COLOR = '#52c41a'
const GANTT_OFF_COLOR = '#d9d9d9'
const LINE_COLORS = ['#1677ff', '#52c41a', '#faad14', '#f5222d']
const viewTabs = [
  { key: 'chart', tab: '历史曲线' },
  { key: 'table', tab: '历史数据' }
]

const router = useRouter()
const route = useRoute()

const chartLoading = ref(false)
const chartData = ref([])
const chartRefs = ref([])
const ganttEl = ref(null)
const ganttRows = ref([])
const historyList = ref([])
const pointMetas = ref([])
const analogPointOptions = ref([])
const boolPointOptions = ref([])
const historyTotal = ref(0)
const activeTab = ref('chart')
let chartInstances = []
let ganttInstance = null
let resizeHandler = null

const deviceName = ref(route.query.name || route.params.id || '-')

const searchForm = reactive({
  pointNames: [],
  boolPointNames: [],
  dateRange: [
    dayjs().subtract(1, 'hour'),
    dayjs()
  ]
})

const isBooleanDataType = value => BOOL_DATA_TYPES.has(String(value || '').toUpperCase())

const isCollectAccessType = accessType => (
  COLLECT_ACCESS_TYPES.includes(String(accessType || 'READ_ONLY').toUpperCase())
)

const parseQueryPointNames = () => String(route.query.points || '')
  .split(',')
  .map(item => item.trim())
  .filter(Boolean)

const ganttHeight = computed(() => `${Math.max(168, ganttRows.value.length * 36 + 72)}px`)

const historyColumns = computed(() => [
  { title: '设备编码', dataIndex: 'deviceCode', key: 'deviceCode', width: 140 },
  { title: '设备名称', dataIndex: 'deviceName', key: 'deviceName', width: 160 },
  { title: '时间', key: 'time', width: 180 },
  ...pointMetas.value.map(meta => ({
    title: meta.unit ? `${meta.pointName}（${meta.unit}）` : meta.pointName,
    key: `point-${meta.pointName}`,
    pointName: meta.pointName,
    meta,
    width: 140
  }))
])

const historyTableScrollX = computed(() => {
  return 480 + pointMetas.value.length * 140
})

const getResponseData = response => {
  const data = response?.data ?? response

  return data?.data ?? data
}

const formatTableTime = value => {
  if (!value) return '-'

  const date = dayjs(value)
  return date.isValid() ? date.format('YYYY-MM-DD HH:mm:ss') : String(value)
}

const formatValue = (value, meta) => {
  if (value === undefined || value === null || value === '') return '-'
  if (isBooleanDataType(meta?.dataType)) {
    const on = parseSwitchOn(value)
    if (on === true) return '开'
    if (on === false) return '关'
  }
  if (typeof value === 'object') return JSON.stringify(value)

  return value
}

const selectedPointNames = () => [
  ...new Set([
    ...(searchForm.pointNames || []),
    ...(searchForm.boolPointNames || [])
  ])
]

const disposeGantt = () => {
  ganttInstance?.dispose()
  ganttInstance = null
}

const disposeCharts = () => {
  chartInstances.forEach(item => item?.dispose())
  chartInstances = []
  disposeGantt()
}

const setChartRef = (el, index) => {
  if (el) {
    chartRefs.value[index] = el
  }
}

const normalizeSingleChart = item => ({
  key: item.pointCode,
  title: item.title,
  name: item.pointName,
  unit: item.unit,
  dataType: item.dataType,
  timestamps: item.timestamps || [],
  values: item.values || []
})

const toMillis = value => {
  const date = dayjs(value)
  return date.isValid() ? date.valueOf() : null
}

const buildGanttSegments = (timestamps, values, rangeEnd) => {
  const samples = []

  timestamps.forEach((stamp, index) => {
    const time = toMillis(stamp)
    const on = parseSwitchOn(values[index])
    if (time == null || on == null) return
    samples.push({ time, on })
  })

  if (!samples.length) return []

  const rangeEndMs = toMillis(rangeEnd)
  const segments = []
  let current = { ...samples[0] }

  for (let index = 1; index < samples.length; index += 1) {
    const sample = samples[index]
    if (sample.on === current.on) continue
    if (sample.time > current.time) {
      segments.push({ start: current.time, end: sample.time, on: current.on })
    }
    current = sample
  }

  const lastEnd = Math.max(current.time + 1000, rangeEndMs ?? current.time + 1000)
  segments.push({ start: current.time, end: lastEnd, on: current.on })

  return segments
}

const splitChartData = (charts, startTime, endTime) => {
  const analog = []
  const boolRows = []
  const knownBoolNames = new Set(boolPointOptions.value.map(item => item.value))

  charts.forEach(item => {
    const bool = isBooleanDataType(item.dataType) || knownBoolNames.has(item.name)
    if (bool) {
      boolRows.push({
        name: item.name || item.title,
        segments: buildGanttSegments(item.timestamps, item.values, endTime)
      })
      return
    }
    analog.push(item)
  })

  return {
    analog,
    boolRows,
    startTime,
    endTime
  }
}

const normalizeChartPayload = response => {
  const data = getResponseData(response)
  const charts = Array.isArray(data?.charts) ? data.charts.map(normalizeSingleChart) : []

  return splitChartData(charts, data?.startTime, data?.endTime)
}

const renderGanttItem = (params, api) => {
  const categoryIndex = api.value(0)
  const start = api.coord([api.value(1), categoryIndex])
  const end = api.coord([api.value(2), categoryIndex])
  const height = Math.max(10, (api.size([0, 1])?.[1] || 24) * 0.5)
  const width = Math.max(end[0] - start[0], 2)

  return {
    type: 'rect',
    transition: ['shape'],
    shape: {
      x: start[0],
      y: start[1] - height / 2,
      width,
      height
    },
    style: api.style()
  }
}

const renderGantt = async (rows, startTime, endTime) => {
  ganttRows.value = rows
  disposeGantt()

  if (!rows.length) return

  await nextTick()

  const el = ganttEl.value
  if (!el) return

  const categories = rows.map(item => item.name)
  const startMs = toMillis(startTime)
  const endMs = toMillis(endTime)
  const onData = []
  const offData = []

  rows.forEach((row, index) => {
    row.segments.forEach(segment => {
      const item = {
        value: [index, segment.start, segment.end],
        name: row.name,
        state: segment.on ? '开' : '关',
        start: dayjs(segment.start).format('YYYY-MM-DD HH:mm:ss'),
        end: dayjs(segment.end).format('YYYY-MM-DD HH:mm:ss')
      }
      if (segment.on) onData.push(item)
      else offData.push(item)
    })
  })

  const gantt = echarts.init(el)
  gantt.setOption({
    legend: { show: false },
    tooltip: {
      formatter: params => {
        const data = params?.data
        if (!data) return ''
        return `${data.name}<br/>状态：${data.state}<br/>${data.start} ~ ${data.end}`
      }
    },
    grid: {
      left: 8,
      right: 16,
      top: 8,
      bottom: 40,
      containLabel: true
    },
    xAxis: {
      type: 'time',
      min: startMs ?? undefined,
      max: endMs ?? undefined,
      axisLabel: {
        hideOverlap: true,
        color: '#5a6578',
        formatter: value => dayjs(value).format('HH:mm:ss')
      },
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      splitLine: { show: true, lineStyle: { color: '#f0f0f0' } }
    },
    yAxis: {
      type: 'category',
      data: categories,
      inverse: true,
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: {
        color: '#1a1d23',
        width: 88,
        overflow: 'truncate'
      }
    },
    dataZoom: [{
      type: 'slider',
      filterMode: 'weakFilter',
      height: 14,
      bottom: 4,
      borderColor: 'transparent',
      backgroundColor: '#f5f7fa',
      fillerColor: 'rgba(22, 119, 255, 0.16)',
      handleSize: 12
    }],
    series: [
      {
        name: '开',
        type: 'custom',
        renderItem: renderGanttItem,
        encode: { x: [1, 2], y: 0 },
        itemStyle: { color: GANTT_ON_COLOR },
        data: onData
      },
      {
        name: '关',
        type: 'custom',
        renderItem: renderGanttItem,
        encode: { x: [1, 2], y: 0 },
        itemStyle: { color: GANTT_OFF_COLOR },
        data: offData
      }
    ]
  }, true)
  gantt.resize()
  ganttInstance = gantt
}

const renderCharts = async (charts, startTime, endTime) => {
  chartData.value = charts
  chartRefs.value = []
  chartInstances.forEach(item => item?.dispose())
  chartInstances = []

  await nextTick()

  const startMs = toMillis(startTime)
  const endMs = toMillis(endTime)

  chartInstances = charts
    .map((item, index) => {
      const el = chartRefs.value[index]
      if (!el) return null

      const color = LINE_COLORS[index % LINE_COLORS.length]
      const seriesData = (item.timestamps || [])
        .map((stamp, pointIndex) => {
          const time = toMillis(stamp)
          return time == null ? null : [time, item.values[pointIndex]]
        })
        .filter(Boolean)

      const chartInstance = echarts.init(el)
      chartInstance.setOption({
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'line' },
          formatter: params => {
            if (!params?.length) return ''
            const point = params[0]
            const timeLabel = dayjs(point.value?.[0]).isValid()
              ? dayjs(point.value[0]).format('YYYY-MM-DD HH:mm:ss')
              : point.name
            const unit = item.unit ? ` ${item.unit}` : ''
            return `${timeLabel}<br/>${point.seriesName}: ${point.value?.[1] ?? '-'}${unit}`
          }
        },
        grid: {
          left: 12,
          right: 12,
          top: 24,
          bottom: 8,
          containLabel: true
        },
        xAxis: {
          type: 'time',
          min: startMs ?? undefined,
          max: endMs ?? undefined,
          axisLabel: {
            hideOverlap: true,
            color: '#5a6578',
            formatter: value => dayjs(value).format('HH:mm:ss')
          },
          axisLine: { lineStyle: { color: '#dcdfe6' } },
          splitLine: { show: false }
        },
        yAxis: {
          type: 'value',
          name: item.unit || undefined,
          nameGap: 8,
          nameTextStyle: { color: '#5a6578', fontSize: 12 },
          scale: true,
          axisLabel: { color: '#5a6578' },
          splitLine: { lineStyle: { color: '#f0f0f0' } }
        },
        series: [{
          name: item.name,
          type: 'line',
          smooth: true,
          showSymbol: seriesData.length <= 24,
          symbolSize: 6,
          data: seriesData,
          itemStyle: { color },
          lineStyle: { width: 2, color }
        }]
      }, true)
      chartInstance.resize()
      return chartInstance
    })
    .filter(Boolean)
}

const renderChartView = async payload => {
  disposeCharts()
  await renderCharts(payload.analog, payload.startTime, payload.endTime)
  await renderGantt(payload.boolRows, payload.startTime, payload.endTime)
  await nextTick()
  chartInstances.forEach(item => item?.resize())
  ganttInstance?.resize()
}

const normalizePointMetas = devices => {
  const metaMap = new Map()

  devices.forEach(device => {
    ;(device.pointMetas || []).forEach(meta => {
      if (meta?.pointName && !metaMap.has(meta.pointName)) {
        metaMap.set(meta.pointName, meta)
      }
    })
  })

  return Array.from(metaMap.values())
}

const normalizeDeviceRows = devices => devices.flatMap(device => {
  return (device.timeRows || []).map((row, index) => ({
    key: `${device.deviceId}-${row.time || index}`,
    deviceId: device.deviceId,
    deviceCode: device.deviceCode || '-',
    deviceName: device.deviceName || '-',
    time: row.time,
    values: row.values || {}
  }))
})

const normalizeHistoryData = response => {
  const data = getResponseData(response)
  const devices = Array.isArray(data?.devices) ? data.devices : []

  pointMetas.value = normalizePointMetas(devices)
  historyTotal.value = data?.total ?? normalizeDeviceRows(devices).length

  return normalizeDeviceRows(devices)
}

const resetHistoryData = () => {
  historyList.value = []
  pointMetas.value = []
  historyTotal.value = 0
}

const resetChartView = async () => {
  await renderChartView({ analog: [], boolRows: [], startTime: null, endTime: null })
}

const applyFallbackPointOptions = () => {
  analogPointOptions.value = [...new Set(parseQueryPointNames())].map(item => ({
    label: item,
    value: item
  }))
  boolPointOptions.value = []
  searchForm.pointNames = analogPointOptions.value.map(item => item.value)
  searchForm.boolPointNames = []
}

const loadPointOptions = async () => {
  if (!route.params.id) {
    applyFallbackPointOptions()
    return
  }

  try {
    const response = await getDeviceDetail(String(route.params.id))
    const points = getResponseData(response)
    const collectable = (Array.isArray(points) ? points : [])
      .filter(item => item?.name && isCollectAccessType(item.accessType))
    analogPointOptions.value = collectable
      .filter(item => !isBooleanDataType(item.dataType))
      .map(item => ({ label: item.name, value: item.name }))
    boolPointOptions.value = collectable
      .filter(item => isBooleanDataType(item.dataType))
      .map(item => ({ label: item.name, value: item.name }))

    const requested = parseQueryPointNames()
    const analogValues = analogPointOptions.value.map(item => item.value)
    const boolValues = boolPointOptions.value.map(item => item.value)
    searchForm.pointNames = requested.length
      ? analogValues.filter(name => requested.includes(name))
      : analogValues
    searchForm.boolPointNames = requested.length
      ? boolValues.filter(name => requested.includes(name))
      : boolValues

    if (!analogPointOptions.value.length && !boolPointOptions.value.length) {
      applyFallbackPointOptions()
    }
  } catch (error) {
    applyFallbackPointOptions()
  }
}

const loadChart = async () => {
  if (!searchForm.dateRange || !searchForm.dateRange[0] || !searchForm.dateRange[1]) {
    message.warning('请选择时间段')
    return
  }

  if (!route.params.id) {
    message.warning('缺少设备ID')
    return
  }

  const pointNames = selectedPointNames()
  if (!pointNames.length) {
    message.warning('请选择点位')
    return
  }

  resetHistoryData()
  chartLoading.value = true

  try {
    const response = await getDeviceSnapshotChar({
      deviceId: String(route.params.id),
      pointNames,
      startTime: dayjs(searchForm.dateRange[0]).format('YYYY-MM-DD HH:mm:ss'),
      endTime: dayjs(searchForm.dateRange[1]).format('YYYY-MM-DD HH:mm:ss')
    })
    await renderChartView(normalizeChartPayload(response))
  } catch (error) {
    await resetChartView()
  } finally {
    chartLoading.value = false
  }
}

const loadHistory = async () => {
  if (!searchForm.dateRange || !searchForm.dateRange[0] || !searchForm.dateRange[1]) {
    message.warning('请选择时间段')
    return
  }

  if (!route.params.id) {
    message.warning('缺少设备ID')
    return
  }

  const pointNames = selectedPointNames()
  if (!pointNames.length) {
    message.warning('请选择点位')
    return
  }

  chartLoading.value = true
  await resetChartView()

  try {
    const response = await getDeviceSnapshotHistory({
      items: [
        {
          deviceId: String(route.params.id),
          pointNames
        }
      ],
      startTime: dayjs(searchForm.dateRange[0]).format('YYYY-MM-DD HH:mm:ss'),
      endTime: dayjs(searchForm.dateRange[1]).format('YYYY-MM-DD HH:mm:ss')
    })
    historyList.value = normalizeHistoryData(response)
  } catch (error) {
    resetHistoryData()
  } finally {
    chartLoading.value = false
  }
}

const handleSearch = () => {
  if (activeTab.value === 'table') {
    loadHistory()
  } else {
    loadChart()
  }
}

const handleTabChange = async key => {
  activeTab.value = key
  if (key === 'chart') {
    await loadChart()
    await nextTick()
    chartInstances.forEach(item => item?.resize())
    ganttInstance?.resize()
  } else {
    await loadHistory()
  }
}

const goBack = () => {
  router.back()
}

onMounted(async () => {
  await loadPointOptions()
  loadChart()
  resizeHandler = () => {
    chartInstances.forEach(item => item?.resize())
    ganttInstance?.resize()
  }
  window.addEventListener('resize', resizeHandler)
})

onBeforeUnmount(() => {
  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler)
  }
  disposeCharts()
})
</script>

<style scoped>
.history-chart-page {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  gap: 12px;
}

.history-chart-page .page-header,
.history-chart-page .toolbar-card {
  flex-shrink: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0;
}

.page-header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--omes-color-primary);
  border-left: 4px solid var(--omes-color-primary);
  padding-left: 12px;
  height: 24px;
  line-height: 24px;
}

.toolbar-card :deep(.ant-card-body) {
  padding: 10px 12px;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  row-gap: 8px;
}

.filter-form :deep(.ant-form-item) {
  margin-bottom: 0;
  margin-inline-end: 12px;
}

.filter-form :deep(.ant-form-item-label) {
  padding: 0;
}

.content-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.content-card :deep(.ant-card-head) {
  min-height: 42px;
  padding: 0 12px;
  border-bottom: 1px solid var(--omes-color-border-secondary);
}

.content-card :deep(.ant-card-head-title),
.content-card :deep(.ant-tabs-nav) {
  margin: 0;
}

.content-card :deep(.ant-card-body) {
  flex: 1;
  min-height: 0;
  padding: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.content-card :deep(.ant-card-body::before),
.content-card :deep(.ant-card-body::after) {
  display: none;
}

.content-card :deep(.ant-spin-nested-loading),
.content-card :deep(.ant-spin-container) {
  flex: 1;
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.chart-empty {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chart-workspace {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chart-panel {
  min-width: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid #eef0f3;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}

.chart-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 36px;
  padding: 0 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--omes-color-primary);
  background: #f7f9fc;
  border-bottom: 1px solid #eef0f3;
}

.line-panel {
  flex: 1;
  min-height: 220px;
}

.line-charts {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 8px;
  overflow: auto;
}

.line-charts.single {
  grid-template-columns: 1fr;
}

.line-charts.single .line-chart-item {
  min-height: 100%;
}

.line-chart-item {
  min-width: 0;
  min-height: 240px;
  display: flex;
  flex-direction: column;
  border: 1px solid #f0f2f5;
  border-radius: 6px;
  background: #fff;
}

.line-chart-title {
  flex-shrink: 0;
  padding: 8px 12px 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--omes-color-text);
}

.temperature-chart {
  flex: 1;
  width: 100%;
  min-height: 200px;
}

.gantt-panel {
  flex: 0 0 auto;
}

.chart-workspace.has-gantt:not(.has-lines) .gantt-panel {
  flex: 1;
  min-height: 220px;
}

.chart-workspace.has-gantt:not(.has-lines) .gantt-chart {
  flex: 1;
  height: 100% !important;
  min-height: 220px;
}

.gantt-legend {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  font-weight: 400;
  color: var(--omes-color-text-secondary);
}

.gantt-swatch {
  display: inline-block;
  width: 12px;
  height: 12px;
  margin-right: 6px;
  border-radius: 2px;
  vertical-align: -1px;
}

.gantt-swatch.on {
  background: #52c41a;
}

.gantt-swatch.off {
  background: #d9d9d9;
}

.gantt-chart {
  width: 100%;
  min-height: 168px;
}

.history-table-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.history-table-wrap :deep(.ant-table-wrapper),
.history-table-wrap :deep(.ant-spin-nested-loading),
.history-table-wrap :deep(.ant-spin-container) {
  height: 100%;
  min-height: 0;
}

.history-table-wrap :deep(.ant-spin-container) {
  display: flex;
  flex-direction: column;
}

.history-table-wrap :deep(.ant-table) {
  height: calc(100% - 56px);
  min-height: 0;
}

.history-table-wrap :deep(.ant-table-pagination) {
  margin: 8px 0 0;
}

.history-table-wrap :deep(.ant-table-container),
.history-table-wrap :deep(.ant-table-body) {
  height: 100%;
}
</style>
