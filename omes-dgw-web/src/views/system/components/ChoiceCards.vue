<template>
  <a-radio-group
    :value="modelValue"
    class="choice-cards"
    :aria-label="label"
    @update:value="$emit('update:modelValue', $event)"
  >
    <div
      v-for="option in options"
      :key="option.value"
      class="choice-card"
      :class="{ 'is-selected': modelValue === option.value }"
      @click="$emit('update:modelValue', option.value)"
    >
      <a-radio :value="option.value">
        <span class="choice-card__title">{{ option.label }}</span>
      </a-radio>
      <span class="choice-card__description">{{ option.description }}</span>
    </div>
  </a-radio-group>
</template>

<script setup>
defineProps({
  modelValue: { type: String, default: '' },
  label: { type: String, default: '选择' },
  options: { type: Array, default: () => [] }
})
defineEmits(['update:modelValue'])
</script>

<style scoped>
.choice-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(240px, 100%), 1fr));
  gap: 12px;
  width: 100%;
  margin: 12px 0;
}
.choice-card {
  padding: 16px;
  border: 1px solid var(--omes-color-border-secondary);
  border-radius: var(--omes-border-radius-x-large);
  cursor: pointer;
}
.choice-card.is-selected {
  border-color: var(--omes-color-primary);
  background: var(--omes-color-primary-bg);
}
.choice-card__title {
  font-weight: var(--omes-font-weight-semibold);
}
.choice-card__description {
  display: block;
  margin: 8px 0 0 24px;
  font-size: 12px;
  color: var(--omes-color-text-secondary);
}
</style>
