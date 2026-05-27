import { type MessageInfo } from 'ant-design-x-vue'

export interface MessageState<Message = any> {
  info: MessageInfo<Message | any>
  candidate: boolean
  deepResearchDetail: boolean
  runFlag: boolean
  deepResearch?: boolean
  threadId: string
}

export interface MsgType<Message> {
  convId: string
  history: MessageInfo<any>[]
  currentState: MessageState<Message>
  htmlReport: string[]
  report: { [key: string]: any[] }
  uploadedFiles: any[]
}
