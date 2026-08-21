<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h3 style="text-align:center">运营/质检 登录</h3>
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
import { ElMessage } from 'element-plus'

const router = useRouter()
const form = reactive({ phone: 'admin', password: 'admin123' })

async function login() {
  const data = await api.post('/auth/login', form)
  localStorage.setItem('token', data.token)
  localStorage.setItem('role', data.role)
  localStorage.setItem('tenantId', data.tenantId)
  ElMessage.success('登录成功')
  router.push('/admin')
}
</script>

<style scoped>
.login-wrap { height: 100vh; display: flex; align-items: center; justify-content: center; }
.login-card { width: 380px; }
</style>
