import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import Alert from 'ant-design-vue/es/alert'
import Button from 'ant-design-vue/es/button'
import ConfigProvider from 'ant-design-vue/es/config-provider'
import Divider from 'ant-design-vue/es/divider'
import Empty from 'ant-design-vue/es/empty'
import Input from 'ant-design-vue/es/input'
import Layout from 'ant-design-vue/es/layout'
import Segmented from 'ant-design-vue/es/segmented'
import Space from 'ant-design-vue/es/space'
import Spin from 'ant-design-vue/es/spin'
import Switch from 'ant-design-vue/es/switch'
import Tag from 'ant-design-vue/es/tag'
import Timeline from 'ant-design-vue/es/timeline'
import Tooltip from 'ant-design-vue/es/tooltip'

import router from './router'
import App from './App.vue'
import 'ant-design-vue/dist/reset.css'
import { i18n } from '@/base/i18n'

const app = createApp(App)
const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)

app
  .use(pinia)
  .use(Alert)
  .use(Button)
  .use(ConfigProvider)
  .use(Divider)
  .use(Empty)
  .use(Input)
  .use(Layout)
  .use(Segmented)
  .use(Space)
  .use(Spin)
  .use(Switch)
  .use(Tag)
  .use(Timeline)
  .use(Tooltip)
  .use(i18n)
  .use(router)
  .mount('#app')
