<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <el-tabs v-model="tab">
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm" label-width="80px">
            <el-form-item label="账号">
              <el-input v-model="loginForm.phone" placeholder="手机号 / admin" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="loginForm.password" type="password" show-password />
            </el-form-item>
            <el-button type="primary" style="width:100%" @click="doLogin">登录</el-button>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="注册" name="register">
          <el-form :model="regForm" label-width="90px">
            <el-form-item label="手机号"><el-input v-model="regForm.phone" /></el-form-item>
            <el-form-item label="密码"><el-input v-model="regForm.password" type="password" show-password /></el-form-item>
            <el-form-item label="企业名称"><el-input v-model="regForm.companyName" /></el-form-item>
            <el-form-item label="信用代码"><el-input v-model="regForm.creditCode" /></el-form-item>
            <el-form-item label="联系人"><el-input v-model="regForm.contactName" /></el-form-item>
            <el-form-item label="企业类型">
              <el-radio-group v-model="regForm.type">
                <el-radio value="BUYER">买家</el-radio>
                <el-radio value="FACTORY">工厂</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-button type="success" style="width:100%" @click="doRegister">注册</el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const tab = ref('login')
const loginForm = reactive({ phone: '', password: '' })
const regForm = reactive({ phone: '', password: '', companyName: '', creditCode: '', contactName: '', type: 'BUYER' })

async function doLogin() {
  const data = await api.post('/auth/login', loginForm)
  localStorage.setItem('token', data.token)
  localStorage.setItem('role', data.role)
  localStorage.setItem('tenantId', data.tenantId)
  localStorage.setItem('name', data.name)
  ElMessage.success('登录成功')
  if (data.role === 'BUYER') router.push('/buyer/home')
  else if (data.role === 'FACTORY') router.push('/factory/home')
  else router.push('/buyer/home')
}

async function doRegister() {
  await api.post('/auth/register', regForm)
  ElMessage.success('注册成功，请登录')
  tab.value = 'login'
  loginForm.phone = regForm.phone
}
</script>

<style scoped>
.login-wrap { height: 100vh; display: flex; align-items: center; justify-content: center; }
.login-card { width: 420px; }
</style>
