<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  KeyOutlined,
  ReloadOutlined,
  SwapOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import modelService from '@/services/api/model'
import type { CurrentModelSelection, ProviderSummary } from '@/services/api/model'
import { userMessageFromError } from '@/utils/errors'

const router = useRouter()
const providers = ref<ProviderSummary[]>([])
const current = ref<CurrentModelSelection | null>(null)
const loading = ref(false)
const testing = ref(false)
const loadError = ref('')

const keyModalVisible = ref(false)
const keyModalProvider = ref<ProviderSummary | null>(null)
const apiKeyInput = ref('')

const switchModalVisible = ref(false)
const switchProvider = ref<ProviderSummary | null>(null)
const switchModelName = ref('')

const currentProviderName = computed(() => current.value?.providerName || current.value?.providerId || '未选择')

async function loadData() {
  loading.value = true
  loadError.value = ''
  try {
    const [providerList, currentModel] = await Promise.all([
      modelService.getProviders(),
      modelService.getCurrent(),
    ])
    providers.value = providerList
    current.value = currentModel
  } catch (err: any) {
    loadError.value = userMessageFromError(err, '加载模型信息失败')
    message.error(loadError.value)
  } finally {
    loading.value = false
  }
}

function openKeyModal(provider: ProviderSummary) {
  keyModalProvider.value = provider
  apiKeyInput.value = ''
  keyModalVisible.value = true
}

async function saveApiKey() {
  if (!keyModalProvider.value || !apiKeyInput.value.trim()) {
    message.warning('请输入 API Key')
    return
  }
  try {
    await modelService.saveApiKey(keyModalProvider.value.providerId, apiKeyInput.value.trim())
    message.success('API Key 已保存')
    keyModalVisible.value = false
    await loadData()
  } catch (err: any) {
    message.error(userMessageFromError(err, '保存失败'))
  }
}

function openSwitchModal(provider: ProviderSummary) {
  switchProvider.value = provider
  switchModelName.value = provider.models[0] || ''
  switchModalVisible.value = true
}

async function switchModel() {
  if (!switchProvider.value || !switchModelName.value) {
    return
  }
  try {
    current.value = await modelService.switchModel(switchProvider.value.providerId, switchModelName.value)
    message.success(`已切换到 ${current.value.providerName} / ${current.value.modelName}`)
    switchModalVisible.value = false
    await loadData()
  } catch (err: any) {
    message.error(userMessageFromError(err, '切换失败'))
  }
}

async function testModel() {
  testing.value = true
  try {
    const result = await modelService.testModel()
    message.success(`${result.providerId} / ${result.modelName} 测试通过`)
  } catch (err: any) {
    message.error(userMessageFromError(err, '测试失败'))
  } finally {
    testing.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <main class="settings-page">
    <div class="page-header">
      <a-button @click="router.push('/chat')">
        <ArrowLeftOutlined />
        返回对话
      </a-button>
      <div>
        <div class="eyebrow">Model Providers</div>
        <h1>模型设置</h1>
      </div>
      <a-button :loading="loading" @click="loadData">
        <ReloadOutlined />
        刷新
      </a-button>
    </div>

    <a-alert
      v-if="loadError"
      class="page-alert"
      show-icon
      type="warning"
      :message="loadError"
    >
      <template #action>
        <a-button size="small" @click="loadData">重试</a-button>
      </template>
    </a-alert>

    <a-card class="current-card" v-if="current">
      <template #title>
        <span><ThunderboltOutlined /> 当前模型</span>
      </template>
      <template #extra>
        <a-button :loading="testing" @click="testModel">
          <ThunderboltOutlined />
          测试
        </a-button>
      </template>
      <a-descriptions :column="2">
        <a-descriptions-item label="供应商">{{ currentProviderName }}</a-descriptions-item>
        <a-descriptions-item label="模型">{{ current.modelName }}</a-descriptions-item>
        <a-descriptions-item label="API Key">
          <a-tag :color="current.apiKeyConfigured ? 'green' : 'red'">
            {{ current.apiKeyConfigured ? '已配置' : '未配置' }}
          </a-tag>
        </a-descriptions-item>
      </a-descriptions>
    </a-card>

    <a-spin :spinning="loading">
      <div class="provider-grid">
        <a-card v-for="provider in providers" :key="provider.providerId" class="provider-card">
          <template #title>
            <div class="provider-title">
              <span>{{ provider.displayName }}</span>
              <a-tag v-if="provider.providerId === current?.providerId" color="blue">
                <CheckCircleOutlined />
                当前
              </a-tag>
            </div>
          </template>

          <div class="models">
            <a-tag v-for="model in provider.models" :key="model">{{ model }}</a-tag>
          </div>

          <div class="provider-footer">
            <a-tag :color="provider.apiKeyConfigured ? 'green' : 'default'">
              {{ provider.apiKeyConfigured ? 'Key 已配置' : 'Key 未配置' }}
            </a-tag>
            <a-space>
              <a-button size="small" @click="openKeyModal(provider)">
                <KeyOutlined />
                Key
              </a-button>
              <a-button size="small" type="primary" ghost @click="openSwitchModal(provider)">
                <SwapOutlined />
                切换
              </a-button>
            </a-space>
          </div>
        </a-card>
      </div>
    </a-spin>

    <a-modal v-model:open="keyModalVisible" title="设置 API Key" @ok="saveApiKey">
      <a-form layout="vertical">
        <a-form-item :label="`${keyModalProvider?.displayName || ''} API Key`">
          <a-input-password v-model:value="apiKeyInput" placeholder="输入 API Key" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="switchModalVisible" title="切换模型" @ok="switchModel">
      <a-form layout="vertical">
        <a-form-item label="供应商">
          <a-input :value="switchProvider?.displayName" disabled />
        </a-form-item>
        <a-form-item label="模型">
          <a-select v-model:value="switchModelName">
            <a-select-option v-for="model in switchProvider?.models || []" :key="model" :value="model">
              {{ model }}
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </main>
</template>

<style lang="less" scoped>
.settings-page {
  height: calc(100vh - 56px);
  overflow: auto;
  padding: 24px;
}

.page-header {
  align-items: center;
  display: flex;
  justify-content: space-between;
  margin: 0 auto 18px;
  max-width: 1100px;
}

.page-header h1 {
  font-size: 24px;
  margin: 2px 0 0;
  text-align: center;
}

.eyebrow {
  color: #738096;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-align: center;
  text-transform: uppercase;
}

.current-card,
.provider-grid,
.page-alert {
  margin: 0 auto 16px;
  max-width: 1100px;
}

.provider-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
}

.provider-title,
.provider-footer {
  align-items: center;
  display: flex;
  justify-content: space-between;
}

.models {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 38px;
}

.provider-footer {
  border-top: 1px solid #edf1f6;
  margin-top: 16px;
  padding-top: 12px;
}
</style>
