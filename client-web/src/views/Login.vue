<template>
  <div class="login-wrap">
    <div class="ambient" aria-hidden="true">
      <i class="glow glow-a"></i>
      <i class="glow glow-b"></i>
      <i class="grid"></i>
    </div>

    <main class="login-shell">
      <section class="login-story">
        <a class="story-brand" href="/">
          <img src="/logo.png" alt="" />
          <span>智能制造云平台</span>
        </a>
        <div class="story-copy">
          <p class="story-kicker">AETHER MANUFACTURING CLOUD</p>
          <h1>从需求到交付，<br />让制造协同清晰可见</h1>
          <p>连接买家与工厂，以质检和资金托管守住履约结果。</p>
        </div>
        <div class="role-network" aria-hidden="true">
          <div class="network-line"></div>
          <div class="role-node active"><i>01</i><span>买家</span><small>发布需求</small></div>
          <div class="role-node active"><i>02</i><span>工厂</span><small>承接生产</small></div>
          <div class="role-node"><i>03</i><span>质检</span><small>独立判定</small></div>
          <div class="role-node"><i>04</i><span>平台</span><small>托管结算</small></div>
        </div>
        <div class="story-status"><i></i> 制造协同网络运行中</div>
      </section>

      <el-card class="login-card">
        <div class="auth-head">
          <div class="brand-login">
            <img src="/logo.png" alt="" />
            <div>
              <h2 class="site-title">欢迎回来</h2>
              <p class="site-sub">买家与工厂服务门户</p>
            </div>
          </div>
          <a class="back" href="/">返回首页 ↗</a>
        </div>

        <el-tabs v-model="tab" class="auth-tabs">
          <el-tab-pane label="登录" name="login">
            <el-form ref="loginRef" :model="loginForm" :rules="loginRules" label-position="top">
              <el-form-item label="账号" prop="phone">
                <el-input v-model="loginForm.phone" placeholder="请输入手机号" />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input v-model="loginForm.password" type="password" show-password placeholder="请输入密码" />
              </el-form-item>
              <el-button type="primary" class="submit-btn" @click="doLogin">进入工作台</el-button>
            </el-form>
            <p class="auth-note">登录即代表你正在进入受保护的企业工作空间</p>
          </el-tab-pane>

          <el-tab-pane label="企业注册" name="register">
            <el-form ref="regRef" :model="regForm" :rules="regRules" label-position="top" class="register-form">
              <el-form-item label="手机号" prop="phone">
                <el-input v-model="regForm.phone" maxlength="11" placeholder="11 位手机号" @input="onRegPhone" />
              </el-form-item>
              <el-form-item label="密码" prop="password"><el-input v-model="regForm.password" type="password" show-password placeholder="至少 6 位" /></el-form-item>
              <el-form-item label="企业名称" prop="companyName"><el-input v-model="regForm.companyName" placeholder="企业全称" /></el-form-item>
              <el-form-item label="信用代码" prop="creditCode"><el-input v-model="regForm.creditCode" maxlength="18" placeholder="18 位统一社会信用代码" /></el-form-item>
              <el-form-item label="联系人" prop="contactName"><el-input v-model="regForm.contactName" placeholder="联系人姓名" /></el-form-item>
              <el-form-item label="企业地址" prop="address"><el-input v-model="regForm.address" placeholder="所在城市及详细地址" /></el-form-item>
              <el-form-item label="企业类型" prop="type" class="type-field">
                <el-radio-group v-model="regForm.type">
                  <el-radio value="BUYER">买家 / 需求方</el-radio>
                  <el-radio value="FACTORY">工厂 / 供给方</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-button type="primary" class="submit-btn register-submit" @click="doRegister">创建企业账号</el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'
import { ElMessage } from 'element-plus'
import { riseIn } from '../motion/recipes'

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
    { pattern: /^\d{11}$/, message: '手机号必须为11位数字', trigger: ['blur', 'change'] },
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
  sessionStorage.setItem('avatarId', data.avatarId == null ? '' : String(data.avatarId))
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

function onRegPhone(v) {
  regForm.phone = String(v ?? '').replace(/\D/g, '').slice(0, 11)
}

onMounted(() => riseIn('.login-card', { y: 16, duration: 0.55 }))
</script>

