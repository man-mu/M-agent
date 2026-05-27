import { ref, type Ref } from 'vue'
import { chatService } from '@/services'
import type { ChatStreamEvent } from '@/services/api/chat'

interface ChatOptions {
  convId: string
  deepResearch?: boolean
}

interface ChatReturn {
  senderLoading: Ref<boolean>
  send: (message: string, onEvent: (event: ChatStreamEvent) => void) => Promise<void>
}

export function useChat(options: ChatOptions): ChatReturn {
  const senderLoading = ref(false)

  async function send(message: string, onEvent: (event: ChatStreamEvent) => void) {
    senderLoading.value = true
    try {
      await chatService.stream(
        {
          query: message,
          session_id: options.convId,
          enable_deepresearch: options.deepResearch ?? true,
          auto_accepted_plan: true,
          max_step_num: 3,
        },
        onEvent,
      )
    } finally {
      senderLoading.value = false
    }
  }

  return { senderLoading, send }
}
