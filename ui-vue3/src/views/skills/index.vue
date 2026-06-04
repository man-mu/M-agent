<script setup lang="ts">
import { computed, getCurrentInstance, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Form from 'ant-design-vue/es/form'
import Modal from 'ant-design-vue/es/modal'
import Table from 'ant-design-vue/es/table'
import message from 'ant-design-vue/es/message'
import {
  ArrowLeftOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  PlusOutlined,
  PoweroffOutlined,
  ReloadOutlined,
  UploadOutlined,
} from '@ant-design/icons-vue'
import appService from '@/services/api/app'
import skillService from '@/services/api/skills'
import type { CreateSkillRequest, SkillDefinition } from '@/services/api/skills'
import { userMessageFromError } from '@/utils/errors'
import {
  defaultParametersSchema,
  deriveExampleParameters,
  formatDependencies,
  normalizeDependencies,
  parameterCount,
  parseJsonObject,
  prettyJson,
  renderPromptPreview,
  validateSkillName,
} from './skillForm'

type StatusFilter = 'all' | 'enabled' | 'disabled'

const router = useRouter()
const app = getCurrentInstance()?.appContext.app
if (app) {
  if (!app.component('AForm')) app.use(Form)
  if (!app.component('AModal')) app.use(Modal)
  if (!app.component('ATable')) app.use(Table)
}

const skills = ref<SkillDefinition[]>([])
const capabilityLoading = ref(true)
const skillEnabled = ref(false)
const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const packageBusyName = ref('')
const loadError = ref('')
const formError = ref('')
const modalVisible = ref(false)
const editingName = ref('')
const packageInput = ref<HTMLInputElement | null>(null)
const keyword = ref('')
const statusFilter = ref<StatusFilter>('all')
const dependenciesText = ref('')
const parametersText = ref(prettyJson(defaultParametersSchema()))
const previewParamsText = ref('{}')

const form = reactive<CreateSkillRequest>({
  definition: {
    name: '',
    description: '',
    version: '1.0.0',
    enabled: true,
    dependencies: [],
    parameters: defaultParametersSchema(),
  },
  promptTemplate: '',
})

const columns = [
  { title: '名称', dataIndex: 'name', key: 'name', width: 180 },
  { title: '描述', dataIndex: 'description', key: 'description', width: 260 },
  { title: '版本', dataIndex: 'version', key: 'version', width: 110 },
  { title: '状态', key: 'enabled', width: 96 },
  { title: '依赖', key: 'dependencies', width: 180 },
  { title: '参数', key: 'parameters', width: 90 },
  { title: '来源', key: 'source', width: 110 },
  { title: '操作', key: 'actions', width: 280, fixed: 'right' },
] as const

const statusOptions = [
  { label: '全部', value: 'all' },
  { label: '启用', value: 'enabled' },
  { label: '停用', value: 'disabled' },
]

const totalCount = computed(() => skills.value.length)
const enabledCount = computed(() => skills.value.filter(skill => skill.enabled).length)
const disabledCount = computed(() => totalCount.value - enabledCount.value)
const moduleStatus = computed(() => {
  if (capabilityLoading.value) {
    return { label: '加载中', color: 'processing' }
  }
  return skillEnabled.value
    ? { label: '已启用', color: 'green' }
    : { label: '未启用', color: 'default' }
})
const filteredSkills = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return skills.value.filter(skill => {
    if (statusFilter.value === 'enabled' && !skill.enabled) {
      return false
    }
    if (statusFilter.value === 'disabled' && skill.enabled) {
      return false
    }
    if (!query) {
      return true
    }
    const searchable = [
      skill.name,
      skill.description,
      skill.version,
      skill.source,
      skill.storageLocation,
      ...(skill.dependencies || []),
    ].join(' ').toLowerCase()
    return searchable.includes(query)
  })
})

