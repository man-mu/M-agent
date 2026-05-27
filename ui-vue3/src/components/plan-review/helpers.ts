import type { ResearchPlan, ResearchStep } from '@/services/api/chat'

export interface PlanStepView {
  key: string
  index: number
  title: string
  description: string
  tags: Array<{ label: string, color?: string }>
}

function text(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function booleanLabel(value: boolean) {
  return value ? '是' : '否'
}

export function planContextLabel(plan: ResearchPlan | null | undefined) {
  if (!plan || typeof plan.has_enough_context !== 'boolean') {
    return ''
  }
  return `上下文充足：${booleanLabel(plan.has_enough_context)}`
}

export function derivePlanStepViews(plan: ResearchPlan | null | undefined): PlanStepView[] {
  const steps = Array.isArray(plan?.steps) ? plan.steps : []
  return steps.map((step: ResearchStep, index) => {
    const title = text(step.title) || `步骤 ${index + 1}`
    const description = text(step.description)
    const tags: PlanStepView['tags'] = []
    if (typeof step.need_web_search === 'boolean') {
      tags.push({ label: step.need_web_search ? '需要搜索' : '无需搜索', color: step.need_web_search ? 'blue' : 'default' })
    }
    if (text(step.step_type)) {
      tags.push({ label: text(step.step_type) })
    }
    if (text(step.assigned_node)) {
      tags.push({ label: text(step.assigned_node), color: 'purple' })
    }
    if (text(step.execution_status)) {
      tags.push({ label: text(step.execution_status), color: 'green' })
    }
    return {
      key: text(step.id) || `${title}-${index}`,
      index: index + 1,
      title,
      description,
      tags,
    }
  })
}
