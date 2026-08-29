<template>
  <el-container class="wrap">
    <el-aside width="200px" class="aside">
      <div class="logo">买家端</div>
      <el-menu :default-active="active" router background-color="#304156" text-color="#bfcbd9" active-text-color="#409EFF">
        <el-menu-item index="/buyer/home">我的需求</el-menu-item>
        <el-menu-item index="/buyer/mine">我的主页</el-menu-item>
        <el-menu-item index="/buyer/publish">发布需求</el-menu-item>
        <el-menu-item index="/buyer/orders">我的订单</el-menu-item>
        <el-menu-item index="/buyer/notifies">通知</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="top">
        <div class="top-left">
          <el-button v-if="backTo" link type="primary" @click="goBack">← 返回</el-button>
          <span>{{ title }}</span>
        </div>
        <el-button @click="logout">退出</el-button>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const active = computed(() => {
  if (route.path.startsWith('/buyer/order')) return '/buyer/orders'
  if (route.path.startsWith('/buyer/demand') || route.path.startsWith('/buyer/solutions')) return '/buyer/home'
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

<style scoped>
.wrap { height: 100vh; }
.aside { background: #304156; }
.logo { color: #fff; text-align: center; padding: 16px 0; font-weight: bold; }
.top { display: flex; align-items: center; justify-content: space-between; background: #fff; border-bottom: 1px solid #eee; }
.top-left { display: flex; align-items: center; gap: 8px; }
.main { background: #f5f7fa; }
</style>
