import Dexie from 'dexie'
import { toRaw } from 'vue-demi'
import { reactive } from 'vue'

const db: any = new Dexie('SAA_DEEPRESEARCH')
db.version(1).stores({
  convInfo: '++id, conv_id',
})
const convInfoDB = db['convInfo']
export const findConvInfo = async (conv_id: string) => {
  return (
    (await convInfoDB.where('conv_id').equals(conv_id).first()) || {
      conv_id: null,
      conv_messages: null,
    }
  )
}

/**
 * 移除会话数据
 * @param conv_id
 */
export const removeConvInfo = async (conv_id: string) => {
  return await convInfoDB.where('conv_id').equals(conv_id).delete()
}

/**
 * 保存会话信息
 * @param conv_id
 * @param conv_messages
 */
export const addConvInfo = async (conv_id: string, conv_messages: any) => {
  if (!conv_messages) {
    return
  }
  conv_messages = {
    convId: conv_messages.convId,
    currentState: conv_messages.currentState,
    history: conv_messages.history,
    htmlReport: conv_messages.htmlReport,
    report: conv_messages.report,
    uploadedFiles: conv_messages.uploadedFiles,
  }
  let old: any = await findConvInfo(conv_id)
  if (old.conv_id) {
    await convInfoDB
      .where('conv_id')
      .equals(old['conv_id'])
      .modify((item: any) => {
        if (conv_messages) {
          item.conv_messages = conv_messages
        }
      })
  } else {
    await convInfoDB.add({
      conv_id,
      conv_messages,
    })
  }
}
