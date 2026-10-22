import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getPortalInfo } from '@/api/system'

/**
 * 门户能力：点位历史（Influx）vs 性能指标（Influx / 内存环形缓冲）。
 * 同一套前端在盒子与中控下按 mode 切换展示。
 */
export const usePortalStore = defineStore('portal', () => {
  const info = ref(null)
  const loading = ref(false)
  let loadPromise = null

  const mode = computed(() => info.value?.mode || 'box')
  const pointHistoryMode = computed(() => info.value?.pointHistoryMode || 'unavailable')
  const collectMetricsMode = computed(() => info.value?.collectMetricsMode || 'memory')
  const pointHistoryAvailable = computed(() => pointHistoryMode.value === 'influx')
  const metricsHistoryMinutes = computed(() =>
    Math.max(1, Number(info.value?.metricsHistoryMinutes) || 60)
  )

  const metricsMinuteOptions = computed(() => {
    const max = metricsHistoryMinutes.value
    const candidates = collectMetricsMode.value === 'influx'
      ? [10, 20, 30, 60, 180, 360, 1440]
      : [10, 20, 30, 60]
    return candidates.filter(m => m <= max)
  })

  const collectMetricsModeLabel = computed(() =>
    collectMetricsMode.value === 'influx' ? 'Influx 历史' : '内存缓存'
  )

  const ensureLoaded = async (force = false) => {
    if (info.value && !force) return info.value
    if (loadPromise && !force) return loadPromise
    loading.value = true
    loadPromise = (async () => {
      try {
        const res = await getPortalInfo()
        info.value = res?.data ?? res ?? null
        return info.value
      } finally {
        loading.value = false
        loadPromise = null
      }
    })()
    return loadPromise
  }

  const reset = () => {
    info.value = null
    loadPromise = null
  }

  return {
    info,
    loading,
    mode,
    pointHistoryMode,
    collectMetricsMode,
    pointHistoryAvailable,
    metricsHistoryMinutes,
    metricsMinuteOptions,
    collectMetricsModeLabel,
    ensureLoaded,
    reset
  }
})
