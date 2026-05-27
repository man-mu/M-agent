import type { RouteRecordRaw } from 'vue-router'

export declare type RouteRecordType = RouteRecordRaw & {
  key?: string
  name: string
  children?: RouteRecordType[]
  meta?: { icon?: string }
}

export const routes: Readonly<RouteRecordType[]> = [
  {
    path: '/',
    name: 'Root',
    redirect: 'chat',
    component: () => import('@/components/layout/index.vue'),
    meta: { skip: true },
    children: [
      {
        path: '/chat/:convId?',
        name: 'chat',
        component: () => import('../views/chat/index.vue'),
        meta: { icon: 'chat', fullscreen: true },
      },
      {
        path: '/skills',
        name: 'skills',
        component: () => import('../views/skills/index.vue'),
        meta: { icon: 'skills', fullscreen: true },
      },
      {
        path: '/settings',
        name: 'settings',
        component: () => import('../views/settings/index.vue'),
        meta: { icon: 'settings', fullscreen: true },
      },
    ],
  },
  {
    path: '/:catchAll(.*)',
    name: 'notFound',
    component: () => import('../views/error/notFound.vue'),
    meta: { skip: true },
  },
]
