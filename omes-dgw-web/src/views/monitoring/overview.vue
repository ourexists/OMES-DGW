<template>
  <div class="page-container monitoring-overview-page">
    <div class="page-header">
      <h2>系统监控总览</h2>
      <a-space>
        <a-select v-model:value="searchForm.minutes" style="width: 140px" @change="loadOverview">
          <a-select-option :value="10">近10分钟</a-select-option>
          <a-select-option :value="30">近30分钟</a-select-option>
          <a-select-option :value="60">近60分钟</a-select-option>
          <a-select-option :value="180">近3小时</a-select-option>
        </a-select>
        <a-select v-model:value="refreshInterval" style="width: 140px" @change="restartPolling">
          <a-select-option :value="0">手动刷新</a-select-option>
          <a-select-option :value="10000">每10秒</a-select-option>
          <a-select-option :value="30000">每30秒</a-select-option>
          <a-select-option :value="60000">每60秒</a-select-option>
        </a-select>
        <a-button type="primary" :loading="loading" @click="loadOverview">
          <ReloadOutlined /> 刷新
        </a-button>
      </a-space>
    </div>

    <a-alert
      v-for="alert in overview.alerts"
      :key="`${alert.code}-${alert.target}`"
      :type="alert.severity === 'warning' ? 'warning' : 'info'"
      show-icon
      :message="alert.message"
      style="margin-bottom: 12px"
    />

    <div class="stats-flow">
      <a-card size="small" class="stat-flow-item">
        <a-statistic title="设备总数" :value="overview.devices.total" suffix="台" />
      </a-card>
      <a-card size="small" class="stat-flow-item">
        <a-statistic
          title="在线"
          :value="overview.devices.online"
          suffix="台"
          :value-style="{ color: '#52c41a' }"
        />
      </a-card>
      <a-card size="small" class="stat-flow-item">
        <a-statistic
          title="离线"
          :value="overview.devices.offline"
          suffix="台"
          :value-style="{ color: '#8c8c8c' }"
        />
      </a-card>
      <a-card size="small" class="stat-flow-item">
        <a-statistic
          title="连接建立"
          :value="overview.devices.connectionConnected"
          suffix="台"
          :value-style="{ color: '#1677ff' }"
        />
      </a-card>
      <a-card size="small" class="stat-flow-item">
        <a-statistic
          title="连接中断/熔断"
          :value="overview.devices.connectionDisconnected + overview.devices.connectionCircuitAbandoned"
          suffix="台"
          :value-style="{ color: '#faad14' }"
        />
      </a-card>
      <a-card size="small" class="stat-flow-item">
        <a-statistic
          title="JVM 堆使用率"
          :value="formatPercent(overview.infrastructure.jvmHeapUsedRatio)"
          suffix="%"
        />
      </a-card>
    </div>

    <a-row class="pipeline-row" :gutter="[16, 16]" style="margin-bottom: 16px">
      <a-col class="pipeline-col" :xs="24" :lg="12">
        <a-card title="异步队列" size="small" class="pipeline-card">
          <div v-for="queue in overview.pipelines.queues" :key="queue.name" class="queue-item">
            <div class="queue-header">
              <span>{{ queueLabel(queue.name) }}</span>
              <span>{{ queue.depth }} / {{ queue.maxSize }}</span>
            </div>
            <a-progress
              :percent="Math.min(100, Math.round(queue.usageRatio * 100))"
              :status="queue.usageRatio >= 0.8 ? 'exception' : 'normal'"
              size="small"
            />
            <div v-if="queue.trimmedTotal > 0" class="queue-trimmed">
              累计丢弃 {{ queue.trimmedTotal }} 条
            </div>
          </div>
        </a-card>
      </a-col>
      <a-col class="pipeline-col" :xs="24" :lg="12">
        <a-card title="持久化与缓存" size="small" class="pipeline-card">
          <a-descriptions :column="1" size="small" bordered>
            <a-descriptions-item label="实例 ID">{{ overview.instanceId || '-' }}</a-descriptions-item>
            <a-descriptions-item label="Influx 启用">
              <a-tag :color="overview.pipelines.influxEnabled ? 'green' : 'default'">
                {{ overview.pipelines.influxEnabled ? '是' : '否' }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="指标持久化">
              <a-tag :color="overview.pipelines.influxMetricsPersistEnabled ? 'green' : 'default'">
                {{ overview.pipelines.influxMetricsPersistEnabled ? '已开启' : '未开启' }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="Influx 写入 Leader">
              <a-tag :color="overview.pipelines.influxMetricsWriteLeader ? 'blue' : 'default'">
                {{ overview.pipelines.influxMetricsWriteLeader ? '本实例' : '非本实例' }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="实时缓存写入">
              成功 {{ overview.pipelines.realtimeCacheWrites.success }} /
              降级 {{ overview.pipelines.realtimeCacheWrites.degraded }} /
              错误 {{ overview.pipelines.realtimeCacheWrites.error }}
            </a-descriptions-item>
            <a-descriptions-item label="连接池">
              Hikari {{ overview.infrastructure.hikariActiveConnections }} /
              {{ overview.infrastructure.hikariMaxConnections }}
            </a-descriptions-item>
          </a-descriptions>
        </a-card>
      </a-col>
    </a-row>

    <a-card title="全站采集性能趋势" class="chart-card" style="margin-bottom: 16px">
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

    <a-card title="各设备最新指标" size="small" style="margin-bottom: 16px">
      <a-table
        :columns="deviceColumns"
        :data-source="overview.collectPerformance.devices"
        :pagination="{ pageSize: 10, showSizeChanger: true }"
        row-key="deviceId"
        size="small"
        :scroll="{ x: 2000 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'pollFailRate'">
            <span :class="{ 'text-warning': record.pollFailRate >= 0.1 }">
              {{ formatPercent(record.pollFailRate) }}%
            </span>
          </template>
          <template v-else-if="column.key === 'readFailRate' || column.key === 'writeFailRate'">
            {{ formatPercent(record[column.key]) }}%
          </template>
          <template v-else-if="column.key === 'pollSkipStreak'">
            <span :class="{ 'text-warning': record.pollSkipStreak >= 3 }">
              {{ record.pollSkipStreak ?? 0 }}
            </span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" size="small" @click="viewDeviceMetrics(record)">详情</a-button>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-card title="轮询调度（防重入跳过）" size="small">
      <a-empty v-if="!overview.scheduling.topSkipStreak.length" description="暂无跳过记录" />
      <a-table
        v-else
        :columns="skipColumns"
        :data-source="overview.scheduling.topSkipStreak"
        :pagination="false"
        row-key="deviceId"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'skipStreak'">
            <a-tag :color="record.skipStreak >= 3 ? 'orange' : 'default'">
              {{ record.skipStreak }}
            </a-tag>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick, onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import * as echarts from 'echarts'
import { getMonitoringOverview } from '@/api/monitoring'
import {
  normalizeMetricsChartData,
  formatPercent,
  queueLabel,
  buildChartYAxis
} from '@/utils/collect-metrics-chart'

const router = useRouter()

const loading = ref(false)
const refreshInterval = ref(30000)
let pollingTimer = null
let pollingActive = false

const searchForm = reactive({
  minutes: 30
})

const overview = reactive({
  serverTime: '',
  instanceId: '',
  status: '',
  message: '',
  devices: {
    total: 0,
    online: 0,
    offline: 0,
    connectionConnected: 0,
    connectionDisconnected: 0,
    connectionCircuitAbandoned: 0,
    connectionHalfOpen: 0,
    connectionDisabled: 0,
    connectionUnsupported: 0
  },
  collectPerformance: {
    summary: { timestamps: [] },
    devices: []
  },
  pipelines: {
    queues: [],
    influxEnabled: false,
    influxMetricsPersistEnabled: false,
    influxMetricsWriteLeaderEnabled: false,
    influxMetricsWriteLeader: false,
    realtimeCacheWrites: {
      success: 0,
      degraded: 0,
      serializationError: 0,
      error: 0
    }
  },
  scheduling: {
    devicesWithSkipStreakGte3: 0,
    topSkipStreak: []
  },
  infrastructure: {
    jvmHeapUsedRatio: 0,
    hikariActiveConnections: 0,
    hikariMaxConnections: 0,
    hikariActiveRatio: 0
  },
  alerts: []
})

const chartData = ref([])
const chartRefs = ref({})
let chartInstances = []
let resizeHandler = null

const deviceColumns = [
  { title: '设备名称', dataIndex: 'deviceName', key: 'deviceName', width: 140 },
  { title: '设备编码', dataIndex: 'deviceCode', key: 'deviceCode', width: 110 },
  { title: '协议', dataIndex: 'protocol', key: 'protocol', width: 90 },
  { title: '采样时间', dataIndex: 'sampleTime', key: 'sampleTime', width: 95 },
  { title: '设备读 QPS', dataIndex: 'readQps', key: 'readQps', width: 95 },
  { title: '轮询 QPS', dataIndex: 'pollQps', key: 'pollQps', width: 95 },
  { title: '设备写 QPS', dataIndex: 'writeQps', key: 'writeQps', width: 95 },
  { title: '设备读失败率', dataIndex: 'readFailRate', key: 'readFailRate', width: 105 },
  { title: '轮询失败率', dataIndex: 'pollFailRate', key: 'pollFailRate', width: 100 },
  { title: '设备写失败率', dataIndex: 'writeFailRate', key: 'writeFailRate', width: 105 },
  { title: '设备读延迟', dataIndex: 'readLatencyMs', key: 'readLatencyMs', width: 95 },
  { title: '轮询延迟', dataIndex: 'pollLatencyMs', key: 'pollLatencyMs', width: 95 },
  { title: '设备写延迟', dataIndex: 'writeLatencyMs', key: 'writeLatencyMs', width: 95 },
  { title: '跳过QPS', dataIndex: 'pollSkipQps', key: 'pollSkipQps', width: 85 },
  { title: '连续跳过', dataIndex: 'pollSkipStreak', key: 'pollSkipStreak', width: 85 },
  { title: '轮询整轮耗时', dataIndex: 'pollLastDurationMs', key: 'pollLastDurationMs', width: 110 },
  { title: '离线(秒)', dataIndex: 'offlineDurationSec', key: 'offlineDurationSec', width: 85 },
  { title: '操作', key: 'action', fixed: 'right', width: 72 }
]

const skipColumns = [
  { title: '设备名称', dataIndex: 'deviceName', key: 'deviceName' },
  { title: '协议', dataIndex: 'protocol', key: 'protocol', width: 100 },
  { title: '连续跳过', dataIndex: 'skipStreak', key: 'skipStreak', width: 100 },
  { title: '轮询整轮耗时(ms)', dataIndex: 'lastDurationMs', key: 'lastDurationMs', width: 150 }
]

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
          textStyle: { fontSize: 14, fontWeight: 600 }
        },
        tooltip: {
          trigger: 'axis',
          formatter: params => {
            if (!params?.length) return ''
            let result = `${params[0].name}<br/>`
            params.forEach(item => {
              const series = data.series[item.seriesIndex]
              result += `${item.seriesName}: ${item.value ?? '-'}${seriesUnit(data, series)}<br/>`
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
          lineStyle: { width: 2 }
        }))
      }, true)
      chart.resize()
      return chart
    })
    .filter(Boolean)
}

