<template>
  <div class="plan-review" data-testid="plan-review">
    <div class="plan-review-header">
      <div>
        <span class="plan-label">计划审阅</span>
        <h2>{{ plan.title || '研究计划' }}</h2>
      </div>
      <a-tag :color="statusColor">{{ statusLabel }}</a-tag>
    </div>

    <div v-if="contextLabel || plan.thought" class="plan-context">
      <a-tag v-if="contextLabel" color="geekblue">{{ contextLabel }}</a-tag>
      <p v-if="plan.thought">{{ plan.thought }}</p>
    </div>

    <ol v-if="stepViews.length" class="plan-steps">
      <li
        v-for="step in stepViews"
        :key="step.key"
      >
        <div class="step-number">{{ step.index }}</div>
        <div class="step-body">
          <div class="step-title">
            <strong>{{ step.title }}</strong>
            <a-tag
              v-for="tag in step.tags"
              :key="`${step.key}-${tag.label}`"
              :color="tag.color"
            >
              {{ tag.label }}
            </a-tag>
          </div>
          <p v-if="step.description">{{ step.description }}</p>
        </div>
      </li>
    </ol>
    <a-empty v-else description="后端未返回计划步骤" />

    <a-textarea
      v-if="feedbackVisible"
      :value="feedback"
      :auto-size="{ minRows: 2, maxRows: 4 }"
      :disabled="busy"
      class="plan-feedback"
      data-testid="plan-feedback"
      placeholder="写下你希望调整的方向..."
      @input="$emit('update:feedback', ($event.target as HTMLTextAreaElement).value)"
    />

    <div class="plan-actions">
      <a-button
        :disabled="busy"
        data-testid="modify-plan"
        @click="$emit('toggle-feedback')"
      >
        <EditOutlined />
        {{ feedbackVisible ? '收起修改' : '修改计划' }}
      </a-button>
      <a-button
        v-if="feedbackVisible"
        :disabled="!feedback.trim() || busy"
        :loading="pendingAction === 'feedback'"
        data-testid="submit-plan-feedback"
        @click="$emit('submit-feedback')"
      >
        <SendOutlined />
        提交修改意见
      </a-button>
      <a-button
        type="primary"
        :disabled="busy"
        :loading="pendingAction === 'accept'"
        data-testid="accept-plan"
        @click="$emit('accept')"
      >
        <CheckCircleOutlined />
        接受计划
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CheckCircleOutlined, EditOutlined, SendOutlined } from '@ant-design/icons-vue'
import type { ResearchPlan } from '@/services/api/chat'
import { derivePlanStepViews, planContextLabel } from './helpers'

const props = defineProps<{
  plan: ResearchPlan
  feedback: string
  feedbackVisible: boolean
  pendingAction: 'accept' | 'feedback' | ''
  running?: boolean
  resuming?: boolean
}>()

defineEmits<{
  accept: []
  'submit-feedback': []
  'toggle-feedback': []
  'update:feedback': [value: string]
}>()

const busy = computed(() => Boolean(props.running || props.resuming))
const stepViews = computed(() => derivePlanStepViews(props.plan))
const contextLabel = computed(() => planContextLabel(props.plan))
const statusLabel = computed(() => {
  if (props.resuming) return '恢复中'
  if (props.running) return '提交中'
  return '等待确认'
})
const statusColor = computed(() => {
  if (props.resuming || props.running) return 'processing'
  return 'orange'
})
</script>

<style lang="less" scoped>
.plan-review {
  background: #fff;
  border: 1px solid #f2c37b;
  border-radius: 8px;
  margin: 0 auto 18px;
  max-width: 880px;
  padding: 18px;
}

.plan-review-header {
  align-items: flex-start;
  display: flex;
  gap: 16px;
  justify-content: space-between;
  margin-bottom: 12px;
}

.plan-label {
  color: #a15c05;
  display: block;
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 4px;
}

.plan-review h2 {
  color: #1b2638;
  font-size: 20px;
  line-height: 1.35;
  margin: 0;
}

.plan-context {
  background: #fbfcff;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  margin-bottom: 14px;
  padding: 12px;
}

.plan-context p {
  color: #55657a;
  margin: 8px 0 0;
  white-space: pre-wrap;
}

.plan-steps {
  display: flex;
  flex-direction: column;
  gap: 10px;
  list-style: none;
  margin: 0;
  padding: 0;
}

.plan-steps li {
  border: 1px solid #edf1f7;
  border-radius: 8px;
  display: grid;
  gap: 10px;
  grid-template-columns: 28px minmax(0, 1fr);
  padding: 12px;
}

.step-number {
  align-items: center;
  background: #fff6e8;
  border: 1px solid #f3c27a;
  border-radius: 8px;
  color: #9b5a00;
  display: flex;
  font-weight: 700;
  height: 28px;
  justify-content: center;
  width: 28px;
}

.step-body {
  min-width: 0;
}

.step-title {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.plan-steps p {
  color: #637087;
  margin: 6px 0 0;
  white-space: pre-wrap;
}

.plan-feedback {
  margin-top: 14px;
}

.plan-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 14px;
}

@media (max-width: 640px) {
  .plan-review {
    margin-bottom: 14px;
    padding: 14px 12px;
  }

  .plan-review-header {
    flex-direction: column;
    gap: 8px;
  }

  .plan-steps li {
    grid-template-columns: 24px minmax(0, 1fr);
    padding: 10px;
  }

  .step-number {
    height: 24px;
    width: 24px;
  }

  .plan-actions {
    justify-content: stretch;
  }

  .plan-actions :deep(.ant-btn) {
    flex: 1;
  }
}
</style>
