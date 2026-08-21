import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
  { path: '/', redirect: '/login' },
  { path: '/admin', component: () => import('../views/Admin.vue') },
]

export default createRouter({ history: createWebHistory(), routes })
