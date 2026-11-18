<template>
  <div class="page-container collect-metrics-page">
    <div class="page-header">
      <div class="title-block">
        <h2>性能指标</h2>
        <span class="device-name" :title="deviceName">{{ deviceName }}</span>
        <a-tag
          v-if="querySourceLabel"
          class="source-tag"
          :color="querySource === 'influx' ? 'blue' : 'orange'"
        >
          {{ querySourceLabel }}
        </a-tag>
      </div>
      <a-button @click="goBack">
        <LeftOutlined /> 返回
      </a-button>
    </div>

    <a-card class="toolbar-card" size="small">
      <div class="toolbar-row">
        <div class="toolbar-meta" v-if="metricsMeta.deviceCode || metricsMeta.protocol">
          <span v-if="metricsMeta.deviceCode" class="meta-item">{{ metricsMeta.deviceCode }}</span>
          <span v-if="metricsMeta.protocol" class="meta-item">{{ metricsMeta.protocol }}</span>
          <span v-if="metricsMeta.lastUpdateTime" class="meta-item muted">
            更新于 {{ metricsMeta.lastUpdateTime }}
          </span>
        </div>
        <div class="toolbar-actions">
          <a-select
            v-model:value="searchForm.minutes"
            class="toolbar-select"
            @change="loadChart"
          >
            <a-select-option v-for="m in minuteOptions" :key="m" :value="m">
              近{{ m }}分钟
            </a-select-option>
          </a-select>
          <a-select
            v-model:value="refreshInterval"
            class="toolbar-select"
            @change="restartPolling"
          >
            <a-select-option :value="0">手动刷新</a-select-option>
            <a-select-option :value="10000">每10秒刷新</a-select-option>
            <a-select-option :value="30000">每30秒刷新</a-select-option>
            <a-select-option :value="60000">每60秒刷新</a-select-option>
          </a-select>
          <a-button type="primary" :loading="chartLoading" @click="loadChart">
            <ReloadOutlined /> 刷新
          </a-button>
        </div>
      </div>
    </a-card>

    <a-row class="info-row" :gutter="[12, 12]">
      <a-col class="info-col" :xs="24" :lg="8">
        <a-card size="small" class="info-card">
          <template #title>
            <span class="info-card-title">设备状态</span>
          </template>
          <a-descriptions :column="1" size="small" class="info-desc">
            <a-descriptions-item label="在线">
              <a-tag :color="metricsMeta.online ? 'green' : 'default'">
                {{ metricsMeta.online === true ? '在线' : metricsMeta.online === false ? '离线' : '-' }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="连接状态">
              {{ metricsMeta.connectionStatusLabel || '-' }}
            </a-descriptions-item>
            <a-descriptions-item v-if="metricsMeta.connectionStatusMessage" label="连接说明">
              {{ metricsMeta.connectionStatusMessage }}
            </a-descriptions-item>
            <a-descriptions-item v-if="metricsMeta.offlineReason" label="离线原因">
              {{ metricsMeta.offlineReason }}
            </a-descriptions-item>
            <a-descriptions-item label="最后更新">
              {{ metricsMeta.lastUpdateTime || '-' }}
            </a-descriptions-item>
          </a-descriptions>
        </a-card>
      </a-col>
      <a-col class="info-col" :xs="24" :lg="8">
        <a-card size="small" class="info-card">
          <template #title>
            <span class="info-card-title">轮询调度</span>
          </template>
          <a-descriptions :column="1" size="small" class="info-desc">
            <a-descriptions-item label="连续跳过">
              <span :class="{ 'text-warning': (metricsMeta.pollSkip?.skipStreak || 0) >= 3 }">
                {{ metricsMeta.pollSkip?.skipStreak ?? 0 }} 次
              </span>
            </a-descriptions-item>
            <a-descriptions-item label="轮询整轮耗时">
              {{ metricsMeta.pollSkip?.lastDurationMs ?? 0 }} ms
              <span class="metric-hint">（读设备+解析+写缓存）</span>
            </a-descriptions-item>
          </a-descriptions>
        </a-card>
      </a-col>
      <a-col class="info-col" :xs="24" :lg="8">
        <a-card size="small" class="info-card">
          <template #title>
            <span class="info-card-title">系统资源</span>
          </template>
          <a-descriptions :column="1" size="small" class="info-desc">
            <a-descriptions-item label="实例 ID">{{ metricsMeta.instanceId || '-' }}</a-descriptions-item>
            <a-descriptions-item label="JVM 堆使用率">
              {{ formatPercent(metricsMeta.infrastructure?.jvmHeapUsedRatio) }}%
            </a-descriptions-item>
            <a-descriptions-item label="Hikari 连接池">
              {{ metricsMeta.infrastructure?.hikariActiveConnections ?? 0 }} /
              {{ metricsMeta.infrastructure?.hikariMaxConnections ?? 0 }}
            </a-descriptions-item>
          </a-descriptions>
        </a-card>
      </a-col>
    </a-row>

    <a-card class="chart-card section-card">
      <template #title>
        <span class="section-title">性能趋势</span>
        <span v-if="chartData.length" class="section-meta">{{ chartData.length }} 组指标</span>
      </template>
      <a-empty v-if="!chartData.length" description="暂无监控数据" />
      <div v-else class="chart-grid">
        <div
          v-for="item in chartData"
          :key="item.key || item.title"
          class="metrics-chart-wrap"
        >
          <div class="metrics-chart-head">
            <span class="metrics-chart-title">{{ item.title }}</span>
            <span v-if="item.unit" class="metrics-chart-unit">{{ item.unit }}</span>
          </div>
          <div
            v-if="!item.empty"
            :ref="el => setChartRef(el, item.key || item.title)"
            class="metrics-chart"
          ></div>
          <a-empty v-else class="metrics-chart-empty" description="暂无数据" />
        </div>
      </div>
    </a-card>
  </div>
</template>

<script setup>
import {computed, nextTick, onBeforeUnmount, onMounted, reactive, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {storeToRefs} from 'pinia'
import {message} from 'ant-design-vue'
import { LeftOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import * as echarts from 'echarts'
import {getCollectMetricsSummaryChart} from '@/api/equipment'
import {usePortalStore} from '@/stores/portal'
import {
  formatPercent,
  normalizeMetricsChartData,
  buildChartYAxis
} from '@/utils/collect-metrics-chart'

const portalStore = usePortalStore()
const { metricsMinuteOptions, collectMetricsModeLabel, collectMetricsMode } = storeToRefs(portalStore)
const minuteOptions = computed(() => {
  const opts = metricsMinuteOptions.value
  return opts.length ? opts : [10, 20, 30, 60]
})
const querySource = ref('')
const querySourceLabel = computed(() => {
  if (querySource.value === 'influx') return 'Influx 历史'
  if (querySource.value === 'memory') return '内存缓存'
  return collectMetricsModeLabel.value
})

const router = useRouter()
const route = useRoute()

const chartLoading = ref(false)
const chartData = ref([])
const chartRefs = ref({})
const refreshInterval = ref(0)
let chartInstances = []
let resizeHandler = null
let pollingTimer = null
let pollingActive = false

const deviceName = ref(route.query.name || route.params.id || '-')

const metricsMeta = reactive({
  deviceCode: '',
  protocol: '',
  online: null,
  connectionStatusLabel: '',
  connectionStatusMessage: '',
  offlineReason: '',
  lastUpdateTime: '',
  pollSkip: null,
  infrastructure: null,
  instanceId: ''
})

const searchForm = reactive({
  minutes: 10
})

const getResponseData = response => {
  const data = response?.data ?? response
  return data?.data ?? data
}

const disposeChart = () => {
  chartInstances.forEach(item => item?.dispose())
  chartInstances = []
}

const setChartRef = (el, key) => {
  if (!key) return
  if (el) {
    chartRefs.value[key] = el
  } else {
    delete chartRefs.value[key]
  }
}

const applyMetricsMeta = data => {
  metricsMeta.deviceCode = data?.deviceCode || ''
  metricsMeta.protocol = data?.protocol || ''
  metricsMeta.online = data?.online
  metricsMeta.connectionStatusLabel = data?.connectionStatusLabel || ''
  metricsMeta.connectionStatusMessage = data?.connectionStatusMessage || ''
  metricsMeta.offlineReason = data?.offlineReason || ''
  metricsMeta.lastUpdateTime = data?.lastUpdateTime || ''
  metricsMeta.pollSkip = data?.pollSkip || null
  metricsMeta.infrastructure = data?.infrastructure || null
  metricsMeta.instanceId = data?.instanceId || ''
  querySource.value = data?.querySource || collectMetricsMode.value || ''
  if (data?.deviceName) {
    deviceName.value = data.deviceName
  }
}

const clampMinutesToMode = () => {
  const opts = minuteOptions.value
  if (!opts.includes(searchForm.minutes)) {
    searchForm.minutes = opts[opts.length - 1] || 10
  }
}

const LINE_COLORS = ['#0078d4', '#5c2e91', '#038387', '#bf0077', '#eaa300']

const seriesUnit = (chart, seriesItem) => seriesItem.unit || chart.unit || ''

const styleYAxis = yAxis => {
  const axes = Array.isArray(yAxis) ? yAxis : [yAxis]
  return axes.map((axis, index) => ({
    ...axis,
    nameTextStyle: { color: '#616161', fontSize: 11, ...(axis?.nameTextStyle || {}) },
    axisLabel: { color: '#616161', fontSize: 11, ...(axis?.axisLabel || {}) },
    axisLine: { show: false },
    axisTick: { show: false },
    splitLine: index === 0
      ? { lineStyle: { color: '#f0f0f0', type: 'dashed' } }
      : { show: false }
  }))
}

const renderChart = async charts => {
  chartData.value = charts
  chartRefs.value = {}
  disposeChart()

  await nextTick()
  await nextTick()

  chartInstances = charts
    .filter(data => !data.empty)
    .map(data => {
      const el = chartRefs.value[data.key || data.title]
      if (!el) return null

      const chart = echarts.init(el)
      const yAxis = styleYAxis(buildChartYAxis(data))

      chart.setOption({
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'line' },
          formatter: params => {
            if (!params?.length) return ''
            let result = `${params[0].name}<br/>`
            params.forEach(item => {
              const series = data.series[item.seriesIndex]
              const unit = seriesUnit(data, series)
              result += `${item.marker}${item.seriesName}: ${item.value ?? '-'}${unit}<br/>`
            })
            return result
          }
        },
        legend: {
          data: data.series.map(item => item.name),
          bottom: 4,
          itemWidth: 12,
          itemHeight: 8,
          textStyle: { color: '#616161', fontSize: 11 }
        },
        grid: {
          left: 12,
          right: data.dualAxis ? 28 : 16,
          top: 20,
          bottom: 36,
          containLabel: true
        },
        xAxis: {
          type: 'category',
          boundaryGap: false,
          data: data.timeLabels,
          axisLabel: {
            color: '#616161',
            fontSize: 11,
            rotate: 45,
            interval: Math.max(0, Math.floor(data.timeLabels.length / 12))
          },
          axisLine: { lineStyle: { color: '#e0e0e0' } },
          axisTick: { show: false },
          splitLine: { show: false }
        },
        yAxis,
        series: data.series.map((item, index) => {
          const color = LINE_COLORS[index % LINE_COLORS.length]
          return {
            name: item.name,
            type: 'line',
            smooth: 0.25,
            showSymbol: false,
            yAxisIndex: item.yAxisIndex || 0,
            data: item.values,
            itemStyle: { color },
            lineStyle: { width: 2, color },
            areaStyle: index === 0
              ? {
                  color: {
                    type: 'linear',
                    x: 0,
                    y: 0,
                    x2: 0,
                    y2: 1,
                    colorStops: [
                      { offset: 0, color: `${color}26` },
                      { offset: 1, color: `${color}00` }
                    ]
                  }
                }
              : undefined
          }
        })
      }, true)
      chart.resize()
      return chart
    })
    .filter(Boolean)
}

const loadChart = async () => {
  if (chartLoading.value) return
  if (!searchForm.minutes) {
    message.warning('请选择时间范围')
    return
  }
  if (!route.params.id) {
    message.warning('缺少设备ID')
    return
  }

  chartLoading.value = true
  try {
    const response = await getCollectMetricsSummaryChart({
      deviceId: String(route.params.id),
      minutes: searchForm.minutes
    })
    const data = getResponseData(response)
    applyMetricsMeta(data)
    await renderChart(normalizeMetricsChartData(data))
  } catch {
    applyMetricsMeta({})
    await renderChart([])
    message.error('加载性能指标失败')
  } finally {
    chartLoading.value = false
  }
}

const restartPolling = () => {
  pollingActive = false
  if (pollingTimer) {
    clearTimeout(pollingTimer)
    pollingTimer = null
  }
  if (refreshInterval.value > 0) {
    pollingActive = true
    const poll = async () => {
      pollingTimer = null
      if (!pollingActive) return
      await loadChart()
      if (!pollingActive || refreshInterval.value <= 0) return
      pollingTimer = setTimeout(poll, refreshInterval.value)
    }
    pollingTimer = setTimeout(poll, refreshInterval.value)
  }
}

const goBack = () => {
  router.back()
}

onMounted(async () => {
  await portalStore.ensureLoaded()
  clampMinutesToMode()
  await loadChart()
  restartPolling()
  resizeHandler = () => {
    chartInstances.forEach(item => item?.resize())
  }
  window.addEventListener('resize', resizeHandler)
})

onBeforeUnmount(() => {
  pollingActive = false
  if (pollingTimer) {
    clearTimeout(pollingTimer)
    pollingTimer = null
  }
  if (resizeHandler) window.removeEventListener('resize', resizeHandler)
  disposeChart()
})
</script>

<style scoped>
/*
 * 滚动由全局 .page-container（flex 子项 + overflow-y:auto）承担。
 * 禁止本页再用 flex 列：子项默认 flex-shrink:1，会被压扁后被
 * 全局 .ant-card{overflow:hidden} 裁掉底部图表（延迟 / 轮询跳过）。
 */
.collect-metrics-page {
  display: block;
  padding-bottom: 24px;
}

.title-block {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10px;
}

.collect-metrics-page .page-header h2 {
  flex-shrink: 0;
}

.device-name {
  min-width: 0;
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 500;
  color: var(--omes-color-text-secondary);
}

.source-tag {
  flex-shrink: 0;
  margin-inline-end: 0;
}

.toolbar-card {
  margin-bottom: 12px;
  border-radius: var(--omes-border-radius-large, 8px);
  background: var(--omes-gradient-toolbar, var(--omes-color-bg-elevated));
}

.toolbar-card :deep(.ant-card-body) {
  padding: 10px 14px;
}

.toolbar-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px 16px;
}

