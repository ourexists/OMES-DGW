import dayjs from 'dayjs'

export const formatChartTime = value => {
  if (!value) return ''
  const date = dayjs(value)
  return date.isValid() ? date.format('HH:mm:ss') : String(value)
}

export const formatPercent = value => {
  const num = Number(value)
  if (Number.isNaN(num)) return '0.0'
  return (num * 100).toFixed(1)
}

const toFailRatePercent = arr => (arr || []).map(v => Number((Number(v) * 100).toFixed(1)))

/** 将指标序列对齐到与 timestamps 相同长度（缺项补 0） */
const alignSeriesValues = (values, axisLen) => {
  const list = Array.isArray(values) ? values : []
  if (axisLen <= 0) return []
  if (list.length === axisLen) return list
  return Array.from({ length: axisLen }, (_, i) => list[i] ?? 0)
}

/** 协议采集指标展示名（读/写=对设备的单次协议请求，轮询=定时整轮采集） */
export const METRIC_LABELS = {
  pollQps: '轮询QPS（整轮）',
  deviceReadQps: '设备读QPS',
  deviceWriteQps: '设备写QPS',
  pollFailRate: '轮询失败率',
  deviceReadFailRate: '设备读失败率',
  deviceWriteFailRate: '设备写失败率',
  pollLatency: '轮询延迟（整轮）',
  deviceReadLatency: '设备读延迟',
  deviceWriteLatency: '设备写延迟',
  pollLastDuration: '轮询整轮耗时'
}

/**
 * 将采集指标 API 数据转为 ECharts 多图配置。
 * @param {object} data CollectMetricsChartVo 或 overview summary
 */
export function normalizeMetricsChartData(data) {
  if (!Array.isArray(data?.timestamps)) {
    return []
  }

  const timeLabels = data.timestamps.map(formatChartTime)
  const axisLen = timeLabels.length
  const align = values => alignSeriesValues(values, axisLen)

  const charts = [
    {
      key: 'qps',
      title: 'QPS',
      timeLabels,
      unit: '次/秒',
      series: [
        { name: METRIC_LABELS.deviceReadQps, values: align(data.readQps) },
        { name: METRIC_LABELS.pollQps, values: align(data.pollQps) },
        { name: METRIC_LABELS.deviceWriteQps, values: align(data.writeQps) }
      ]
    },
    {
      key: 'failRate',
      title: '失败率',
      timeLabels,
      unit: '%',
      series: [
        { name: METRIC_LABELS.deviceReadFailRate, values: align(toFailRatePercent(data.readFailRate)) },
        { name: METRIC_LABELS.pollFailRate, values: align(toFailRatePercent(data.pollFailRate)) },
        { name: METRIC_LABELS.deviceWriteFailRate, values: align(toFailRatePercent(data.writeFailRate)) }
      ]
    },
    {
      key: 'latency',
      title: '延迟',
      timeLabels,
      unit: 'ms',
      series: [
        { name: METRIC_LABELS.deviceReadLatency, values: align(data.readLatencyMs) },
        { name: METRIC_LABELS.pollLatency, values: align(data.pollLatencyMs) },
        { name: METRIC_LABELS.deviceWriteLatency, values: align(data.writeLatencyMs) }
      ]
    },
    {
      key: 'pollSkip',
      title: '轮询跳过',
      timeLabels,
      unit: '',
      dualAxis: true,
      series: [
        { name: '跳过QPS', values: align(data.pollSkipQps), yAxisIndex: 0, unit: '次/秒' },
        { name: '连续跳过', values: align(data.pollSkipStreak), yAxisIndex: 1, unit: '次' }
      ]
    },
    {
      key: 'pollLastDuration',
      title: METRIC_LABELS.pollLastDuration,
      timeLabels,
      unit: 'ms',
      series: [{ name: METRIC_LABELS.pollLastDuration, values: align(data.pollLastDurationMs) }]
    },
    {
      key: 'offlineDuration',
      title: '离线时长',
      timeLabels,
      unit: '秒',
      series: [{ name: '离线持续', values: align(data.offlineDurationSec) }]
    }
  ]

  // 有横轴即渲染；全 0 也是有效数据（轮询未跳过、设备在线）
  return charts.map(item => ({
    ...item,
    empty: false
  }))
}

/** 全 0 时抬高 Y 轴上限，避免折线贴在坐标轴上看不见 */
export function buildChartYAxis(chart) {
  const seriesMax = values => Math.max(0, ...(values || []).map(v => Number(v) || 0))

  if (chart.dualAxis) {
    const leftMax = seriesMax(chart.series.find(s => (s.yAxisIndex || 0) === 0)?.values)
    const rightMax = seriesMax(chart.series.find(s => s.yAxisIndex === 1)?.values)
    return [
      { type: 'value', name: '次/秒', min: 0, max: leftMax === 0 ? 1 : undefined, scale: leftMax > 0 },
      { type: 'value', name: '次', position: 'right', min: 0, max: rightMax === 0 ? 1 : undefined, scale: rightMax > 0 }
    ]
  }

  const max = seriesMax(chart.series.flatMap(s => s.values))
  const zeroMax = chart.unit === '%' ? 100 : 1
  return {
    type: 'value',
    name: chart.unit,
    min: 0,
    max: max === 0 ? zeroMax : undefined,
    scale: max > 0
  }
}

export const queueLabel = name => {
  const map = {
    history: '历史快照队列',
    metrics: '指标写入队列',
    online_event: '在线事件队列'
  }
  return map[name] || name
}
