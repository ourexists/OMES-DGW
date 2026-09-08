<template>
  <a-spin :spinning="loading">
    <a-empty v-if="!section" :description="emptyText || '暂无配置'" />
    <a-form v-else layout="vertical" class="config-section-form" :class="{ 'is-compact': compact }">
      <p v-if="section.description && !hideDescription" class="section-desc">{{ section.description }}</p>

      <div v-for="block in fieldBlocks" :key="block.key" class="field-block">
        <div v-if="block.title" class="field-block__head">
          <h4>{{ block.title }}</h4>
        </div>
        <div class="field-grid" :class="{ 'field-grid--custom': $slots.field }">
          <div
            v-for="field in block.fields"
            :key="field.key"
            class="field-cell"
            :class="fieldCellClass(field)"
          >
            <slot name="field" :field="field" :values="form">
            <a-form-item :label="field.label" :extra="field.hint || undefined">
              <a-switch
                v-if="field.type === 'boolean'"
                :checked="form[field.key] === 'true' || form[field.key] === true"
                checked-children="开"
                un-checked-children="关"
                @update:checked="v => (form[field.key] = String(v))"
              />
              <a-select
                v-else-if="field.type === 'select'"
                v-model:value="form[field.key]"
                :options="(field.options || []).map(o => ({ label: field.key === 'COLLECT_MONITOR_TYPE' ? (o === 'influxdb' ? 'Influxdb' : '无') : o, value: o }))"
                style="width: 100%"
              />
              <a-input-password
                v-else-if="field.type === 'password'"
                v-model:value="form[field.key]"
                :placeholder="passwordPlaceholder(field)"
                allow-clear
              />
              <a-input-number
                v-else-if="field.type === 'int' || field.type === 'double'"
                v-model:value="numberModel[field.key]"
                style="width: 100%"
                :step="field.type === 'double' ? 0.1 : 1"
                @change="v => onNumberChange(field, v)"
              />
              <a-input
                v-else
                v-model:value="form[field.key]"
                :placeholder="field.defaultValue || ''"
                allow-clear
              />
            </a-form-item>
            </slot>
          </div>
        </div>
      </div>

      <slot name="before-footer" :values="form" />
      <div v-if="!hideFooter" class="form-footer">
        <div class="form-footer__hint">{{ sectionId.startsWith('plugin-') ? '保存后在线生效，无需重启。' : '保存后，部分配置需重启生效。' }}</div>
        <a-space wrap>
          <slot name="extra-actions" />
          <a-button :loading="loading" @click="reload">重置</a-button>
          <a-button type="primary" :loading="saving" @click="handleSave">保存更改</a-button>
        </a-space>
      </div>
    </a-form>
  </a-spin>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { getRuntimeConfigSection, saveRuntimeConfigSection } from '@/api/system'

const props = defineProps({
  sectionId: { type: String, required: true },
  emptyText: { type: String, default: '' },
  compact: { type: Boolean, default: false },
  hideDescription: { type: Boolean, default: false },
  hideFooter: { type: Boolean, default: false },
  visibleKeys: { type: Array, default: null },
  /** 仅展示并保存这些 groupLabel；空/不传则全部 */
  visibleGroups: { type: Array, default: null }
})

const emit = defineEmits(['saved', 'loaded', 'values-change'])

const loading = ref(false)
const saving = ref(false)
const section = ref(null)
const form = reactive({})
const numberModel = reactive({})
const clearSecrets = reactive({})

const visibleFields = computed(() => {
  const fields = (section.value?.fields || []).filter(field => !props.visibleKeys || props.visibleKeys.includes(field.key))
  const groups = props.visibleGroups
  if (!groups || !groups.length) return fields
  return fields.filter(f => groups.includes(f.groupLabel))
})

const fieldBlocks = computed(() => {
  const fields = visibleFields.value
  const blocks = []
  let current = null
  for (const field of fields) {
    const title = field.groupLabel || ''
    if (!current || current.title !== title) {
      current = { key: `${title || 'default'}-${blocks.length}`, title, fields: [] }
      blocks.push(current)
    }
    current.fields.push(field)
  }
  return blocks
})