.toolbar-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 14px;
  min-width: 0;
}

.meta-item {
  font-size: 13px;
  color: var(--omes-color-text-secondary);
}

.meta-item.muted {
  color: var(--omes-color-text-tertiary);
}

.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.toolbar-select {
  width: 140px;
}

.section-card {
  margin-bottom: 16px;
}

.info-row {
  margin-bottom: 12px;
  align-items: stretch;
}

.info-col {
  display: flex;
}

.info-card {
  width: 100%;
  height: 100%;
  border-radius: var(--omes-border-radius-large, 8px);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
}

.info-card :deep(.ant-card-head) {
  min-height: 40px;
  padding: 0 14px;
  background: var(--omes-color-bg-spotlight);
  border-bottom: 1px solid var(--omes-color-border);
}

.info-card :deep(.ant-card-head-title) {
  padding: 10px 0;
}

.info-card :deep(.ant-card-body) {
  padding: 12px 14px;
}

.info-card-title {
  font-size: 13px;
  font-weight: var(--omes-font-weight-semibold);
  color: var(--omes-color-text-heading);
}

.info-desc :deep(.ant-descriptions-item-label) {
  width: 96px;
  color: var(--omes-color-text-tertiary);
}

.info-desc :deep(.ant-descriptions-item-content) {
  color: var(--omes-color-text);
}

