import { defineConfig, devices } from '@playwright/test'

const backendURL = process.env.E2E_BACKEND_URL || 'http://localhost:18080'

export default defineConfig({
  testDir: './e2e',
  timeout: 120_000,
  expect: {
    timeout: 20_000,
  },
  use: {
    baseURL: 'http://127.0.0.1:5173',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: `npm run dev -- --host 127.0.0.1 --port 5173 --strictPort`,
    env: {
      VITE_BACKEND_PROXY: backendURL,
    },
    reuseExistingServer: false,
    timeout: 120_000,
    url: 'http://127.0.0.1:5173',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
