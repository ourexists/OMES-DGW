<template>
  <div class="switch-lights" :class="`size-${size}`" @click.stop>
    <a-tooltip v-for="item in lights" :key="item.key" :title="tooltipText(item)">
      <div class="switch-light" :class="[`kind-${item.key}`, `state-${item.state}`]">
        <span class="lamp">
          <span class="lamp-core"></span>
        </span>
        <span class="lamp-label">{{ item.label }}</span>
      </div>
    </a-tooltip>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { resolveSwitchLights } from '@/utils/switch-status'

const props = defineProps({
  device: {
    type: Object,
    default: () => ({})
  },
  size: {
    type: String,
    default: 'card'
  }
})

const lights = computed(() => resolveSwitchLights(props.device))

const stateLabel = {
  on: '开',
  off: '关',
  unknown: '无数据',
  error: '读取失败',
  unset: '未配置'
}

const tooltipText = item => {
  const status = stateLabel[item.state] || '未知'
  if (item.state === 'error' && item.error) {
    return `${item.label}：${status}（${item.error}）`
  }
  return `${item.label}：${status}`
}
</script>

<style scoped>
.switch-lights {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 4px;
  border-radius: 20px;
  background: var(--omes-color-bg-layout);
  border: 1px solid rgba(0, 51, 102, 0.06);
}

.switch-light {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 10px;
  border-radius: 14px;
  background: transparent;
  color: var(--omes-color-text-tertiary);
  cursor: default;
  transition: background 0.2s ease, color 0.2s ease;
}

.lamp {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #d9dee6;
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.18);
  flex-shrink: 0;
}

.lamp-core {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #b0b8c4;
  box-shadow: inset 0 1px 1px rgba(255, 255, 255, 0.55);
}

.lamp-label {
  font-size: 12px;
  line-height: 1;
  font-weight: 500;
  white-space: nowrap;
}

.size-header {
  padding: 5px;
  border-radius: 22px;
}

.size-header .switch-light {
  height: 32px;
  padding: 0 12px;
}

.size-header .lamp {
  width: 14px;
  height: 14px;
}

.size-header .lamp-core {
  width: 9px;
  height: 9px;
}

.size-header .lamp-label {
  font-size: 13px;
}

.kind-power.state-on {
  color: var(--omes-color-success);
  background: rgba(45, 143, 78, 0.12);
}

.kind-running.state-on {
  color: var(--omes-color-warning);
  background: rgba(212, 160, 23, 0.14);
}

.kind-fault.state-on {
  color: var(--omes-color-error);
  background: rgba(196, 30, 58, 0.12);
}

.kind-power.state-off,
.kind-running.state-off,
.kind-fault.state-off {
  color: var(--omes-color-text-secondary);
}

.kind-power.state-on .lamp {
  background: rgba(45, 143, 78, 0.22);
}

.kind-running.state-on .lamp {
  background: rgba(212, 160, 23, 0.24);
}

.kind-fault.state-on .lamp {
  background: rgba(196, 30, 58, 0.22);
}

.kind-power.state-on .lamp-core {
  background: #2d8f4e;
  box-shadow: 0 0 6px 1px rgba(45, 143, 78, 0.7), inset 0 1px 1px rgba(255, 255, 255, 0.55);
  animation: lamp-pulse-green 1.8s ease-in-out infinite;
}

.kind-running.state-on .lamp-core {
  background: #d4a017;
  box-shadow: 0 0 6px 1px rgba(212, 160, 23, 0.75), inset 0 1px 1px rgba(255, 255, 255, 0.55);
  animation: lamp-pulse-yellow 1.8s ease-in-out infinite;
}

.kind-fault.state-on .lamp-core {
  background: #c41e3a;
  box-shadow: 0 0 6px 1px rgba(196, 30, 58, 0.75), inset 0 1px 1px rgba(255, 255, 255, 0.5);
  animation: lamp-pulse-red 1.2s ease-in-out infinite;
}

.kind-power.state-off .lamp-core {
  background: #7aa88a;
}

.kind-running.state-off .lamp-core {
  background: #c4b07a;
}

.kind-fault.state-off .lamp-core {
  background: #c48a94;
}

.state-error {
  color: var(--omes-color-error);
}

.state-error .lamp-core {
  background: var(--omes-color-error);
  opacity: 0.45;
}

@keyframes lamp-pulse-green {
  0%, 100% { box-shadow: 0 0 4px 0 rgba(45, 143, 78, 0.45), inset 0 1px 1px rgba(255, 255, 255, 0.55); }
  50% { box-shadow: 0 0 8px 2px rgba(45, 143, 78, 0.8), inset 0 1px 1px rgba(255, 255, 255, 0.55); }
}

@keyframes lamp-pulse-yellow {
  0%, 100% { box-shadow: 0 0 4px 0 rgba(212, 160, 23, 0.45), inset 0 1px 1px rgba(255, 255, 255, 0.55); }
  50% { box-shadow: 0 0 8px 2px rgba(212, 160, 23, 0.85), inset 0 1px 1px rgba(255, 255, 255, 0.55); }
}

@keyframes lamp-pulse-red {
  0%, 100% { box-shadow: 0 0 4px 0 rgba(196, 30, 58, 0.4), inset 0 1px 1px rgba(255, 255, 255, 0.5); }
  50% { box-shadow: 0 0 9px 2px rgba(196, 30, 58, 0.9), inset 0 1px 1px rgba(255, 255, 255, 0.5); }
}
</style>
