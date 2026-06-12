<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  DatabaseOutlined,
  DeleteOutlined,
  EditOutlined,
  FileOutlined,
  ReloadOutlined,
  SaveOutlined,
  UploadOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import message from 'ant-design-vue/es/message'
import knowledgeService from '@/services/api/knowledge'
import type { RagDocumentItem, UserProfileData } from '@/services/api/knowledge'
import {
  completeRagUploadItem,
  createRagUploadItem,
  failRagUploadItem,
  ragUploadStatusLabel,
  ragUploadStatusColor,
  RAG_UPLOAD_FORMAT_HINT,
  validateRagUploadFile,
} from '@/views/chat/ragUpload'
import type { RagUploadItem } from '@/views/chat/ragUpload'

// ========== 全局文档 ==========
const documents = ref<RagDocumentItem[]>([])
const docLoading = ref(false)
const docError = ref('')
const uploading = ref(false)
const uploadItems = ref<RagUploadItem[]>([])

async function loadDocuments() {
  docLoading.value = true
  docError.value = ''
  try {
    documents.value = await knowledgeService.listGlobalDocuments()
  } catch (error: unknown) {
    docError.value = (error as Error)?.message || '加载文档列表失败'
  } finally {
    docLoading.value = false
  }
}

function handleUpload(file: File) {
  const validation = validateRagUploadFile(file)
  if (!validation.valid) {
    message.warning(validation.error || '文件校验失败')
    return false
  }

  uploading.value = true
  const item = createRagUploadItem(file, '__global__')
  uploadItems.value = [item, ...uploadItems.value].slice(0, 3)

  knowledgeService.uploadGlobalDocument(file)
    .then(result => {
      uploadItems.value = uploadItems.value.map(i =>
        i.id === item.id ? completeRagUploadItem(i, { ...result, sessionId: '__global__' }) : i,
      )
      message.success(`${file.name} 上传成功，已切 ${result.chunks} 块`)
      loadDocuments()
    })
    .catch((error: unknown) => {
      uploadItems.value = uploadItems.value.map(i =>
        i.id === item.id ? failRagUploadItem(i, error) : i,
      )
      message.error(`上传失败：${(error as Error)?.message || '未知错误'}`)
    })
    .finally(() => {
      uploading.value = false
    })
  return false
}

async function deleteDocument(id: string, fileName: string) {
  try {
    await knowledgeService.deleteGlobalDocument(id)
    message.success(`已删除 ${fileName}`)
    documents.value = documents.value.filter(d => d.id !== id)
  } catch (error: unknown) {
    message.error(`删除失败：${(error as Error)?.message || '未知错误'}`)
  }
}

function formatTime(value: string) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// ========== 用户画像 ==========
const profile = ref<UserProfileData | null>(null)
const profileLoading = ref(false)
const profileError = ref('')
const editing = ref(false)
const editForm = ref({
  profile_summary: '',
  expertise_level: '',
  detail_preference: '',
  style_preference: '',
  manual_fields: [] as string[],
})

const expertiseOptions = ['beginner', 'intermediate', 'advanced']
const detailOptions = ['concise', 'balanced', 'comprehensive']
const styleOptions = ['practical', 'theoretical', 'mixed']

const expertiseLabels: Record<string, string> = {
  beginner: '初学者',
  intermediate: '中级',
  advanced: '高级',
}
const detailLabels: Record<string, string> = {
  concise: '简洁',
  balanced: '均衡',
  comprehensive: '详细',
}
const styleLabels: Record<string, string> = {
  practical: '实践',
  theoretical: '理论',
  mixed: '混合',
}

async function loadProfile() {
  profileLoading.value = true
  profileError.value = ''
  try {
    profile.value = await knowledgeService.getUserProfile()
  } catch (error: unknown) {
    profileError.value = (error as Error)?.message || '加载画像失败'
  } finally {
    profileLoading.value = false
  }
}

function startEdit() {
  if (!profile.value) return
  editForm.value = {
    profile_summary: profile.value.profile_summary,
    expertise_level: profile.value.expertise_level,
    detail_preference: profile.value.detail_preference,
    style_preference: profile.value.style_preference,
    manual_fields: [...profile.value.manual_fields],
  }
  editing.value = true
}

function cancelEdit() {
  editing.value = false
}

function toggleManualField(field: string) {
  const idx = editForm.value.manual_fields.indexOf(field)
  if (idx >= 0) {
    editForm.value.manual_fields.splice(idx, 1)
  } else {
    editForm.value.manual_fields.push(field)
  }
}

function isManual(field: string): boolean {
  return editing.value
    ? editForm.value.manual_fields.includes(field)
    : (profile.value?.manual_fields || []).includes(field)
}

