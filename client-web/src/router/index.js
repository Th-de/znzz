import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', component: () => import('../views/Landing.vue') },
  { path: '/login', component: () => import('../views/Login.vue') },
  {
    path: '/buyer',
    component: () => import('../layouts/BuyerLayout.vue'),
    children: [
      { path: 'home', component: () => import('../views/buyer/Workbench.vue'), meta: { title: '工作台' } },
      { path: 'demands', component: () => import('../views/buyer/Home.vue'), meta: { title: '我的需求' } },
      { path: 'mine', component: () => import('../views/MyHome.vue'), meta: { title: '我的主页' } },
      { path: 'publish', component: () => import('../views/buyer/Publish.vue'), meta: { title: '发布需求' } },
      { path: 'demand/:id', component: () => import('../views/buyer/DemandDetail.vue'), meta: { title: '需求详情', backTo: '/buyer/demands' } },
      { path: 'solutions/:demandId', redirect: to => ({ path: '/buyer/demand/' + to.params.demandId }) },
      { path: 'orders', redirect: '/buyer/demands' },
      { path: 'order/:id', component: () => import('../views/buyer/OrderDetail.vue'), meta: { title: '需求详情', backTo: '/buyer/demands' } },
      { path: 'notifies', component: () => import('../views/Notifies.vue'), meta: { title: '通知' } },
    ],
  },
  {
    path: '/factory',
    component: () => import('../layouts/FactoryLayout.vue'),
    children: [
      { path: 'home', component: () => import('../views/factory/Home.vue'), meta: { title: '工作台' } },
      { path: 'mine', component: () => import('../views/MyHome.vue'), meta: { title: '信息' } },
      { path: 'devices', component: () => import('../views/factory/Devices.vue'), meta: { title: '设备' } },
      { path: 'profile', component: () => import('../views/factory/Profile.vue'), meta: { title: '能力档案' } },
      { path: 'demands', component: () => import('../views/factory/Demands.vue'), meta: { title: '浏览需求' } },
      { path: 'quotations', component: () => import('../views/factory/Quotations.vue'), meta: { title: '我的报名' } },
      { path: 'quotations/:id', component: () => import('../views/factory/BidDetail.vue'), meta: { title: '报名详情', backTo: '/factory/quotations' } },
      { path: 'stages', redirect: '/factory/quotations' },
      { path: 'notifies', component: () => import('../views/Notifies.vue'), meta: { title: '通知' } },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  if (to.path === '/login' || to.path === '/') return true
  const token = sessionStorage.getItem('token')
  if (!token) return '/login'
  const role = sessionStorage.getItem('role')
  if (to.path.startsWith('/buyer') && role !== 'BUYER') return role === 'FACTORY' ? '/factory/home' : '/login'
  if (to.path.startsWith('/factory') && role !== 'FACTORY') return role === 'BUYER' ? '/buyer/home' : '/login'
  return true
})

export default router