const fieldCellClass = field => {
  if (field.type === 'boolean' || field.type === 'select' || field.type === 'int' || field.type === 'double') {
    return 'field-cell--sm'
  }
  if (
    field.key.includes('PATH') ||
    field.key.includes('URL') ||
    field.key.includes('DIR') ||
    field.key.includes('PREFIX') ||
    field.type === 'password'
  ) {
    return 'field-cell--full'
  }
  return 'field-cell--md'
}

const passwordPlaceholder = field => {
  if (clearSecrets[field.key]) return '将清除，保存后生效'
  if (section.value?.secretsSet?.[field.key]) return '已设置，留空则保持不变'
  return '可选'
}

const syncForm = data => {
  section.value = data
  Object.keys(form).forEach(k => delete form[k])
  Object.keys(numberModel).forEach(k => delete numberModel[k])
  Object.keys(clearSecrets).forEach(k => delete clearSecrets[k])
  ;(data.fields || []).forEach(f => {
    const raw = data.values?.[f.key]
    if (f.type === 'int' || f.type === 'double') {
      const n = raw === '' || raw == null ? undefined : Number(raw)
      numberModel[f.key] = Number.isFinite(n) ? n : undefined
      form[f.key] = raw ?? f.defaultValue ?? ''
    } else if (f.type === 'password') {
      form[f.key] = ''
    } else {
      form[f.key] = raw ?? f.defaultValue ?? ''
    }
  })
  emit('loaded', data)
  emitValuesChange()
}

const emitValuesChange = () => {
  emit('values-change', { ...form })
}

watch(form, emitValuesChange, { deep: true })

const reload = async () => {
  loading.value = true
  try {
    const res = await getRuntimeConfigSection(props.sectionId)
    syncForm(res.data)
  } finally {
    loading.value = false
  }
}

const onNumberChange = (field, v) => {
  form[field.key] = v === null || v === undefined ? '' : String(v)
}

watch(
  () => props.sectionId,
  () => {
    if (props.sectionId) reload()
  }
)

const handleSave = async () => {
  if (!section.value) return
  saving.value = true
  try {
    const values = {}
    for (const f of visibleFields.value) {
      if (f.type === 'password') {
        const typed = form[f.key]
        if (typed === undefined || typed === null || typed === '') {
          continue
        }
        values[f.key] = typed
      } else if (f.type === 'int' || f.type === 'double') {
        const n = numberModel[f.key]
        values[f.key] = n === null || n === undefined ? (f.defaultValue || '') : String(n)
      } else {
        values[f.key] = form[f.key] ?? ''
      }
    }
    const res = await saveRuntimeConfigSection(props.sectionId, { values })
    syncForm(res.data)
    message.success('配置已保存')
    emit('saved', res.data)
  } finally {
    saving.value = false
  }
}

onMounted(reload)

defineExpose({ reload, save: handleSave, getValues: () => ({ ...form }) })
</script>

<style scoped>
.config-section-form {
  max-width: 100%;
}

.section-desc {
  margin: 0 0 16px;
  color: rgba(0, 0, 0, 0.45);
}

.field-block + .field-block {
  margin-top: 8px;
}

.field-block__head {
  margin: 4px 0 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--omes-fluent-divider, rgba(0, 0, 0, 0.06));
}

.field-block__head h4 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--omes-color-text-secondary, rgba(0, 0, 0, 0.65));
  letter-spacing: 0.02em;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 0 16px;
}

.field-cell {
  grid-column: span 6;
}

.field-cell--sm {
  grid-column: span 4;
}

.field-cell--md {
  grid-column: span 6;
}

.field-cell--full {
  grid-column: span 12;
}

.field-grid--custom .field-cell {
  grid-column: span 12;
}

.form-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--omes-fluent-divider, rgba(0, 0, 0, 0.06));
}

.form-footer__hint {
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
  line-height: 1.5;
}

.is-compact :deep(.ant-form-item) {
  margin-bottom: 14px;
}

@media (max-width: 900px) {
  .field-cell,
  .field-cell--sm,
  .field-cell--md,
  .field-cell--full {
    grid-column: span 12;
  }

  .form-footer {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
