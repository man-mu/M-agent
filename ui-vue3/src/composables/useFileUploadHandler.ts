import { ref } from 'vue'
import { message } from 'ant-design-vue'
import service from '@/utils/request'

interface FileUploadHandlerOptions {
  convId: string
}

export function useFileUploadHandler(options: FileUploadHandlerOptions) {
  const headerOpen = ref(false)

  const beforeUpload = (file: File): boolean => {
    const maxSize = 10 * 1024 * 1024
    if (file.size > maxSize) {
      message.error('文件大小不能超过 10MB')
      return false
    }
    return true
  }

  const handleFileUpload = async (uploadOptions: any) => {
    const file = uploadOptions.file as File
    const form = new FormData()
    form.append('file', file)
    form.append('session_id', options.convId || '__default__')

    try {
      const response = await service.post('/api/rag/upload', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      uploadOptions.onSuccess?.(response.data)
      message.success(`${file.name} 上传成功`)
    } catch (error) {
      uploadOptions.onError?.(error)
      message.error(`${file.name} 上传失败`)
    }
  }

  const handleFileChange = () => undefined

  return {
    headerOpen,
    handleFileChange,
    beforeUpload,
    handleFileUpload,
  }
}