async function saveProfile() {
  try {
    profile.value = await knowledgeService.updateUserProfile(editForm.value)
    editing.value = false
    message.success('画像已更新')
  } catch (error: unknown) {
    message.error(`保存失败：${(error as Error)?.message || '未知错误'}`)
  }
}

async function resetProfile() {
  try {
    await knowledgeService.resetManualFields()
    message.success('手动覆盖已重置')
    await loadProfile()
  } catch (error: unknown) {
    message.error(`重置失败：${(error as Error)?.message || '未知错误'}`)
  }
}

const hasProfile = computed(() => profile.value?.has_profile === true)

onMounted(() => {
  loadDocuments()
  loadProfile()
})
</script>

<template>
  <div class="knowledge-page">
    <!-- 全局知识库 -->
    <a-card class="section-card">
      <template #title>
        <div class="section-header">
          <span><DatabaseOutlined /> 全局知识库</span>
          <a-upload
            :before-upload="handleUpload"
            :show-upload-list="false"
            :disabled="uploading"
          >
            <a-button type="primary" :loading="uploading">
              <UploadOutlined />
              上传文档
            </a-button>
          </a-upload>
        </div>
      </template>
      <p class="section-desc">上传的文档对所有会话生效，用于 RAG 语义检索。{{ RAG_UPLOAD_FORMAT_HINT }}</p>

      <a-spin :spinning="docLoading">
        <a-alert
          v-if="docError"
          type="error"
          :message="docError"
          show-icon
          style="margin-bottom: 16px"
        />

        <div v-if="uploadItems.length" class="upload-items">
          <div
            v-for="item in uploadItems"
            :key="item.id"
            class="upload-item"
            :class="item.status"
          >
            <FileOutlined />
            <span class="upload-name">{{ item.fileName }}</span>
            <a-tag :color="ragUploadStatusColor(item.status)" size="small">
              {{ ragUploadStatusLabel(item) }}
            </a-tag>
            <span v-if="item.error" class="upload-error">{{ item.error }}</span>
          </div>
        </div>

        <a-empty
          v-if="!docLoading && !docError && documents.length === 0 && uploadItems.length === 0"
          description="尚未上传全局文档"
        />

        <div v-if="documents.length" class="doc-list">
          <div v-for="doc in documents" :key="doc.id" class="doc-item">
            <div class="doc-info">
              <FileOutlined class="doc-icon" />
              <span class="doc-name">{{ doc.fileName }}</span>
              <a-tag color="blue" size="small">{{ doc.chunks }} 块</a-tag>
              <span class="doc-time">{{ formatTime(doc.uploadedAt) }}</span>
            </div>
            <a-popconfirm
              :title="`确定删除 ${doc.fileName}？`"
              ok-text="删除"
              cancel-text="取消"
              @confirm="deleteDocument(doc.id, doc.fileName)"
            >
              <a-button size="small" type="text" danger>
                <DeleteOutlined />
              </a-button>
            </a-popconfirm>
          </div>
        </div>
      </a-spin>
    </a-card>

    <!-- 用户画像 -->
    <a-card class="section-card">
      <template #title>
        <div class="section-header">
          <span><UserOutlined /> 用户画像</span>
          <a-space>
            <template v-if="editing">
              <a-button size="small" @click="cancelEdit">取消</a-button>
              <a-button size="small" type="primary" @click="saveProfile">
                <SaveOutlined /> 保存
              </a-button>
            </template>
            <template v-else>
              <a-button size="small" :disabled="!hasProfile" @click="resetProfile">
                <ReloadOutlined /> 重置手动覆盖
              </a-button>
              <a-button size="small" type="primary" :disabled="!hasProfile" @click="startEdit">
                <EditOutlined /> 编辑
              </a-button>
            </template>
          </a-space>
        </div>
      </template>
      <p class="section-desc">系统根据你的对话自动构建，可手动覆盖。手动值优先于自动提取。</p>

      <a-spin :spinning="profileLoading">
        <a-alert
          v-if="profileError"
          type="error"
          :message="profileError"
          show-icon
          style="margin-bottom: 16px"
        />

        <a-empty
          v-if="!profileLoading && !profileError && !hasProfile"
          description="暂无画像信息，开始对话后系统会自动构建"
        />

        <div v-if="hasProfile || editing" class="profile-grid">
          <!-- 角色背景 -->
          <div class="profile-field">
            <div class="field-label">
              角色背景
              <a-tag v-if="isManual('profile_summary')" color="orange" size="small">手动</a-tag>
              <a-tag v-else color="default" size="small">自动</a-tag>
            </div>
            <template v-if="editing">
              <a-input
                v-model:value="editForm.profile_summary"
                placeholder="描述你的角色和背景"
              />
              <a-button
                size="small"
                type="link"
                @click="toggleManualField('profile_summary')"
              >
                {{ isManual('profile_summary') ? '取消手动覆盖' : '标记为手动覆盖' }}
              </a-button>
            </template>
            <div v-else class="field-value">{{ profile?.profile_summary || '—' }}</div>
          </div>

          <!-- 专业水平 -->
          <div class="profile-field">
            <div class="field-label">
              专业水平
              <a-tag v-if="isManual('expertise_level')" color="orange" size="small">手动</a-tag>
              <a-tag v-else color="default" size="small">自动</a-tag>
            </div>
            <template v-if="editing">
              <a-select
                v-model:value="editForm.expertise_level"
                :options="expertiseOptions.map(o => ({ value: o, label: expertiseLabels[o] || o }))"
                style="width: 100%"
              />
              <a-button
                size="small"
                type="link"
                @click="toggleManualField('expertise_level')"
              >
                {{ isManual('expertise_level') ? '取消手动覆盖' : '标记为手动覆盖' }}
              </a-button>
            </template>
            <div v-else class="field-value">
              {{ expertiseLabels[profile?.expertise_level || ''] || profile?.expertise_level || '—' }}
            </div>
          </div>

          <!-- 详细程度 -->
          <div class="profile-field">
            <div class="field-label">
              详细程度
              <a-tag v-if="isManual('detail_preference')" color="orange" size="small">手动</a-tag>
              <a-tag v-else color="default" size="small">自动</a-tag>
            </div>
            <template v-if="editing">
              <a-select
                v-model:value="editForm.detail_preference"
                :options="detailOptions.map(o => ({ value: o, label: detailLabels[o] || o }))"
                style="width: 100%"
              />
              <a-button
                size="small"
                type="link"
                @click="toggleManualField('detail_preference')"
              >
                {{ isManual('detail_preference') ? '取消手动覆盖' : '标记为手动覆盖' }}
              </a-button>
            </template>
            <div v-else class="field-value">
              {{ detailLabels[profile?.detail_preference || ''] || profile?.detail_preference || '—' }}
            </div>
          </div>

          <!-- 风格偏好 -->
          <div class="profile-field">
            <div class="field-label">
              风格偏好
              <a-tag v-if="isManual('style_preference')" color="orange" size="small">手动</a-tag>
              <a-tag v-else color="default" size="small">自动</a-tag>
            </div>
            <template v-if="editing">
              <a-select
                v-model:value="editForm.style_preference"
                :options="styleOptions.map(o => ({ value: o, label: styleLabels[o] || o }))"
                style="width: 100%"
              />
              <a-button
                size="small"
                type="link"
                @click="toggleManualField('style_preference')"
              >
                {{ isManual('style_preference') ? '取消手动覆盖' : '标记为手动覆盖' }}
              </a-button>
            </template>
            <div v-else class="field-value">
              {{ styleLabels[profile?.style_preference || ''] || profile?.style_preference || '—' }}
            </div>
          </div>
        </div>
      </a-spin>
    </a-card>
  </div>
