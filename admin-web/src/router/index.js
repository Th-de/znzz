import { createRouter, createWebHistory } from 'vue-router'
import { getRole, getToken } from '../utils/auth'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
  { path: '/', redirect: '/login' },
  { path: '/admin', component: () => import('../views/Admin.vue') },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  if (to.path === '/login') return true
  if (!getToken()) return '/login'
  const role = getRole()
  if (!['OPERATOR', 'SUPER_ADMIN', 'INSPECTION'].includes(role)) return '/login'
  return true
})

export default router
