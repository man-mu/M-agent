import { h } from 'vue'
import { CheckCircleOutlined, LoadingOutlined } from '@ant-design/icons-vue'
import { ThoughtChain, type ThoughtChainProps } from 'ant-design-x-vue'

export function useThoughtChainBuilder() {
  const buildPendingNodeThoughtChain = () => {
    const items: ThoughtChainProps['items'] = [
      {
        title: '研究进行中',
        description: '等待后端工作流返回下一段 SSE 事件',
        icon: h(LoadingOutlined),
        status: 'pending',
      },
    ]
    return h(ThoughtChain, { items })
  }

  const buildCompletedThoughtChain = () => {
    const items: ThoughtChainProps['items'] = [
      {
        title: '研究完成',
        icon: h(CheckCircleOutlined),
        status: 'success',
      },
    ]
    return h(ThoughtChain, { items })
  }

  return {
    collapsible: [],
    onExpand: () => undefined,
    buildPendingNodeThoughtChain,
    buildStartDSThoughtChain: buildPendingNodeThoughtChain,
    buildOnDSThoughtChain: buildPendingNodeThoughtChain,
    buildEndDSThoughtChain: buildCompletedThoughtChain,
  }
}
