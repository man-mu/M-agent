<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowLeftOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  PoweroffOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import appService from '@/services/api/app'
import skillService from '@/services/api/skills'
import type { CreateSkillRequest, SkillDefinition } from '@/services/api/skills'

const router = useRouter()
const skills = ref<SkillDefinition[]>([])
const capabilityLoading = ref(true)
const skillEnabled = ref(false)
const loading = ref(false)
const saving = ref(false)
const modalVisible = ref(false)
const editingName = ref('')
const form = reactive<CreateSkillRequest>({
  definition: {
    name: '',
    description: '',
    version: '1.0.0',
    enabled: true,
  },
  promptTemplate: '',
})

const columns = [
  { title: '名称', dataIndex: 'name', key: 'name', width: 180 },
  { title: '描述', dataIndex: 'description', key: 'description' },
  { title: '版本', dataIndex: 'version', key: 'version', width: 110 },
  { title: '状态', key: 'enabled', width: 90 },
  { title: '操作', key: 'actions', width: 170 },
]

async function loadSkills() {
  if (!skillEnabled.value) {
    skills.value = []
    return
  }
  loading.value = true
  try {
    skills.value = await skillService.list()
  } catch (err: any) {
    message.error(err.message || '加载 Skill 列表失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editingName.value = ''
  form.definition = {
    name: '',
    description: '',
    version: '1.0.0',
    enabled: true,
  }
  form.promptTemplate = ''
}

function openCreate() {
  resetForm()
  modalVisible.value = true
}

async function openEdit(name: string) {
  try {
    const detail = await skillService.get(name)
    editingName.value = name
    form.definition = { ...detail.definition }
    form.promptTemplate = detail.promptTemplate
    modalVisible.value = true
  } catch (err: any) {
    message.error(err.message || '读取 Skill 详情失败')
  }
}

async function saveSkill() {
  if (!form.definition.name.trim() || !form.definition.description.trim()) {
    message.warning('名称和描述不能为空')
    return
  }
  saving.value = true
  try {
    if (editingName.value) {
      await skillService.update(editingName.value, form)
      message.success('Skill 已更新')
    } else {
      await skillService.create(form)
      message.success('Skill 已创建')
    }
    modalVisible.value = false
    await loadSkills()
  } catch (err: any) {
    message.error(err.response?.data?.error || err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleSkill(name: string) {
  try {
    await skillService.toggle(name)
    await loadSkills()
  } catch (err: any) {
    message.error(err.message || '切换失败')
  }
}

function confirmDelete(name: string) {
  Modal.confirm({
    title: `删除 Skill：${name}`,
    content: '删除后会移除对应的 Skill 文件。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await skillService.delete(name)
        message.success('Skill 已删除')
        await loadSkills()
      } catch (err: any) {
        message.error(err.message || '删除失败')
      }
    },
  })
}

async function initialize() {
  capabilityLoading.value = true
  try {
    const capabilities = await appService.getCapabilities()
    skillEnabled.value = capabilities.skillEnabled
    if (capabilities.skillEnabled) {
      await loadSkills()
    }
  } catch (err: any) {
    skillEnabled.value = false
    message.error(err.message || '加载应用能力信息失败')
  } finally {
    capabilityLoading.value = false
  }
}

onMounted(initialize)
</script>

<template>
  <main class="skills-page">
    <div class="page-header">
      <div>
        <div class="eyebrow">Prompt Skills</div>
        <h1>Skill 管理</h1>
      </div>
      <a-space v-if="skillEnabled">
        <a-button :loading="loading" @click="loadSkills">
          <ReloadOutlined />
          刷新
        </a-button>
        <a-button type="primary" @click="openCreate">
          <PlusOutlined />
          新建 Skill
        </a-button>
      </a-space>
    </div>

    <a-spin v-if="capabilityLoading" />

    <a-empty v-else-if="!skillEnabled" description="Skill 模块未启用">
      <a-button type="primary" @click="router.push('/chat')">
        <ArrowLeftOutlined />
        返回对话
      </a-button>
    </a-empty>

    <a-table v-else :columns="columns" :data-source="skills" :loading="loading" row-key="name">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'enabled'">
          <a-tag :color="record.enabled ? 'green' : 'default'">
            {{ record.enabled ? '启用' : '禁用' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a-tooltip title="编辑">
              <a-button size="small" @click="openEdit(record.name)">
                <EditOutlined />
              </a-button>
            </a-tooltip>
            <a-tooltip :title="record.enabled ? '禁用' : '启用'">
              <a-button size="small" :danger="record.enabled" @click="toggleSkill(record.name)">
                <PoweroffOutlined />
              </a-button>
            </a-tooltip>
            <a-tooltip title="删除">
              <a-button size="small" danger @click="confirmDelete(record.name)">
                <DeleteOutlined />
              </a-button>
            </a-tooltip>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="modalVisible"
      :title="editingName ? '编辑 Skill' : '新建 Skill'"
      :confirm-loading="saving"
      width="760px"
      @ok="saveSkill"
    >
      <a-form layout="vertical">
        <a-form-item label="名称" required>
          <a-input
            v-model:value="form.definition.name"
            :disabled="Boolean(editingName)"
            placeholder="例如 code-review"
          />
        </a-form-item>
        <a-form-item label="描述" required>
          <a-input v-model:value="form.definition.description" placeholder="这个 Skill 解决什么问题" />
        </a-form-item>
        <a-form-item label="版本">
          <a-input v-model:value="form.definition.version" />
        </a-form-item>
        <a-form-item label="Prompt 模板">
          <a-textarea
            v-model:value="form.promptTemplate"
            :rows="12"
            placeholder="可使用 {{param}} 作为参数占位符"
          />
        </a-form-item>
        <a-form-item label="启用">
          <a-switch v-model:checked="form.definition.enabled" />
        </a-form-item>
      </a-form>
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
  justify-content: space-between;
  margin-bottom: 18px;
}

.page-header h1 {
  font-size: 24px;
  margin: 2px 0 0;
}

.eyebrow {
  color: #738096;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
</style>
