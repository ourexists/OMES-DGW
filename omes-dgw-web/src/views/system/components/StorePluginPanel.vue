<template>
  <a-alert
    type="info"
    show-icon
    message="请直接编辑下方连接配置"
    description="存储已改为编译期模块（omes-dgw-store-*），不再使用插件热插拔。"
  />
</template>

<script setup>
defineProps({
  types: { type: Array, default: () => [] }
})
defineExpose({ reload: () => {} })
</script>
