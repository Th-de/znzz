import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
  { path: '/', redirect: '/login' },
  {
    path: '/buyer',
    component: () => import('../layouts/BuyerLayout.vue'),
    children: [
      { path: 'home', component: () => import('../views/buyer/Home.vue'), meta: { title: '我的需求' } },
      { path: 'publish', component: () => import('../views/buyer/Publish.vue'), meta: { title: '发布需求' } },
      { path: 'demand/:id', component: () => import('../views/buyer/DemandDetail.vue'), meta: { title: '需求详情' } },
      { path: 'solutions/:demandId', component: () => import('../views/buyer/Solutions.vue'), meta: { title: '方案比选' } },
      { path: 'orders', component: () => import('../views/buyer/Orders.vue'), meta: { title: '我的订单' } },
      { path: 'order/:id', component: () => import('../views/buyer/OrderDetail.vue'), meta: { title: '订单履约' } },
      { path: 'notifies', component: () => import('../views/Notifies.vue'), meta: { title: '通知' } },
    ],
  },
  {
    path: '/factory',
    component: () => import('../layouts/FactoryLayout.vue'),
    children: [
      { path: 'home', component: () => import('../views/factory/Home.vue'), meta: { title: '工作台' } },
      { path: 'profile', component: () => import('../views/factory/Profile.vue'), meta: { title: '能力档案' } },
      { path: 'demands', component: () => import('../views/factory/Demands.vue'), meta: { title: '浏览需求' } },
      { path: 'quotations', component: () => import('../views/factory/Quotations.vue'), meta: { title: '我的报名' } },
      { path: 'stages', component: () => import('../views/factory/Stages.vue'), meta: { title: '我的工单' } },
      { path: 'notifies', component: () => import('../views/Notifies.vue'), meta: { title: '通知' } },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
