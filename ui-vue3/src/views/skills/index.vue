<script setup lang="ts">
import { computed, getCurrentInstance, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import Descriptions from 'ant-design-vue/es/descriptions'
import Drawer from 'ant-design-vue/es/drawer'
import Form from 'ant-design-vue/es/form'
import Modal from 'ant-design-vue/es/modal'
import Table from 'ant-design-vue/es/table'
import Tabs from 'ant-design-vue/es/tabs'
import Upload from 'ant-design-vue/es/upload'
import message from 'ant-design-vue/es/message'
import {
  ArrowLeftOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  EyeOutlined,
  InboxOutlined,
  PlusOutlined,
  PoweroffOutlined,
  ReloadOutlined,
  UploadOutlined,
} from '@ant-design/icons-vue'
import appService from '@/services/api/app'
import skillService from '@/services/api/skills'
import type { CreateSkillRequest, SkillDefinition, SkillDetail, SkillPackageImportResult } from '@/services/api/skills'
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
  validateHomepage,
  validateSkillName,
} from './skillForm'
import {
  filterSkillMarket,
  isBuiltinSkill as isBuiltin,
  skillCategoryLabel as categoryLabel,
  skillDisplayTitle as skillTitle,
  skillListLabel,
  skillPackageTypeLabel as packageTypeLabel,
  skillSourceColor as sourceColor,
  skillSourceLabel as sourceLabel,
  skillStatusLabel as statusLabel,
  skillStorageLabel as storageLabel,
} from './skillMarket'

type StatusFilter = 'all' | 'enabled' | 'disabled'
type ActiveTab = 'installed' | 'market' | 'imports'

interface ImportRecord {
  id: string
  fileName: string
  name: string
  version?: string
  status: 'success' | 'failed'
  message: string
  importedAt: string
}