</template>

<style lang="less" scoped>
.knowledge-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.section-card {
  border-radius: 12px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.section-desc {
  color: #7a8798;
  font-size: 13px;
  margin-bottom: 16px;
}

.upload-items {
  margin-bottom: 16px;
}

.upload-item {
  align-items: center;
  border-radius: 8px;
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 6px;
  background: #f5f7fb;
}

.upload-item.success {
  background: #eaf7ee;
}

.upload-item.error {
  background: #fff0f0;
}

.upload-name {
  font-weight: 500;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upload-error {
  color: #c32f35;
  font-size: 12px;
}

.doc-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.doc-item {
  align-items: center;
  border: 1px solid #e8edf4;
  border-radius: 8px;
  display: flex;
  justify-content: space-between;
  padding: 10px 14px;
  transition: border-color 0.2s;
}

.doc-item:hover {
  border-color: #2356f6;
}

.doc-info {
  align-items: center;
  display: flex;
  gap: 10px;
  min-width: 0;
}

.doc-icon {
  color: #2356f6;
}

.doc-name {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-time {
  color: #7a8798;
  font-size: 12px;
}

.profile-grid {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.profile-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-label {
  align-items: center;
  color: #263244;
  display: flex;
  font-weight: 600;
  gap: 8px;
}

.field-value {
  color: #4a5568;
  font-size: 14px;
  padding: 8px 12px;
  background: #f5f7fb;
  border-radius: 6px;
}
</style>
