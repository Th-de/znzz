<template>
  <div class="shell">
    <AppHeader :items="menus" :active="active" role-label="工厂端" @logout="logout" />
    <div class="app-body">
      <div class="page-shell" ref="body">
        <div class="page-bar">
          <div style="display:flex;align-items:center;gap:8px">
            <el-button v-if="backTo" link type="primary" @click="goBack">← 返回</el-button>
            <h2>{{ title }}</h2>
          </div>
        </div>
        <router-view v-slot="{ Component }">
          <transition name="page-rise" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import { refreshWorkspace } from '../motion/workspace'

const route = useRoute()
const router = useRouter()
const body = ref(null)
const menus = [
  { index: '/factory/home', label: '工作台' },
  {
    index: 'factory-mine',
    label: '我的主页',
    children: [
      { index: '/factory/mine', label: '信息' },
      { index: '/factory/devices', label: '设备' },
      { index: '/factory/profile', label: '能力档案' },
    ],
  },
  { index: '/factory/demands', label: '浏览需求' },
  { index: '/factory/quotations', label: '我的报名' },
  { index: '/factory/notifies', label: '通知' },
]
const active = computed(() => {
  if (route.path.startsWith('/factory/mine')) return '/factory/mine'
  if (route.path.startsWith('/factory/devices')) return '/factory/devices'
  if (route.path.startsWith('/factory/profile')) return '/factory/profile'
  if (route.path.startsWith('/factory/demands')) return '/factory/demands'
  if (route.path.startsWith('/factory/quotations')) return '/factory/quotations'
  if (route.path.startsWith('/factory/notifies')) return '/factory/notifies'
  return '/factory/home'
})
const title = computed(() => route.meta.title || '工厂工作台')
const backTo = computed(() => route.meta.backTo || '')
function goBack() {
  if (window.history.length > 1) router.back()
  else router.push(backTo.value)
}
function logout() {
  sessionStorage.clear()
  router.push('/login')
}

onMounted(() => refreshWorkspace(body.value))
watch(() => route.fullPath, () => refreshWorkspace(body.value))
</script>
