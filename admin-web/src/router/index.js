import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
  { path: '/', redirect: '/login' },
  { path: '/admin', component: () => import('../views/Admin.vue') },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  if (to.path === '/login') return true
  if (!localStorage.getItem('token')) return '/login'
  const role = localStorage.getItem('role')
  if (!['OPERATOR', 'SUPER_ADMIN', 'INSPECTION'].includes(role)) return '/login'
  return true
})

export default router
