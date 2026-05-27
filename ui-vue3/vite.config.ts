import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendProxy = env.VITE_BACKEND_PROXY || 'http://localhost:18080'

  return {
    base: '/',
    build: {
      outDir: './ui',
      chunkSizeWarningLimit: 600,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes('node_modules')) {
              return undefined
            }
            if (id.includes('highlight.js')) {
              return 'highlight'
            }
            if (id.includes('markdown-it') || id.includes('katex')) {
              return 'markdown'
            }
            if (id.includes('@ant-design/icons-vue')) {
              return 'antd-icons'
            }
            if (id.includes('ant-design-vue')) {
              return undefined
            }
            if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router') || id.includes('vue-i18n')) {
              return 'vue'
            }
            return 'vendor'
          },
        },
      },
    },
    server: {
      open: true,
      host: true,
      proxy: {
        '/api': {
          target: backendProxy,
          changeOrigin: true,
        },
        '^/chat/(stream|resume|stop)$': {
          target: backendProxy,
          changeOrigin: true,
        },
      },
    },
    plugins: [vue(), vueJsx()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
      extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.vue'],
    },
  }
})
