import { createRouter, createWebHistory } from 'vue-router'
import { routes } from '@/router/defaultRoutes'

const router = createRouter({
  history: createWebHistory('/'),
  routes,
})

export default router
