import { reactive } from 'vue'
import { createI18n } from 'vue-i18n'
import en from './en'
import zh from './zh'

export const LOCAL_STORAGE_LOCALE = 'LOCAL_STORAGE_LOCALE'

export const localeConfig = reactive({
  locale: localStorage.getItem(LOCAL_STORAGE_LOCALE) || 'zh',
  opts: [
    { value: 'en', label: 'En' },
    { value: 'zh', label: '中文' },
  ],
})

export const i18n = createI18n({
  legacy: false,
  locale: localeConfig.locale,
  fallbackLocale: 'zh',
  messages: { en, zh },
})

export const changeLanguage = (locale: 'zh' | 'en') => {
  localStorage.setItem(LOCAL_STORAGE_LOCALE, locale)
  i18n.global.locale.value = locale
  localeConfig.locale = locale
}
