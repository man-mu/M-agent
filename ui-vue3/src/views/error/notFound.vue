<template>
  <div class="not-found-page">
    <div class="error-container">
      <h1 class="error-code">404</h1>
      <p class="error-message">页面未找到</p>
      <a-button type="primary" @click="backHome">返回首页</a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const backHome = () => {
  router.push('/')
}
</script>

<style lang="less" scoped>
.not-found-page {
  width: 100%;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: #f5f5f5;
}

.error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
  padding: 48px;
  border-radius: 24px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.error-code {
  font-size: 64px;
  font-weight: 700;
  margin: 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.error-message {
  font-size: 18px;
  color: #888;
  margin: 0;
}
</style>
