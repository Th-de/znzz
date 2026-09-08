<template>
  <div class="shell">
    <AppHeader :items="menus" :active="active" role-label="买家端" @logout="logout" />
    <div class="app-body">
      <div class="page-shell">
        <div class="page-bar">
          <div style="display:flex;align-items:center;gap:8px">
            <el-button v-if="backTo" link type="primary" @click="goBack">← 返回</el-button>
            <h2>{{ title }}</h2>
          </div>
        </div>
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'

const route = useRoute()
const router = useRouter()
const menus = [
  { index: '/buyer/home', label: '工作台' },
  { index: '/buyer/demands', label: '我的需求' },
  { index: '/buyer/mine', label: '我的主页' },
  { index: '/buyer/publish', label: '发布需求' },
  { index: '/buyer/notifies', label: '通知' },
]
const active = computed(() => {
  if (route.path.startsWith('/buyer/demands')) return '/buyer/demands'
  if (route.path.startsWith('/buyer/order') || route.path.startsWith('/buyer/demand') || route.path.startsWith('/buyer/solutions')) return '/buyer/demands'
  if (route.path.startsWith('/buyer/mine')) return '/buyer/mine'
  if (route.path.startsWith('/buyer/publish')) return '/buyer/publish'
  if (route.path.startsWith('/buyer/notifies')) return '/buyer/notifies'
  return '/buyer/home'
})
const title = computed(() => route.meta.title || '买家工作台')
const backTo = computed(() => route.meta.backTo || '')
function goBack() {
  if (window.history.length > 1) router.back()
  else router.push(backTo.value)
}
function logout() {
  sessionStorage.clear()
  router.push('/login')
}
</script>