<style scoped>
.login-wrap {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  display: grid;
  place-items: center;
  padding: 32px;
  background: #071735;
}
.ambient {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(circle at 18% 20%, rgba(28, 154, 230, 0.32), transparent 32%),
    radial-gradient(circle at 82% 80%, rgba(101, 72, 255, 0.3), transparent 36%),
    linear-gradient(135deg, #071735, #0a315b 48%, #23195b);
}
.glow { position: absolute; border-radius: 50%; filter: blur(80px); }
.glow-a { width: 34vw; height: 34vw; left: 6%; top: 8%; background: rgba(61, 180, 255, 0.18); }
.glow-b { width: 30vw; height: 30vw; right: 8%; bottom: 2%; background: rgba(107, 77, 255, 0.2); }
.grid {
  position: absolute;
  inset: 0;
  opacity: 0.2;
  background-image:
    linear-gradient(rgba(255,255,255,.07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,.07) 1px, transparent 1px);
  background-size: 64px 64px;
  mask-image: linear-gradient(to bottom, #000, transparent 86%);
}
.login-shell {
  position: relative;
  z-index: 1;
  width: min(1120px, 100%);
  min-height: 650px;
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 30px;
  background: rgba(255, 255, 255, 0.08);
  box-shadow: 0 32px 90px rgba(1, 10, 30, 0.42);
  backdrop-filter: blur(30px) saturate(150%);
}
.login-story {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 46px 50px 40px;
  color: #fff;
  overflow: hidden;
}
.login-story::after {
  content: "";
  position: absolute;
  width: 360px;
  height: 360px;
  right: -150px;
  top: -130px;
  border: 1px solid rgba(125, 210, 255, 0.2);
  border-radius: 50%;
  box-shadow: 0 0 0 52px rgba(125, 210, 255, 0.04), 0 0 0 104px rgba(125, 210, 255, 0.025);
}
.story-brand { display: flex; align-items: center; gap: 12px; color: #fff; text-decoration: none; font-weight: 600; letter-spacing: .06em; }
.story-brand img { width: 42px; height: 42px; border-radius: 12px; }
.story-copy { margin: 76px 0 48px; }
.story-kicker { margin: 0 0 16px; color: #7fd8ff; font-size: 11px; font-weight: 600; letter-spacing: .14em; }
.story-copy h1 { margin: 0; font-size: clamp(36px, 4.2vw, 54px); font-weight: 300; line-height: 1.08; letter-spacing: -.035em; }
.story-copy > p:last-child { max-width: 460px; margin: 22px 0 0; color: rgba(255,255,255,.68); font-size: 15px; line-height: 1.7; }
.role-network { position: relative; display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
.network-line { position: absolute; left: 11%; right: 11%; top: 18px; height: 1px; background: linear-gradient(90deg, #65d8ff, #7c6ffe); opacity: .56; }
.role-node { position: relative; z-index: 1; display: flex; flex-direction: column; align-items: center; text-align: center; }
.role-node i { width: 36px; height: 36px; display: grid; place-items: center; border: 1px solid rgba(255,255,255,.26); border-radius: 50%; background: #102b58; color: rgba(255,255,255,.72); font-size: 10px; font-style: normal; }
.role-node.active i { border-color: #72dcff; color: #fff; box-shadow: 0 0 20px rgba(101,216,255,.25); }
.role-node span { margin-top: 10px; font-size: 12px; font-weight: 600; }
.role-node small { margin-top: 4px; color: rgba(255,255,255,.46); font-size: 9px; }
.story-status { margin-top: auto; color: rgba(255,255,255,.58); font-size: 11px; }
.story-status i { display: inline-block; width: 7px; height: 7px; margin-right: 7px; border-radius: 50%; background: #32d296; box-shadow: 0 0 0 4px rgba(50,210,150,.12); }
.login-card {
  width: auto;
  align-self: center;
  height: calc(100% - 56px);
  max-height: calc(100% - 56px);
  margin: 28px 36px 28px 0;
  overflow-y: auto;
  border: 1px solid rgba(255,255,255,.78);
  border-radius: 22px;
  background: rgba(249, 251, 255, 0.92);
  box-shadow: inset 0 1px 0 #fff, 0 18px 46px rgba(3,16,47,.18);
}
.login-card :deep(.el-card__body) { padding: 30px; }
.auth-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 24px; }
.brand-login { display: flex; align-items: center; gap: 12px; }
.brand-login img { width: 44px; height: 44px; border-radius: 13px; }
.site-title { margin: 0 0 4px; color: #0d253d; font-size: 22px; font-weight: 600; letter-spacing: -.02em; }
.site-sub { margin: 0; color: #758197; font-size: 12px; }
.back { color: #533afd; text-decoration: none; font-size: 11px; white-space: nowrap; }
.auth-tabs :deep(.el-tabs__header) { margin-bottom: 28px; }
.auth-tabs :deep(.el-tabs__nav-scroll) { width: 100%; }
.auth-tabs :deep(.el-tabs__nav) { width: 100%; }
.auth-tabs :deep(.el-tabs__item) { flex: 1; }
.auth-tabs :deep(.el-form-item) { margin-bottom: 19px; }
.auth-tabs :deep(.el-form-item__label) { height: auto; margin-bottom: 7px; color: #526077; font-size: 12px; line-height: 1.2; }
.auth-tabs :deep(.el-input__wrapper) { min-height: 42px; border-radius: 11px; }
.submit-btn { width: 100%; height: 42px; margin-top: 4px; font-weight: 600; box-shadow: 0 8px 20px rgba(83,58,253,.2); }
.auth-note { margin: 18px 0 0; color: #8993a4; font-size: 10px; text-align: center; }
.register-form { display: grid; grid-template-columns: 1fr 1fr; gap: 0 14px; }
.register-form .type-field,
.register-submit { grid-column: 1 / -1; }
.type-field :deep(.el-radio-group) { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; width: 100%; }
.type-field :deep(.el-radio) { height: 38px; margin: 0; padding: 0 12px; border: 1px solid #dde2ee; border-radius: 10px; background: #fff; }
.type-field :deep(.el-radio.is-checked) { border-color: #7c6ffe; background: #f3f1ff; }
@media (max-width: 860px) {
  .login-wrap { padding: 18px; }
  .login-shell { grid-template-columns: 1fr; min-height: auto; }
  .login-story { display: none; }
  .login-card { margin: 16px; max-height: calc(100vh - 36px); }
}
@media (max-width: 520px) {
  .register-form { grid-template-columns: 1fr; }
  .register-form .type-field,
  .register-submit { grid-column: auto; }
  .login-card :deep(.el-card__body) { padding: 24px 20px; }
}
@media (prefers-reduced-transparency: reduce) {
  .login-shell { background: #0a315b; backdrop-filter: none; }
  .login-card { background: #fff; }
}
</style>
