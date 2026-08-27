<template>
  <el-container class="wrap">
    <el-aside width="200px" class="aside">
      <div class="logo">工厂端</div>
      <el-menu :default-active="active" router background-color="#304156" text-color="#bfcbd9" active-text-color="#409EFF">
        <el-menu-item index="/factory/home">工作台</el-menu-item>
        <el-menu-item index="/factory/mine">我的主页</el-menu-item>
        <el-menu-item index="/factory/devices">我的设备</el-menu-item>
        <el-menu-item index="/factory/profile">能力档案</el-menu-item>
        <el-menu-item index="/factory/demands">浏览需求</el-menu-item>
        <el-menu-item index="/factory/quotations">我的报名</el-menu-item>
        <el-menu-item index="/factory/stages">我的工单</el-menu-item>
        <el-menu-item index="/factory/notifies">通知</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="top">
        <span>{{ title }}</span>
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
  if (route.path.startsWith('/factory/mine')) return '/factory/mine'
  if (route.path.startsWith('/factory/devices')) return '/factory/devices'
  if (route.path.startsWith('/factory/profile')) return '/factory/profile'
  if (route.path.startsWith('/factory/demands')) return '/factory/demands'
  if (route.path.startsWith('/factory/quotations')) return '/factory/quotations'
  if (route.path.startsWith('/factory/stages')) return '/factory/stages'
  if (route.path.startsWith('/factory/notifies')) return '/factory/notifies'
  return '/factory/home'
})
const title = computed(() => route.meta.title || '工厂工作台')
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
.main { background: #f5f7fa; }
</style>
