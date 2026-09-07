<template>
  <a-config-provider :theme="omesAntdTheme">
    <router-view />
  </a-config-provider>
</template>

<script setup>
import { omesAntdTheme } from '@/theme/antdTheme'
</script>