const router = useRouter()
const app = getCurrentInstance()?.appContext.app
if (app) {
  if (!app.component('ADescriptions')) app.use(Descriptions)
  if (!app.component('ADrawer')) app.use(Drawer)
  if (!app.component('AForm')) app.use(Form)
  if (!app.component('AModal')) app.use(Modal)
  if (!app.component('ATable')) app.use(Table)
  if (!app.component('ATabs')) app.use(Tabs)
  if (!app.component('AUpload')) app.use(Upload)
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
const keyword = ref('')
const statusFilter = ref<StatusFilter>('all')
const activeTab = ref<ActiveTab>('installed')
const dependenciesText = ref('')
const tagsText = ref('')
const parametersText = ref(prettyJson(defaultParametersSchema()))
const previewParamsText = ref('{}')
const detailVisible = ref(false)
const detailLoading = ref(false)
const selectedSkill = ref<SkillDefinition | null>(null)
const selectedSkillDetail = ref<SkillDetail | null>(null)
const importRecords = ref<ImportRecord[]>([])

const form = reactive<CreateSkillRequest>({
  definition: {
    name: '',
    description: '',
    version: '1.0.0',
    enabled: true,
    dependencies: [],
    tags: [],
    parameters: defaultParametersSchema(),
    packageType: 'PROMPT',
  },
  promptTemplate: '',
})

const columns = [
  { title: 'Skill', key: 'name', width: 220 },
  { title: '描述', dataIndex: 'description', key: 'description', width: 280 },
  { title: '版本', dataIndex: 'version', key: 'version', width: 110 },
  { title: '状态', key: 'enabled', width: 96 },
  { title: '分类', key: 'category', width: 120 },
  { title: '依赖', key: 'dependencies', width: 180 },
  { title: '参数', key: 'parameters', width: 90 },
  { title: '来源', key: 'source', width: 110 },
  { title: '操作', key: 'actions', width: 336, fixed: 'right' },
] as const

const importColumns = [
  { title: '时间', key: 'importedAt', width: 180 },
  { title: '包文件', dataIndex: 'fileName', key: 'fileName', width: 220 },
  { title: 'Skill', key: 'name', width: 190 },
  { title: '结果', key: 'status', width: 110 },
  { title: '说明', dataIndex: 'message', key: 'message' },
] as const

const statusOptions = [
  { label: '全部', value: 'all' },
  { label: '启用', value: 'enabled' },
  { label: '停用', value: 'disabled' },
]

const totalCount = computed(() => skills.value.length)
const enabledCount = computed(() => skills.value.filter(skill => skill.enabled).length)
const disabledCount = computed(() => totalCount.value - enabledCount.value)
const builtinCount = computed(() => skills.value.filter(isBuiltin).length)
const localCount = computed(() => skills.value.filter(skill => !isBuiltin(skill)).length)
const moduleStatus = computed(() => {
  if (capabilityLoading.value) {
    return { label: '加载中', color: 'processing' }
  }
  return skillEnabled.value
    ? { label: '已启用', color: 'green' }
    : { label: '未启用', color: 'default' }
})
const filteredSkills = computed(() => {
  return filterSkillMarket(skills.value, {
    tab: activeTab.value === 'market' ? 'market' : 'installed',
    keyword: keyword.value,
    status: statusFilter.value,
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
    displayName: '',
    category: '',
    author: '',
    homepage: '',
    tags: [],
    packageType: 'PROMPT',
    parameters: defaultParametersSchema(),
  }
  form.promptTemplate = ''
  dependenciesText.value = ''
  tagsText.value = ''
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
      tags: detail.definition.tags || [],
      parameters: detail.definition.parameters || defaultParametersSchema(),
    }
    form.promptTemplate = detail.promptTemplate || ''
    dependenciesText.value = formatDependencies(form.definition.dependencies)
    tagsText.value = formatDependencies(form.definition.tags)
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
  const homepage = form.definition.homepage?.trim()
  const homepageError = validateHomepage(homepage)
  if (homepageError) {
    formError.value = homepageError
    message.warning(homepageError)
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
      displayName: form.definition.displayName?.trim() || undefined,
      category: form.definition.category?.trim() || undefined,
      author: form.definition.author?.trim() || undefined,
      homepage: homepage || undefined,
      tags: normalizeDependencies(tagsText.value),
      packageType: form.definition.packageType || 'PROMPT',
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

function tagsLabel(skill: SkillDefinition) {
  return skillListLabel(skill.tags)
}

function formatTime(value?: string) {
  if (!value) {
    return '未记录'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString()
}

function beforePackageUpload(file: File) {
  void importPackageFile(file)
  return false
}

async function importPackageFile(file: File) {
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
    addImportRecord(file, result)
    message.success(`Skill 包已导入：${result.name}`)
    await loadSkills()
    activeTab.value = 'market'
  } catch (err: unknown) {
    const errorMessage = userMessageFromError(err, '导入 Skill 包失败')
    addImportRecord(file, null, errorMessage)
    message.error(errorMessage)
  } finally {
    importing.value = false
  }
}

function addImportRecord(file: File, result: SkillPackageImportResult | null, errorMessage = '') {
  importRecords.value.unshift({
    id: `${Date.now()}-${file.name}`,
    fileName: file.name,
    name: result?.name || '-',
    version: result?.version,
    status: result ? 'success' : 'failed',
    message: result?.message || errorMessage,
    importedAt: new Date().toISOString(),
  })
}

async function openDetail(skill: SkillDefinition) {
  selectedSkill.value = skill
  selectedSkillDetail.value = null
  detailVisible.value = true
  detailLoading.value = true
  try {
    selectedSkillDetail.value = await skillService.get(skill.name)
  } catch (err: unknown) {
    message.error(userMessageFromError(err, '读取 Skill 详情失败'))
  } finally {
    detailLoading.value = false
  }
}

const detailDefinition = computed(() => selectedSkillDetail.value?.definition || selectedSkill.value)
const detailPromptTemplate = computed(() => selectedSkillDetail.value?.promptTemplate || '')
const detailParametersText = computed(() => prettyJson(detailDefinition.value?.parameters || defaultParametersSchema()))
const detailDependencies = computed(() => detailDefinition.value?.dependencies || [])
const detailTags = computed(() => detailDefinition.value?.tags || [])

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
  return skillListLabel(skill.dependencies)
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
        <div class="eyebrow">Skill Market</div>
        <h1>Skill 市场</h1>
      </div>
      <a-space v-if="skillEnabled" class="header-actions">
        <a-tooltip title="刷新列表">
          <a-button :loading="loading" @click="loadSkills">
            <ReloadOutlined />
          </a-button>
        </a-tooltip>
        <a-upload
          accept=".zip,application/zip"
          :before-upload="beforePackageUpload"
          :disabled="importing"
          :show-upload-list="false"
        >
          <a-button :loading="importing">
            <UploadOutlined />
            导入 Skill 包
          </a-button>
        </a-upload>
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
          <span>本地安装</span>
          <strong>{{ localCount }}</strong>
        </div>
        <div class="summary-item">
          <span>内置 Skill</span>
          <strong>{{ builtinCount }}</strong>
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
        <a-tabs v-model:active-key="activeTab" class="market-tabs">
          <a-tab-pane key="installed" :tab="`已安装 ${totalCount}`" />
          <a-tab-pane key="market" :tab="`本地市场 ${localCount}`" />
          <a-tab-pane key="imports" :tab="`导入记录 ${importRecords.length}`" />
        </a-tabs>

        <section v-if="activeTab === 'market'" class="upload-panel">
          <a-upload-dragger
            accept=".zip,application/zip"
            :before-upload="beforePackageUpload"
            :disabled="importing"
            :show-upload-list="false"
          >
            <p class="upload-icon"><InboxOutlined /></p>
            <p class="upload-title">Prompt Skill Zip</p>
          </a-upload-dragger>
        </section>

        <template v-if="activeTab !== 'imports'">
          <div class="toolbar">
            <a-input
              v-model:value="keyword"
              allow-clear
              class="search-input"
              placeholder="搜索名称、分类、版本或依赖"
            />
            <a-segmented v-model:value="statusFilter" :options="statusOptions" />
          </div>

          <a-table
            class="skill-table"
            :columns="columns"
            :data-source="filteredSkills"
            :loading="loading"
            :pagination="{ pageSize: 8, hideOnSinglePage: true }"
            :scroll="{ x: 1440 }"
            row-key="name"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'name'">
                <div class="skill-name-cell">
                  <strong>{{ skillTitle(record) }}</strong>
                  <span>{{ record.name }}</span>
                  <div class="inline-tags">
                    <a-tag :color="sourceColor(record)">{{ sourceLabel(record) }}</a-tag>
                    <a-tag>{{ packageTypeLabel(record) }}</a-tag>
                  </div>
                </div>
              </template>
              <template v-else-if="column.key === 'enabled'">
                <a-tag :color="record.enabled ? 'green' : 'default'">
                  {{ statusLabel(record) }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'category'">
                <span class="muted">{{ categoryLabel(record) }}</span>
              </template>
              <template v-else-if="column.key === 'dependencies'">
                <span class="muted">{{ dependenciesLabel(record) }}</span>
              </template>
              <template v-else-if="column.key === 'parameters'">
                {{ parameterCount(record.parameters) }}
              </template>
              <template v-else-if="column.key === 'source'">
                <span class="muted">{{ storageLabel(record) }}</span>
              </template>
              <template v-else-if="column.key === 'actions'">
                <a-space wrap>
                  <a-tooltip title="详情">
                    <a-button size="small" @click="openDetail(record)">
                      <EyeOutlined />
                    </a-button>
                  </a-tooltip>
                  <a-tooltip :title="isBuiltin(record) ? '内置 Skill 只读' : '编辑'">
                    <a-button size="small" :disabled="isBuiltin(record)" @click="openEdit(record.name)">
                      <EditOutlined />
                    </a-button>
                  </a-tooltip>
                  <a-tooltip :title="isBuiltin(record) ? '内置 Skill 由服务管理' : (record.enabled ? '停用' : '启用')">
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
                  <strong>{{ skillTitle(skill) }}</strong>
                  <span>{{ skill.name }}</span>
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
                  <dt>分类</dt>
                  <dd>{{ categoryLabel(skill) }}</dd>
                </div>
                <div>
                  <dt>依赖</dt>
                  <dd>{{ dependenciesLabel(skill) }}</dd>
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
                <a-button size="small" @click="openDetail(skill)">
                  <EyeOutlined />
                  详情
                </a-button>
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

        <a-table
          v-else
          class="import-table"
          :columns="importColumns"
          :data-source="importRecords"
          :pagination="{ pageSize: 8, hideOnSinglePage: true }"
          :scroll="{ x: 820 }"
          row-key="id"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'importedAt'">
              {{ formatTime(record.importedAt) }}
            </template>
            <template v-else-if="column.key === 'name'">
              <strong>{{ record.name }}</strong>
              <span v-if="record.version" class="record-version">v{{ record.version }}</span>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="record.status === 'success' ? 'green' : 'red'">
                {{ record.status === 'success' ? '成功' : '失败' }}
              </a-tag>
            </template>
          </template>
        </a-table>
      </template>
    </template>

    <a-drawer
      v-model:open="detailVisible"
      class="skill-drawer"
      placement="right"
      width="560"
      :title="detailDefinition ? skillTitle(detailDefinition) : 'Skill 详情'"
    >
      <a-spin :spinning="detailLoading">
        <template v-if="detailDefinition">
          <a-descriptions bordered size="small" :column="1">
            <a-descriptions-item label="名称">{{ detailDefinition.name }}</a-descriptions-item>
            <a-descriptions-item label="显示名">{{ detailDefinition.displayName || '未设置' }}</a-descriptions-item>
            <a-descriptions-item label="描述">{{ detailDefinition.description }}</a-descriptions-item>
            <a-descriptions-item label="版本">{{ detailDefinition.version || '1.0.0' }}</a-descriptions-item>
            <a-descriptions-item label="分类">{{ categoryLabel(detailDefinition) }}</a-descriptions-item>
            <a-descriptions-item label="作者">{{ detailDefinition.author || '未记录' }}</a-descriptions-item>
            <a-descriptions-item label="来源">
              <a-tag :color="sourceColor(detailDefinition)">{{ sourceLabel(detailDefinition) }}</a-tag>
              <a-tag>{{ storageLabel(detailDefinition) }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="状态">
              <a-tag :color="detailDefinition.enabled ? 'green' : 'default'">
                {{ statusLabel(detailDefinition) }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="安装时间">{{ formatTime(detailDefinition.installed_at || detailDefinition.created_at) }}</a-descriptions-item>
            <a-descriptions-item label="更新时间">{{ formatTime(detailDefinition.updated_at) }}</a-descriptions-item>
            <a-descriptions-item label="依赖">{{ detailDependencies.length ? detailDependencies.join('、') : '无' }}</a-descriptions-item>
            <a-descriptions-item label="标签">{{ detailTags.length ? detailTags.join('、') : tagsLabel(detailDefinition) }}</a-descriptions-item>
          </a-descriptions>

          <section class="drawer-section">
            <strong>参数 Schema</strong>
            <pre>{{ detailParametersText }}</pre>
          </section>

          <section class="drawer-section">
            <strong>Prompt 预览</strong>
            <pre>{{ detailPromptTemplate || 'Prompt 模板为空。' }}</pre>
          </section>
        </template>
      </a-spin>
    </a-drawer>

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
        <div class="form-grid metadata-grid">
          <a-form-item label="显示名">
            <a-input v-model:value="form.definition.displayName" placeholder="例如 代码审查助手" />
          </a-form-item>
          <a-form-item label="分类">
            <a-input v-model:value="form.definition.category" placeholder="例如 engineering" />
          </a-form-item>
          <a-form-item label="作者">
            <a-input v-model:value="form.definition.author" placeholder="作者或团队" />
          </a-form-item>
          <a-form-item label="主页">
            <a-input v-model:value="form.definition.homepage" placeholder="https://example.com" />
          </a-form-item>
        </div>
        <a-form-item label="描述" required>
          <a-input v-model:value="form.definition.description" placeholder="这个 Skill 解决什么问题" />
        </a-form-item>
        <a-form-item label="标签">
          <a-textarea
            v-model:value="tagsText"
            :rows="2"
            placeholder="每行一个标签，也可以用逗号分隔"
          />
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
  grid-template-columns: repeat(6, minmax(0, 1fr));
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

.market-tabs {
  margin-bottom: 8px;
}

.upload-panel {
  margin-bottom: 14px;
}

.upload-panel :deep(.ant-upload-drag) {
  background: #f8fafc;
  border-color: #d8e0ea;
  border-radius: 8px;
}

.upload-icon {
  color: #2356f6;
  font-size: 28px;
  line-height: 1;
  margin: 0 0 8px;
}

.upload-title {
  color: #111827;
  font-weight: 600;
  margin: 0;
}

.skill-table {
  :deep(.ant-table-cell) {
    vertical-align: top;
  }
}

.skill-name-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.skill-name-cell strong,
.skill-card-header strong {
  color: #111827;
  overflow-wrap: anywhere;
}

.skill-name-cell span,
.skill-card-header span,
.record-version {
  color: #6b7280;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.inline-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.import-table {
  :deep(.ant-table-cell) {
    vertical-align: top;
  }
}

.record-version {
  display: block;
  margin-top: 2px;
}

.mobile-skill-list {
  display: none;
}

.form-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(0, 1fr) 160px;
}

.metadata-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.code-editor {
  font-family: Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
}

.skill-drawer {
  :deep(.ant-drawer-content-wrapper) {
    max-width: 100vw;
    width: min(560px, 100vw) !important;
  }
}

.drawer-section {
  margin-top: 16px;
}

.drawer-section strong {
  color: #111827;
  display: block;
  margin-bottom: 8px;
}

.drawer-section pre {
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  margin: 0;
  max-height: 260px;
  overflow: auto;
  padding: 12px;
  white-space: pre-wrap;
  word-break: break-word;
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

  .header-actions :deep(.ant-space-item) {
    max-width: 100%;
  }

  .header-actions :deep(.ant-btn) {
    max-width: 100%;
    white-space: normal;
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
    min-width: 0;
    padding: 14px;
  }

  .skill-card-header {
    align-items: flex-start;
    display: flex;
    gap: 12px;
    justify-content: space-between;
  }

  .skill-card-header > div {
    min-width: 0;
  }

  .skill-card-header p {
    color: #4b5563;
    margin: 6px 0 0;
    overflow-wrap: anywhere;
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
    width: 100%;
  }

  .card-actions :deep(.ant-space-item) {
    max-width: 100%;
  }

  .card-actions :deep(.ant-btn) {
    max-width: 100%;
    white-space: normal;
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
