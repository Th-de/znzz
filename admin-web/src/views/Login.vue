<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <div class="brand-login">
        <img src="/logo.png" alt="" />
        <h2 class="site-title">智能制造云平台</h2>
      </div>
      <p class="site-sub">运营 / 质检 管理端</p>
      <el-form :model="form" label-width="60px">
        <el-form-item label="账号"><el-input v-model="form.phone" placeholder="admin" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-button type="primary" style="width:100%" @click="login">登录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'
import { setAuth } from '../utils/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const form = reactive({ phone: 'admin', password: 'admin123' })

async function login() {
  const data = await api.post('/auth/login', form)
  setAuth(data)
  ElMessage.success('登录成功')
  router.push('/admin')
}
</script>

<style scoped>
.login-wrap { height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(180deg, #C1E8FF 0%, #7DA0CA 42%, #052659 100%); }
.login-card { width: 380px; border-radius: 20px; }
.brand-login { display: flex; align-items: center; justify-content: center; gap: 10px; }
.brand-login img { width: 48px; height: 48px; border-radius: 14px; }
.site-title { text-align: center; margin: 4px 0 2px; font-family: "ZCOOL XiaoWei", "Noto Serif SC", "KaiTi", serif; color: #052659; letter-spacing: 2px; }
.site-sub { text-align: center; color: #909399; margin: 0 0 16px; font-size: 13px; }
</style>
