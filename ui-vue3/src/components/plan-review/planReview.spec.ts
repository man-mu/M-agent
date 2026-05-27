import { describe, expect, it } from 'vitest'
import { derivePlanStepViews, planContextLabel } from './helpers'

describe('plan-review helpers', () => {
  it('derives readable step metadata from real plan fields', () => {
    const steps = derivePlanStepViews({
      title: 'Plan',
      has_enough_context: false,
      thought: 'Need more context',
      steps: [
        {
          id: 's1',
          title: 'Search records',
          description: 'Find real references',
          need_web_search: true,
          step_type: 'research',
          assigned_node: 'researcher',
          execution_status: 'pending',
        },
        {
          description: 'Write the report',
          need_web_search: false,
        },
      ],
    })

    expect(steps[0]).toEqual({
      key: 's1',
      index: 1,
      title: 'Search records',
      description: 'Find real references',
      tags: [
        { label: '需要搜索', color: 'blue' },
        { label: 'research' },
        { label: 'researcher', color: 'purple' },
        { label: 'pending', color: 'green' },
      ],
    })
    expect(steps[1]).toMatchObject({
      index: 2,
      title: '步骤 2',
      description: 'Write the report',
      tags: [{ label: '无需搜索', color: 'default' }],
    })
  })

  it('handles missing plan fields without inventing content', () => {
    expect(derivePlanStepViews(null)).toEqual([])
    expect(derivePlanStepViews({ steps: [{}] })).toEqual([
      {
        key: '步骤 1-0',
        index: 1,
        title: '步骤 1',
        description: '',
        tags: [],
      },
    ])
    expect(planContextLabel({})).toBe('')
  })

  it('shows whether the backend says context is enough', () => {
    expect(planContextLabel({ has_enough_context: true })).toBe('上下文充足：是')
    expect(planContextLabel({ has_enough_context: false })).toBe('上下文充足：否')
  })
})
