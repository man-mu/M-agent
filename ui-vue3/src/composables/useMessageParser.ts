import type { MessageStatus } from 'ant-design-x-vue'
import { parseJsonTextStrict } from '@/utils/jsonParser'

export function useMessageParser() {
  const findNode = (jsonArray: any[], nodeName: string) =>
    jsonArray.find(item => item.nodeName === nodeName || item.node_name === nodeName)

  const parseLoadingMessage = (msg: string) => ({
    type: 'pending',
    data: parseJsonTextStrict(msg),
  })

  const parseSuccessMessage = (msg: string) => {
    const jsonArray = parseJsonTextStrict(msg)
    const endNode = findNode(jsonArray, '__END__')
    const content = endNode?.content
    if (content && typeof content === 'object') {
      return { type: 'chat', content: content.output || content.final_report || '' }
    }
    return { type: 'chat', content: typeof content === 'string' ? content : msg }
  }

  const parseMessage = (status: MessageStatus, msg: string) => {
    if (status === 'loading') return parseLoadingMessage(msg)
    if (status === 'success') return parseSuccessMessage(msg)
    return msg
  }

  const parseFooter = () => ''

  return {
    findNode,
    parseLoadingMessage,
    parseSuccessMessage,
    parseMessage,
    parseFooter,
  }
}
