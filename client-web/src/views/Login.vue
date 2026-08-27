<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <el-tabs v-model="tab">
        <el-tab-pane label="登录" name="login">
          <el-form ref="loginRef" :model="loginForm" :rules="loginRules" label-width="80px">
            <el-form-item label="账号" prop="phone">
              <el-input v-model="loginForm.phone" placeholder="手机号 / admin" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="loginForm.password" type="password" show-password />
            </el-form-item>
            <el-button type="primary" style="width:100%" @click="doLogin">登录</el-button>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="注册" name="register">
          <el-form ref="regRef" :model="regForm" :rules="regRules" label-width="90px">
            <el-form-item label="手机号" prop="phone"><el-input v-model="regForm.phone" maxlength="11" /></el-form-item>
            <el-form-item label="密码" prop="password"><el-input v-model="regForm.password" type="password" show-password /></el-form-item>
            <el-form-item label="企业名称" prop="companyName"><el-input v-model="regForm.companyName" /></el-form-item>
            <el-form-item label="信用代码" prop="creditCode"><el-input v-model="regForm.creditCode" maxlength="18" placeholder="18位统一社会信用代码" /></el-form-item>
            <el-form-item label="联系人" prop="contactName"><el-input v-model="regForm.contactName" /></el-form-item>
            <el-form-item label="企业地址" prop="address"><el-input v-model="regForm.address" /></el-form-item>
            <el-form-item label="企业类型" prop="type">
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
const loginRef = ref()
const regRef = ref()
const loginForm = reactive({ phone: '', password: '' })
const regForm = reactive({ phone: '', password: '', companyName: '', creditCode: '', contactName: '', address: '', type: 'BUYER' })
const loginRules = {
  phone: [{ required: true, message: '请填写账号', trigger: 'blur' }],
  password: [{ required: true, message: '请填写密码', trigger: 'blur' }],
}
const regRules = {
  phone: [
    { required: true, message: '请填写手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '请填写11位手机号', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请填写密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
  companyName: [{ required: true, message: '请填写企业名称', trigger: 'blur' }],
  creditCode: [
    { required: true, message: '请填写信用代码', trigger: 'blur' },
    { min: 18, max: 18, message: '信用代码须为 18 位', trigger: 'blur' },
  ],
  contactName: [{ required: true, message: '请填写联系人', trigger: 'blur' }],
  address: [{ required: true, message: '请填写企业地址', trigger: 'blur' }],
  type: [{ required: true, message: '请选择企业类型', trigger: 'change' }],
}

async function doLogin() {
  await loginRef.value.validate()
  const data = await api.post('/auth/login', loginForm)
  sessionStorage.setItem('token', data.token)
  sessionStorage.setItem('role', data.role)
  sessionStorage.setItem('tenantId', data.tenantId)
  sessionStorage.setItem('name', data.name)
  ElMessage.success('登录成功')
  if (data.role === 'BUYER') router.push('/buyer/home')
  else if (data.role === 'FACTORY') router.push('/factory/home')
  else router.push('/buyer/home')
}

async function doRegister() {
  await regRef.value.validate()
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