.chart-card {
  overflow: visible;
  border-radius: var(--omes-border-radius-large, 8px);
}

.chart-card :deep(.ant-card-head) {
  min-height: 44px;
  padding: 0 16px;
  background: var(--omes-color-bg-spotlight);
  border-bottom: 1px solid var(--omes-color-border-secondary);
}

.chart-card :deep(.ant-card-head-title) {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 0;
}

.section-title {
  font-size: 13px;
  font-weight: var(--omes-font-weight-semibold);
  color: var(--omes-color-text-heading);
}

.section-meta {
  font-size: 12px;
  font-weight: 400;
  color: var(--omes-color-text-tertiary);
}

.chart-card :deep(.ant-card-body) {
  padding: 14px 16px;
  overflow: visible;
  background: var(--omes-color-bg-elevated);
}

.chart-card :deep(.ant-card-body > .ant-empty) {
  padding: 48px 0;
  background: var(--omes-color-bg-container);
  border: 1px dashed var(--omes-color-border-secondary);
  border-radius: 8px;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
}

.metrics-chart-wrap {
  min-width: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--omes-color-border-secondary);
  border-radius: 8px;
  background: var(--omes-color-bg-container);
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
}

.metrics-chart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 36px;
  padding: 0 14px;
  border-bottom: 1px solid var(--omes-color-border);
  background: var(--omes-color-bg-spotlight);
}

.metrics-chart-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--omes-color-text);
}

.metrics-chart-unit {
  font-size: 12px;
  color: var(--omes-color-text-tertiary);
}

.metrics-chart {
  width: 100%;
  height: 300px;
  min-width: 0;
}

.metrics-chart-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  margin: 0;
}

.text-warning {
  color: var(--omes-color-warning);
  font-weight: var(--omes-font-weight-semibold);
}

.metric-hint {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--omes-color-text-tertiary);
  font-weight: normal;
}

@media screen and (max-width: 992px) {
  .toolbar-actions {
    width: 100%;
    margin-left: 0;
  }

  .toolbar-select {
    flex: 1;
    min-width: 120px;
  }

  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