const applyOverview = data => {
  Object.assign(overview, {
    ...data,
    devices: { ...overview.devices, ...(data.devices || {}) },
    collectPerformance: {
      summary: data.collectPerformance?.summary || { timestamps: [] },
      devices: data.collectPerformance?.devices || []
    },
    pipelines: {
      ...overview.pipelines,
      ...(data.pipelines || {}),
      realtimeCacheWrites: {
        ...overview.pipelines.realtimeCacheWrites,
        ...(data.pipelines?.realtimeCacheWrites || {})
      },
      queues: data.pipelines?.queues || []
    },
    scheduling: {
      ...overview.scheduling,
      ...(data.scheduling || {}),
      topSkipStreak: data.scheduling?.topSkipStreak || []
    },
    infrastructure: {
      ...overview.infrastructure,
      ...(data.infrastructure || {})
    },
    alerts: data.alerts || []
  })
}

const loadOverview = async () => {
  if (loading.value) return

  loading.value = true
  try {
    const response = await getMonitoringOverview({ minutes: searchForm.minutes })
    const data = getResponseData(response)
    applyOverview(data)
    await renderChart(normalizeMetricsChartData(data.collectPerformance?.summary))
  } catch (error) {
    message.error('加载监控总览失败')
  } finally {
    loading.value = false
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
      await loadOverview()
      if (!pollingActive || refreshInterval.value <= 0) return
      pollingTimer = setTimeout(poll, refreshInterval.value)
    }
    pollingTimer = setTimeout(poll, refreshInterval.value)
  }
}

const viewDeviceMetrics = record => {
  router.push({
    path: `/equipment/collect-metrics/${record.deviceId}`,
    query: { name: record.deviceName }
  })
}

onMounted(() => {
  loadOverview()
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
  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler)
  }
  disposeChart()
})
</script>

<style scoped>
.pipeline-row {
  align-items: stretch;
}

.pipeline-col {
  display: flex;
}

.pipeline-card {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.pipeline-card :deep(.ant-card-body) {
  flex: 1;
}

.queue-item + .queue-item {
  margin-top: 16px;
}

.queue-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
  font-size: 13px;
}

.queue-trimmed {
  margin-top: 4px;
  font-size: 12px;
  color: #faad14;
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
  border: 1px solid var(--omes-color-border-secondary);
  border-radius: 8px;
  background: var(--omes-color-bg-elevated);
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

@media screen and (max-width: 992px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
}

.text-warning {
  color: #faad14;
  font-weight: 600;
}
</style>
