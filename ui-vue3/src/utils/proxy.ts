import { isProxy, isRef } from 'vue'
import { toRaw } from 'vue-demi'

export const deepToRaw = <T>(value: T, cache = new WeakMap<object, any>()): T => {
  // 非对象/空值：直接返回
  if (value === null || typeof value !== 'object') {
    return value
  }

  // 缓存命中：直接返回已处理的结果
  if (cache.has(value as object)) {
    return cache.get(value as object)
  }

  // 处理 Ref
  const rawValue: any = isRef(value) ? value.value : isProxy(value) ? toRaw(value) : value

  let result: any
  // 处理数组
  if (Array.isArray(rawValue)) {
    result = rawValue.map(item => deepToRaw(item, cache))
  }
  // 处理纯对象
  else if (Object.prototype.toString.call(rawValue) === '[object Object]') {
    result = {}
    for (const key in rawValue) {
      if (rawValue.hasOwnProperty(key)) {
        result[key] = deepToRaw(rawValue[key], cache)
      }
    }
  }
  // 内置对象/其他：直接返回
  else {
    result = rawValue
  }

  // 存入缓存
  cache.set(value as object, result)
  return result as T
}
