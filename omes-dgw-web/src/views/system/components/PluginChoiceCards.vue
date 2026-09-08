<template>
  <a-radio-group :value="modelValue" class="plugin-choices" :aria-label="label" @update:value="$emit('update:modelValue', $event)">
    <div v-for="option in options" :key="option.value" class="plugin-choice" :class="{ 'is-selected': modelValue === option.value }" @click="$emit('update:modelValue', option.value)">
      <a-radio :value="option.value"><span class="plugin-choice__title">{{ option.label }}</span></a-radio>
      <span class="plugin-choice__description">{{ option.description }}</span>
    </div>
  </a-radio-group>
</template>

<script setup>
defineProps({
  modelValue: { type: String, default: '' },
  label: { type: String, default: '选择插件' },
  options: { type: Array, default: () => [] }
})
defineEmits(['update:modelValue'])
</script>

<style scoped>
.plugin-choices {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(240px, 100%), 1fr));
  gap: 12px;
  width: 100%;
  margin: 12px 0;
}
.plugin-choice {
  padding: 16px;
  border: 1px solid var(--omes-fluent-divider, #d9d9d9);
  border-radius: 10px;
  cursor: pointer;
}
.plugin-choice.is-selected {
  border-color: var(--omes-color-primary, #1677ff);
  background: var(--omes-color-primary-bg, #e6f4ff);
}
.plugin-choice__title { font-weight: 600; }
.plugin-choice__description {
  display: block;
  margin: 8px 0 0 24px;
  font-size: 12px;
  color: var(--omes-color-text-secondary, #595959);
}
</style>
