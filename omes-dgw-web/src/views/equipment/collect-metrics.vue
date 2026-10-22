<template>
  <div class="page-container collect-metrics-page">
    <div class="page-header">
      <div>
        <h2>性能指标 - {{ deviceName }}</h2>
        <div v-if="metricsMeta.deviceCode" class="page-subtitle">
          {{ metricsMeta.deviceCode }}
          <span v-if="metricsMeta.protocol"> · {{ metricsMeta.protocol }}</span>
          <a-tag :color="querySource === 'influx' ? 'blue' : 'orange'" style="margin-left: 8px">
            {{ querySourceLabel }}
          </a-tag>
        </div>
      </div>
      <a-space>
        <a-select v-model:value="searchForm.minutes" style="width: 140px" @change="loadChart">
          <a-select-option v-for="m in minuteOptions" :key="m" :value="m">近{{ m }}分钟</a-select-option>
        </a-select>
        <a-select v-model:value="refreshInterval" style="width: 140px" @change="restartPolling">
          <a-select-option :value="0">手动刷新</a-select-option>
          <a-select-option :value="10000">每10秒刷新</a-select-option>
          <a-select-option :value="30000">每30秒刷新</a-select-option>
          <a-select-option :value="60000">每60秒刷新</a-select-option>
        </a-select>
        <a-button type="primary" :loading="chartLoading" @click="loadChart">
          <ReloadOutlined /> 刷新
        </a-button>
        <a-button @click="goBack">
          <LeftOutlined /> 返回
        </a-button>
      </a-space>
    </div>

    <a-row class="info-row" :gutter="[16, 16]">
      <a-col class="info-col" :xs="24" :lg="8">
        <a-card title="设备状态" size="small" class="info-card">
          <a-descriptions :column="1" size="small" bordered>
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
        <a-card title="轮询调度" size="small" class="info-card">
          <a-descriptions :column="1" size="small" bordered>
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
        <a-card title="系统资源" size="small" class="info-card">
          <a-descriptions :column="1" size="small" bordered>
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

    <a-card title="性能指标" class="chart-card section-card">
      <a-empty v-if="!chartData.length" description="暂无监控数据" />
      <div v-else class="chart-grid">
        <div
          v-for="item in chartData"
          :key="item.key || item.title"
          class="metrics-chart-wrap"
        >
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

const seriesUnit = (chart, seriesItem) => seriesItem.unit || chart.unit || ''

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
        const yAxis = buildChartYAxis(data)

        chart.setOption({
          title: {
            text: data.title,
            left: 'center',
            textStyle: {fontSize: 14, fontWeight: 600}
          },
          tooltip: {
            trigger: 'axis',
            formatter: params => {
              if (!params?.length) return ''
              let result = `${params[0].name}<br/>`
              params.forEach(item => {
                const series = data.series[item.seriesIndex]
                const unit = seriesUnit(data, series)
                result += `${item.seriesName}: ${item.value ?? '-'}${unit}<br/>`
              })
              return result
            }
          },
          legend: {
            data: data.series.map(item => item.name),
            bottom: 0
          },
          grid: {
            left: '3%',
            right: data.dualAxis ? '8%' : '5%',
            top: 48,
            bottom: 40,
            containLabel: true
          },
          xAxis: {
            type: 'category',
            boundaryGap: false,
            data: data.timeLabels,
            name: '时间',
            axisLabel: {
              rotate: 45,
              interval: Math.max(0, Math.floor(data.timeLabels.length / 12))
            }
          },
          yAxis,
          series: data.series.map((item, index) => ({
            name: item.name,
            type: 'line',
            smooth: true,
            showSymbol: false,
            yAxisIndex: item.yAxisIndex || 0,
            data: item.values,
            itemStyle: {
              color: ['#1677ff', '#52c41a', '#faad14', '#ff4d4f', '#722ed1'][index % 5]
            },
            lineStyle: {width: 2}
          }))
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
  /* 覆盖可能残留的 flex；块级布局让卡片按内容增高，由页面滚动 */
  display: block;
  padding-bottom: 24px;
}

.collect-metrics-page .page-header {
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 12px;
}

.collect-metrics-page .page-header > .ant-space {
  flex-shrink: 0;
}

.page-subtitle {
  margin-top: 6px;
  padding-left: 16px;
  color: #8c8c8c;
  font-size: 13px;
  line-height: 1.5;
}

.section-card {
  margin-bottom: 16px;
}

.info-row {
  margin-bottom: 16px;
  align-items: stretch;
}

.info-col {
  display: flex;
}

.info-card {
  width: 100%;
  height: 100%;
}

.chart-card {
  /* 避免全局 .ant-card{overflow:hidden} 在异常高度约束下裁切图表 */
  overflow: visible;
}

.chart-card :deep(.ant-card-body) {
  padding: 16px;
  overflow: visible;
}

.chart-card :deep(.ant-card-body > .ant-empty) {
  padding: 48px 0;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  width: 100%;
}

.metrics-chart-wrap {
  min-width: 0;
  min-height: 320px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fafafa;
  overflow: hidden;
}

.metrics-chart {
  width: 100%;
  height: 320px;
  min-width: 0;
}

.metrics-chart-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  margin: 0;
}

.text-warning {
  color: #faad14;
  font-weight: 600;
}

.metric-hint {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #8c8c8c;
  font-weight: normal;
}

@media screen and (max-width: 992px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
