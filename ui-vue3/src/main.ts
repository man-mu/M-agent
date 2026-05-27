import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import Antd from 'ant-design-vue'

import router from './router'
import App from './App.vue'
import 'ant-design-vue/dist/reset.css'
import { i18n } from '@/base/i18n'

const app = createApp(App)
const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)

app.use(pinia).use(Antd).use(i18n).use(router).mount('#app')