const parsedParameters = computed(() => parseJsonObject(parametersText.value))
const parsedPreviewParams = computed(() => parseJsonObject(previewParamsText.value))
const parametersError = computed(() => parsedParameters.value.ok ? '' : parsedParameters.value.error)
const previewParamsError = computed(() => parsedPreviewParams.value.ok ? '' : parsedPreviewParams.value.error)
const preview = computed(() => {
  const params = parsedPreviewParams.value.ok ? parsedPreviewParams.value.value : {}
  return renderPromptPreview(form.promptTemplate, params)
})
const modalTitle = computed(() => editingName.value ? '编辑 Skill' : '新建 Skill')

watch(parametersText, value => {
  const parsed = parseJsonObject(value)
  if (parsed.ok) {
    if (formError.value === 'JSON 格式错误，请检查逗号、引号和括号。') {
      formError.value = ''
    }
    previewParamsText.value = prettyJson(deriveExampleParameters(parsed.value))
  }
})

async function loadSkills() {
  if (!skillEnabled.value) {
    skills.value = []
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    skills.value = await skillService.list()
  } catch (err: unknown) {
    loadError.value = userMessageFromError(err, '加载 Skill 列表失败')
    message.error(loadError.value)
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editingName.value = ''
  formError.value = ''
  form.definition = {
    name: '',
    description: '',
    version: '1.0.0',
    enabled: true,
    dependencies: [],
    parameters: defaultParametersSchema(),
  }
  form.promptTemplate = ''
  dependenciesText.value = ''
  parametersText.value = prettyJson(defaultParametersSchema())
  previewParamsText.value = '{}'
}

function openCreate() {
  resetForm()
  modalVisible.value = true
}

async function openEdit(name: string) {
  formError.value = ''
  try {
    const detail = await skillService.get(name)
    editingName.value = name
    form.definition = {
      ...detail.definition,
      dependencies: detail.definition.dependencies || [],
      parameters: detail.definition.parameters || defaultParametersSchema(),
    }
    form.promptTemplate = detail.promptTemplate || ''
    dependenciesText.value = formatDependencies(form.definition.dependencies)
    parametersText.value = prettyJson(form.definition.parameters)
    previewParamsText.value = prettyJson(deriveExampleParameters(form.definition.parameters || {}))
    modalVisible.value = true
  } catch (err: unknown) {
    message.error(userMessageFromError(err, '读取 Skill 详情失败'))
  }
}

async function saveSkill() {
  formError.value = ''
  const name = form.definition.name.trim()
  const nameError = validateSkillName(name)
  if (nameError) {
    formError.value = nameError
    message.warning(nameError)
    return
  }
  const description = form.definition.description.trim()
  if (!description) {
    formError.value = 'Skill 描述不能为空。'
    message.warning(formError.value)
    return
  }
  if (!parsedParameters.value.ok) {
    formError.value = parsedParameters.value.error
    message.warning(formError.value)
    return
  }

  const request: CreateSkillRequest = {
    definition: {
      name,
      description,
      version: form.definition.version?.trim() || '1.0.0',
      enabled: form.definition.enabled,
      dependencies: normalizeDependencies(dependenciesText.value),
      parameters: parsedParameters.value.value,
    },
    promptTemplate: form.promptTemplate,
  }

  saving.value = true
  try {
    if (editingName.value) {
      await skillService.update(editingName.value, request)
      message.success('Skill 已更新')
    } else {
      await skillService.create(request)
      message.success('Skill 已创建')
    }
    modalVisible.value = false
    await loadSkills()
  } catch (err: unknown) {
    formError.value = userMessageFromError(err, '保存 Skill 失败')
    message.error(formError.value)
  } finally {
    saving.value = false
  }
}

async function toggleSkill(name: string) {
  try {
    await skillService.toggle(name)
    await loadSkills()
  } catch (err: unknown) {
    message.error(userMessageFromError(err, '切换 Skill 状态失败'))
  }
}

function confirmDelete(skill: SkillDefinition) {
  Modal.confirm({
    title: `删除 Skill：${skill.name}`,
    content: '删除后会移除对应的 Skill 文件，无法从页面恢复。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await skillService.delete(skill.name)
        message.success('Skill 已删除')
        await loadSkills()
      } catch (err: unknown) {
        message.error(userMessageFromError(err, '删除 Skill 失败'))
      }
    },
  })
}

