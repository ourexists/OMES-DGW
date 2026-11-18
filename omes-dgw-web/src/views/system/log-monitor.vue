<template>
  <div class="page-container log-monitor-page">
    <div class="page-header">
      <h2>日志监控</h2>
      <a-space size="small">
        <span class="live-dot" :class="{ 'is-on': refreshInterval > 0 }" />
        <span class="log-meta">{{ metaText }}</span>
        <a-button :loading="loading" @click="reloadContent">
          <ReloadOutlined />
          刷新
        </a-button>
      </a-space>
    </div>

    <div class="log-filters">
      <div class="log-filter">
        <span class="log-filter__label">文件</span>
        <a-select
          v-model:value="selectedName"
          class="log-filter__control log-filter__control--file"
          :options="fileOptions"
          placeholder="选择日志"
          @change="reloadContent"
        />
      </div>
      <div class="log-filter">
        <span class="log-filter__label">级别</span>
        <a-select v-model:value="level" class="log-filter__control" @change="reloadContent">
          <a-select-option value="ALL">全部</a-select-option>
          <a-select-option value="ERROR">ERROR+</a-select-option>
          <a-select-option value="WARN">WARN+</a-select-option>
          <a-select-option value="INFO">INFO+</a-select-option>
          <a-select-option value="DEBUG">DEBUG+</a-select-option>
        </a-select>
      </div>
      <div class="log-filter">
        <span class="log-filter__label">行数</span>
        <a-select v-model:value="tailLines" class="log-filter__control" @change="reloadContent">
          <a-select-option :value="100">100</a-select-option>
          <a-select-option :value="200">200</a-select-option>
          <a-select-option :value="500">500</a-select-option>
        </a-select>
      </div>
      <div class="log-filter">
        <span class="log-filter__label">刷新</span>
        <a-select v-model:value="refreshInterval" class="log-filter__control" @change="restartPolling">
          <a-select-option :value="0">手动</a-select-option>
          <a-select-option :value="3000">3 秒</a-select-option>
          <a-select-option :value="5000">5 秒</a-select-option>
        </a-select>
      </div>
      <div class="log-filter log-filter--grow">
        <span class="log-filter__label">关键字</span>
        <a-input-search
          v-model:value="keyword"
          allow-clear
          placeholder="过滤内容"
          class="log-filter__control log-filter__control--search"
          @search="reloadContent"
        />
      </div>
    </div>

    <div class="log-panel">
      <div class="log-panel__bar">
        <span class="log-panel__name">{{ selectedName || '未选择文件' }}</span>
        <span class="log-panel__hint">仅显示尾部 {{ tailLines }} 行</span>
      </div>
      <div ref="viewerRef" class="log-viewer">
        <div v-if="!lines.length && !loading" class="log-viewer__empty">暂无日志</div>
        <div
          v-for="(line, idx) in lines"
          :key="idx"
          class="log-line"
          :class="lineClass(line)"
        >{{ line }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { listLogFiles, readLogContent } from '@/api/system'

const files = ref([])
const loading = ref(false)
const selectedName = ref('')
const lines = ref([])
const fileSize = ref(0)

const level = ref('ALL')
const keyword = ref('')
const tailLines = ref(200)
const refreshInterval = ref(5000)

const viewerRef = ref(null)
let pollingTimer = null
let pollingActive = false
let requestSeq = 0

const fileOptions = computed(() =>
  files.value.map(f => ({
    value: f.name,
    label: `${f.name} (${formatSize(f.size)})`
  }))
)

const metaText = computed(() => {
  if (!selectedName.value) return '未加载'
  return `${formatSize(fileSize.value)} · ${lines.value.length} 行`
})

const formatSize = bytes => {
  if (bytes == null) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

const lineClass = line => {
  if (/\bERROR\b/.test(line)) return 'is-error'
  if (/\bWARN\b/.test(line)) return 'is-warn'
  if (/\bDEBUG\b/.test(line) || /\bTRACE\b/.test(line)) return 'is-debug'
  if (/\bINFO\b/.test(line)) return 'is-info'
  return ''
}

const scrollToBottom = async () => {
  await nextTick()
  const el = viewerRef.value
  if (el) el.scrollTop = el.scrollHeight
}

const loadFiles = async () => {
  try {
    const res = await listLogFiles()
    files.value = res.data || []
    if (!selectedName.value && files.value.length) {
      selectedName.value = files.value[0].name
    } else if (selectedName.value && !files.value.some(f => f.name === selectedName.value)) {
      selectedName.value = files.value[0]?.name || ''
    }
  } catch (e) {
    // request 拦截器已提示
  }
}

const fetchContent = async () => {
  if (!selectedName.value) {
    lines.value = []
    return
  }
  const seq = ++requestSeq
  loading.value = true
  try {
    const res = await readLogContent({
      name: selectedName.value,
      level: level.value,
      keyword: keyword.value || undefined,
      tailLines: tailLines.value
    })
    if (seq !== requestSeq) return
    lines.value = res.data?.lines || []
    fileSize.value = res.data?.size || 0
    await scrollToBottom()
  } catch (e) {
    // request 拦截器已提示
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

const reloadContent = async () => {
  await fetchContent()
  restartPolling()
}

const stopPolling = () => {
  pollingActive = false
  if (pollingTimer) {
    clearTimeout(pollingTimer)
    pollingTimer = null
  }
}

const restartPolling = () => {
  stopPolling()
  if (refreshInterval.value <= 0 || !selectedName.value) return
  pollingActive = true
  const poll = async () => {
    pollingTimer = null
    if (!pollingActive) return
    await fetchContent()
    if (!pollingActive || refreshInterval.value <= 0) return
    pollingTimer = setTimeout(poll, refreshInterval.value)
  }
  pollingTimer = setTimeout(poll, refreshInterval.value)
}

onMounted(async () => {
  await loadFiles()
  await reloadContent()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.log-monitor-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden !important;
  gap: 12px;
}

.page-header {
  flex: 0 0 auto;
  margin-bottom: 0;
}

.log-meta {
  font-size: 13px;
  color: var(--omes-color-text-secondary);
  font-variant-numeric: tabular-nums;
}

.live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--omes-color-text-tertiary);
  display: inline-block;
}

.live-dot.is-on {
  background: var(--omes-color-success);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--omes-color-success) 20%, transparent);
}

.log-filters {
  flex: 0 0 auto;
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
  align-items: center;
  padding: 12px 14px;
  border-radius: var(--omes-border-radius-x-large);
  background: var(--omes-color-bg-container);
  box-shadow: var(--omes-fluent-surface-shadow);
}

.log-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.log-filter--grow {
  flex: 1 1 200px;
}

.log-filter__label {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--omes-color-text-tertiary);
  white-space: nowrap;
}

