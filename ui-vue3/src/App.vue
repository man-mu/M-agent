<script setup lang="ts">
import { RouterView } from 'vue-router'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { PRIMARY_COLOR } from '@/base/constants'
</script>

<template>
  <a-config-provider :locale="zhCN" :theme="{ token: { colorPrimary: PRIMARY_COLOR } }">
    <RouterView />
  </a-config-provider>
</template>

<style lang="less">
html,
body,
#app {
  height: 100%;
  margin: 0;
}

* {
  box-sizing: border-box;
}

body {
  color: #20242a;
  font-family:
    Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}
</style>