function isBuiltin(skill: SkillDefinition) {
  return skill.storageLocation === 'BUILTIN' || skill.source === 'builtin'
}

function sourceLabel(skill: SkillDefinition) {
  return isBuiltin(skill) ? '内置' : '本地'
}

function sourceColor(skill: SkillDefinition) {
  return isBuiltin(skill) ? 'blue' : 'green'
}

function openPackagePicker() {
  packageInput.value?.click()
}

async function handlePackageFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }
  if (!file.name.toLowerCase().endsWith('.zip')) {
    message.warning('请选择 .zip 格式的 Skill 包')
    return
  }
  importing.value = true
  try {
    const result = await skillService.importPackage(file)
    message.success(`Skill 包已导入：${result.name}`)
    await loadSkills()
  } catch (err: unknown) {
    message.error(userMessageFromError(err, '导入 Skill 包失败'))
  } finally {
    importing.value = false
  }
}

async function exportSkillPackage(skill: SkillDefinition) {
  packageBusyName.value = skill.name
  try {
    const blob = await skillService.exportPackage(skill.name)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${skill.name}-skill.zip`
    link.click()
    URL.revokeObjectURL(url)
    message.success('Skill 包已导出')
  } catch (err: unknown) {
    message.error(userMessageFromError(err, '导出 Skill 包失败'))
  } finally {
    packageBusyName.value = ''
  }
}

async function reloadSkill(skill: SkillDefinition) {
  packageBusyName.value = skill.name
  try {
    await skillService.reload(skill.name)
    message.success('Skill 已重载')
    await loadSkills()
  } catch (err: unknown) {
    message.error(userMessageFromError(err, '重载 Skill 失败'))
  } finally {
    packageBusyName.value = ''
  }
}

function confirmUninstall(skill: SkillDefinition) {
  Modal.confirm({
    title: `卸载 Skill 包：${skill.name}`,
    content: '卸载后会移除本地安装目录中的 Skill 包，并立即从工具列表中移除。',
    okText: '卸载',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      packageBusyName.value = skill.name
      try {
        await skillService.uninstallPackage(skill.name)
        message.success('Skill 包已卸载')
        await loadSkills()
      } catch (err: unknown) {
        message.error(userMessageFromError(err, '卸载 Skill 包失败'))
      } finally {
        packageBusyName.value = ''
      }
    },
  })
}

function dependenciesLabel(skill: SkillDefinition) {
  return skill.dependencies?.length ? skill.dependencies.join('、') : '无'
}

function statusLabel(skill: SkillDefinition) {
  return skill.enabled ? '启用' : '停用'
}

function promptPreviewText() {
  return preview.value.text || 'Prompt 模板为空。'
}

async function initialize() {
  capabilityLoading.value = true
  loadError.value = ''
  try {
    const capabilities = await appService.getCapabilities()
    skillEnabled.value = capabilities.skillEnabled
    if (capabilities.skillEnabled) {
      await loadSkills()
    }
  } catch (err: unknown) {
    skillEnabled.value = false
    loadError.value = userMessageFromError(err, '加载应用能力信息失败')
    message.error(loadError.value)
  } finally {
    capabilityLoading.value = false
  }
}

onMounted(initialize)
</script>

<template>
  <main class="skills-page" data-testid="skills-page">
    <div class="page-header">
      <div>
        <div class="eyebrow">Prompt Skills</div>
        <h1>Skill 管理</h1>
      </div>
      <a-space v-if="skillEnabled" class="header-actions">
        <input
          ref="packageInput"
          accept=".zip,application/zip"
          class="package-input"
          type="file"
          @change="handlePackageFileChange"
        />
        <a-tooltip title="刷新列表">
          <a-button :loading="loading" @click="loadSkills">
            <ReloadOutlined />
          </a-button>
        </a-tooltip>
        <a-button :loading="importing" @click="openPackagePicker">
          <UploadOutlined />
          导入 Skill 包
        </a-button>
        <a-button type="primary" @click="openCreate">
          <PlusOutlined />
          新建 Skill
        </a-button>
      </a-space>
    </div>

    <a-spin v-if="capabilityLoading" />

    <template v-else>
      <section class="summary-band" aria-label="Skill 状态">
        <div class="summary-item">
          <span>模块状态</span>
          <strong><a-tag :color="moduleStatus.color">{{ moduleStatus.label }}</a-tag></strong>
        </div>
        <div class="summary-item">
          <span>Skill 总数</span>
          <strong>{{ totalCount }}</strong>
        </div>
        <div class="summary-item">
          <span>已启用</span>
          <strong>{{ enabledCount }}</strong>
        </div>
        <div class="summary-item">
          <span>已停用</span>
          <strong>{{ disabledCount }}</strong>
        </div>
      </section>

      <a-alert
        v-if="loadError"
        class="page-alert"
        show-icon
        type="warning"
        :message="loadError"
      >
        <template #action>
          <a-button size="small" @click="initialize">重试</a-button>
        </template>
      </a-alert>

      <a-empty v-else-if="!skillEnabled" data-testid="skills-disabled" description="Skill 模块未启用">
        <a-button type="primary" @click="router.push('/chat')">
          <ArrowLeftOutlined />
          返回对话
        </a-button>
      </a-empty>

      <template v-else>
        <div class="toolbar">
          <a-input
            v-model:value="keyword"
            allow-clear
            class="search-input"
            placeholder="搜索名称、描述、版本或依赖"
          />
          <a-segmented v-model:value="statusFilter" :options="statusOptions" />
        </div>

        <a-table
          class="skill-table"
          :columns="columns"
          :data-source="filteredSkills"
          :loading="loading"
          :pagination="{ pageSize: 8, hideOnSinglePage: true }"
          :scroll="{ x: 1320 }"
          row-key="name"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
              <strong>{{ record.name }}</strong>
            </template>
            <template v-else-if="column.key === 'enabled'">
              <a-tag :color="record.enabled ? 'green' : 'default'">
                {{ statusLabel(record) }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'dependencies'">
              <span class="muted">{{ dependenciesLabel(record) }}</span>
            </template>
            <template v-else-if="column.key === 'parameters'">
              {{ parameterCount(record.parameters) }}
            </template>
            <template v-else-if="column.key === 'source'">
              <a-tag :color="sourceColor(record)">
                {{ sourceLabel(record) }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space wrap>
                <a-tooltip title="编辑">
                  <a-button size="small" :disabled="isBuiltin(record)" @click="openEdit(record.name)">
                    <EditOutlined />
                  </a-button>
                </a-tooltip>
                <a-tooltip :title="record.enabled ? '停用' : '启用'">
                  <a-button
                    size="small"
                    :danger="record.enabled"
                    :disabled="isBuiltin(record)"
                    @click="toggleSkill(record.name)"
                  >
                    <PoweroffOutlined />
                  </a-button>
                </a-tooltip>
                <a-tooltip :title="isBuiltin(record) ? '内置 Skill 不能导出为本地包' : '导出'">
                  <a-button
                    size="small"
                    :disabled="isBuiltin(record)"
                    :loading="packageBusyName === record.name"
                    @click="exportSkillPackage(record)"
                  >
                    <DownloadOutlined />
                  </a-button>
                </a-tooltip>
                <a-tooltip :title="isBuiltin(record) ? '内置 Skill 随服务启动加载' : '重载'">
                  <a-button
                    size="small"
                    :disabled="isBuiltin(record)"
                    :loading="packageBusyName === record.name"
                    @click="reloadSkill(record)"
                  >
                    <ReloadOutlined />
                  </a-button>
                </a-tooltip>
                <a-tooltip :title="isBuiltin(record) ? '内置 Skill 不能卸载' : '卸载'">
                  <a-button
                    size="small"
                    danger
                    :disabled="isBuiltin(record)"
                    :loading="packageBusyName === record.name"
                    @click="confirmUninstall(record)"
                  >
                    <DeleteOutlined />
                  </a-button>
                </a-tooltip>
              </a-space>
            </template>
          </template>
        </a-table>

        <div class="mobile-skill-list">
          <article v-for="skill in filteredSkills" :key="skill.name" class="skill-card">
            <div class="skill-card-header">
              <div>
                <strong>{{ skill.name }}</strong>
                <p>{{ skill.description }}</p>
              </div>
              <a-tag :color="skill.enabled ? 'green' : 'default'">{{ statusLabel(skill) }}</a-tag>
            </div>
            <dl>
              <div>
                <dt>版本</dt>
                <dd>{{ skill.version || '1.0.0' }}</dd>
              </div>
              <div>
                <dt>依赖</dt>
                <dd>{{ dependenciesLabel(skill) }}</dd>
              </div>
              <div>
                <dt>参数</dt>
                <dd>{{ parameterCount(skill.parameters) }}</dd>
              </div>
              <div>
                <dt>来源</dt>
                <dd>
                  <a-tag :color="sourceColor(skill)">
                    {{ sourceLabel(skill) }}
                  </a-tag>
                </dd>
              </div>
            </dl>
            <a-space class="card-actions">
              <a-button size="small" :disabled="isBuiltin(skill)" @click="openEdit(skill.name)">
                <EditOutlined />
                编辑
              </a-button>
              <a-button
                size="small"
                :danger="skill.enabled"
                :disabled="isBuiltin(skill)"
                @click="toggleSkill(skill.name)"
              >
                <PoweroffOutlined />
                {{ skill.enabled ? '停用' : '启用' }}
              </a-button>
              <a-button
                size="small"
                :disabled="isBuiltin(skill)"
                :loading="packageBusyName === skill.name"
                @click="exportSkillPackage(skill)"
              >
                <DownloadOutlined />
                导出
              </a-button>
              <a-button
                size="small"
                :disabled="isBuiltin(skill)"
                :loading="packageBusyName === skill.name"
                @click="reloadSkill(skill)"
              >
                <ReloadOutlined />
                重载
              </a-button>
              <a-button
                size="small"
                danger
                :disabled="isBuiltin(skill)"
                :loading="packageBusyName === skill.name"
                @click="confirmUninstall(skill)"
              >
                <DeleteOutlined />
                卸载
              </a-button>
            </a-space>
          </article>
          <a-empty v-if="!filteredSkills.length && !loading" description="没有匹配的 Skill" />
        </div>
      </template>
    </template>

    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :confirm-loading="saving"
      width="880px"
      @ok="saveSkill"
    >
      <a-alert
        v-if="formError"
        class="modal-alert"
        show-icon
        type="warning"
        :message="formError"
      />
      <a-form layout="vertical">
        <div class="form-grid">
          <a-form-item label="名称" required>
            <a-input
              v-model:value="form.definition.name"
              :disabled="Boolean(editingName)"
              placeholder="例如 code-review"
            />
          </a-form-item>
          <a-form-item label="版本">
            <a-input v-model:value="form.definition.version" placeholder="例如 1.0.0" />
          </a-form-item>
        </div>
        <a-form-item label="描述" required>
          <a-input v-model:value="form.definition.description" placeholder="这个 Skill 解决什么问题" />
        </a-form-item>
        <a-form-item label="依赖">
          <a-textarea
            v-model:value="dependenciesText"
            :rows="3"
            placeholder="每行一个依赖，也可以用逗号分隔"
          />
        </a-form-item>
        <a-form-item
          label="参数 JSON Schema"
          :validate-status="parametersError ? 'error' : ''"
          :help="parametersError || '用于定义 {{param}} 可用参数和默认示例值。'"
        >
          <a-textarea
            v-model:value="parametersText"
            class="code-editor"
            :rows="9"
            spellcheck="false"
          />
        </a-form-item>
        <a-form-item label="Prompt 模板">
          <a-textarea
            v-model:value="form.promptTemplate"
            class="code-editor"
            :rows="8"
            placeholder="可使用 {{param}} 作为参数占位符"
            spellcheck="false"
          />
        </a-form-item>
        <a-form-item label="启用">
          <a-switch v-model:checked="form.definition.enabled" />
        </a-form-item>
      </a-form>

      <section class="preview-panel" aria-label="Prompt 预览">
        <div class="preview-head">
          <strong>Prompt 预览</strong>
          <a-space v-if="preview.missing.length" wrap>
            <a-tag v-for="name in preview.missing" :key="name" color="orange">
              缺少 {{ name }}
            </a-tag>
          </a-space>
        </div>
        <a-form layout="vertical">
          <a-form-item
            label="示例参数"
            :validate-status="previewParamsError ? 'error' : ''"
            :help="previewParamsError || '会根据 required、properties.default 和类型自动生成，可手动调整。'"
          >
            <a-textarea
              v-model:value="previewParamsText"
              class="code-editor"
              :rows="5"
              spellcheck="false"
            />
          </a-form-item>
        </a-form>
        <pre>{{ promptPreviewText() }}</pre>
      </section>
    </a-modal>
  </main>
</template>

<style lang="less" scoped>
.skills-page {
  height: calc(100vh - 56px);
  overflow: auto;
  padding: 24px;
}

.page-header {
  align-items: center;
  display: flex;
  gap: 16px;
  justify-content: space-between;
  margin-bottom: 18px;
}

.page-header h1 {
  font-size: 24px;
  letter-spacing: 0;
  margin: 2px 0 0;
}

.header-actions {
  flex-shrink: 0;
  flex-wrap: wrap;
}

.package-input {
  display: none;
}

.eyebrow {
  color: #738096;
  font-size: 12px;
  letter-spacing: 0;
  text-transform: uppercase;
}

.summary-band {
  background: #f6f8fb;
  border: 1px solid #e7edf5;
  border-radius: 8px;
  display: grid;
  gap: 1px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 16px;
  overflow: hidden;
}

.summary-item {
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  padding: 14px 16px;
}

.summary-item span {
  color: #6b7280;
  font-size: 13px;
}

.summary-item strong {
  color: #111827;
  font-size: 22px;
  line-height: 1.2;
}

.page-alert,
.modal-alert {
  margin-bottom: 16px;
}

.toolbar {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  margin-bottom: 14px;
}

.search-input {
  max-width: 360px;
}

.muted {
  color: #6b7280;
}

.skill-table {
  :deep(.ant-table-cell) {
    vertical-align: top;
  }
}

.mobile-skill-list {
  display: none;
}

.form-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(0, 1fr) 160px;
}

.code-editor {
  font-family: Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
}

.preview-panel {
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  margin-top: 8px;
  padding: 14px;
}

.preview-head {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  margin-bottom: 12px;
}

.preview-panel pre {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  color: #111827;
  margin: 0;
  max-height: 220px;
  overflow: auto;
  padding: 12px;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 720px) {
  .skills-page {
    padding: 16px;
  }

  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-actions,
  .toolbar {
    width: 100%;
  }

  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .search-input {
    max-width: none;
  }

  .summary-band {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .summary-item {
    padding: 12px;
  }

  .summary-item strong {
    font-size: 20px;
  }

  .skill-table {
    display: none;
  }

  .mobile-skill-list {
    display: grid;
    gap: 12px;
  }

  .skill-card {
    border: 1px solid #e5e7eb;
    border-radius: 8px;
    padding: 14px;
  }

  .skill-card-header {
    align-items: flex-start;
    display: flex;
    gap: 12px;
    justify-content: space-between;
  }

  .skill-card-header p {
    color: #4b5563;
    margin: 6px 0 0;
  }

  .skill-card dl {
    display: grid;
    gap: 8px;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    margin: 14px 0;
  }

  .skill-card dt {
    color: #6b7280;
    font-size: 12px;
  }

  .skill-card dd {
    margin: 4px 0 0;
    overflow-wrap: anywhere;
  }

  .card-actions {
    flex-wrap: wrap;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .preview-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
