<template>
  <div class="redirect-page">
    <a-spin tip="插件能力已并入系统配置，正在跳转…" />
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

onMounted(() => {
  router.replace('/system/runtime-config')
})
</script>

<style scoped>
.redirect-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
}
</style>