.log-filter__control {
  width: 110px;
}

.log-filter__control--file {
  width: 240px;
}

.log-filter__control--search {
  width: 100%;
  min-width: 160px;
  max-width: 280px;
}

.log-panel {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: var(--omes-border-radius-x-large);
  background: var(--omes-color-bg-container);
  box-shadow: var(--omes-fluent-surface-shadow);
}

.log-panel__bar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 14px;
  border-bottom: 1px solid var(--omes-fluent-divider);
  background: var(--omes-color-bg-muted);
}

.log-panel__name {
  font-size: 13px;
  font-weight: 500;
  color: var(--omes-color-text);
  font-family: 'Cascadia Mono', Consolas, ui-monospace, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.log-panel__hint {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--omes-color-text-tertiary);
}

.log-viewer {
  flex: 1 1 0;
  min-height: 0;
  overflow: auto;
  padding: 10px 0;
  background: var(--omes-color-bg-standalone);
  color: color-mix(in srgb, var(--omes-color-white) 88%, transparent);
  font-family: 'Cascadia Mono', Consolas, ui-monospace, monospace;
  font-size: 12px;
  line-height: 1.55;
}

.log-viewer::-webkit-scrollbar-thumb {
  background: color-mix(in srgb, var(--omes-color-white) 22%, transparent);
}

.log-viewer::-webkit-scrollbar-thumb:hover {
  background: color-mix(in srgb, var(--omes-color-white) 35%, transparent);
}

.log-viewer__empty {
  color: color-mix(in srgb, var(--omes-color-white) 45%, transparent);
  padding: 48px 0;
  text-align: center;
}

.log-line {
  padding: 1px 14px;
  white-space: pre-wrap;
  word-break: break-word;
  border-left: 2px solid transparent;
}

.log-line:hover {
  background: color-mix(in srgb, var(--omes-color-white) 4%, transparent);
}

.log-line.is-error {
  color: #fca5a5;
  border-left-color: var(--omes-color-error);
  background: color-mix(in srgb, var(--omes-color-error) 12%, transparent);
}

.log-line.is-warn {
  color: #fcd34d;
  border-left-color: var(--omes-color-warning);
}

.log-line.is-info {
  color: #93c5fd;
}

.log-line.is-debug {
  color: color-mix(in srgb, var(--omes-color-white) 55%, transparent);
}

@media (max-width: 768px) {
  .log-filter__control--file {
    width: 180px;
  }

  .log-filter__control--search {
    max-width: none;
  }
}
</style>
