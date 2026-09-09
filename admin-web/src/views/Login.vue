<template>
  <div class="login-wrap">
    <div class="ambient" aria-hidden="true">
      <i class="glow glow-a"></i>
      <i class="glow glow-b"></i>
      <i class="grid"></i>
    </div>

    <main class="login-shell">
      <section class="login-story">
        <div class="story-brand">
          <img src="/logo.png" alt="" />
          <span>智能制造云平台</span>
        </div>
        <div class="story-copy">
          <p>OPERATIONS & INSPECTION</p>
          <h1>让每一次审核，<br />都对应真实的履约结果</h1>
          <span>运营监管流程，质检独立判定，资金按照确认结果执行。</span>
        </div>
        <div class="role-network" aria-hidden="true">
          <div class="network-line"></div>
          <div class="role-node"><i>01</i><b>买家</b><small>需求</small></div>
          <div class="role-node"><i>02</i><b>工厂</b><small>生产</small></div>
          <div class="role-node active"><i>03</i><b>质检</b><small>判定</small></div>
          <div class="role-node active"><i>04</i><b>平台</b><small>监管</small></div>
        </div>
        <div class="story-status"><i></i> 风险控制与质检链路在线</div>
      </section>

      <el-card class="login-card">
        <div class="brand-login">
          <img src="/logo.png" alt="" />
          <div>
            <h2 class="site-title">管理控制中心</h2>
            <p class="site-sub">平台运营与独立质检入口</p>
          </div>
        </div>
        <div class="access-badges">
          <span>平台运营</span>
          <span>独立质检</span>
        </div>
        <el-form :model="form" label-position="top" class="auth-form">
          <el-form-item label="管理账号"><el-input v-model="form.phone" placeholder="请输入账号" /></el-form-item>
          <el-form-item label="登录密码"><el-input v-model="form.password" type="password" show-password placeholder="请输入密码" /></el-form-item>
          <el-button type="primary" class="submit-btn" @click="login">进入控制中心</el-button>
        </el-form>
        <p class="auth-note">系统将根据账号权限自动进入运营端或质检端</p>
      </el-card>
    </main>
  </div>
</template>

<script setup>
import { reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'
import { setAuth } from '../utils/auth'
import { ElMessage } from 'element-plus'
import { riseIn } from '../motion/recipes'

const router = useRouter()
const form = reactive({ phone: 'admin', password: 'admin123' })

async function login() {
  const data = await api.post('/auth/login', form)
  setAuth(data)
  ElMessage.success('登录成功')
  router.push('/admin')
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
    radial-gradient(circle at 15% 18%, rgba(28, 154, 230, 0.3), transparent 31%),
    radial-gradient(circle at 84% 78%, rgba(101, 72, 255, 0.32), transparent 36%),
    linear-gradient(135deg, #071735, #0a315b 48%, #23195b);
}
.glow { position: absolute; border-radius: 50%; filter: blur(80px); }
.glow-a { width: 34vw; height: 34vw; left: 5%; top: 5%; background: rgba(61,180,255,.18); }
.glow-b { width: 30vw; height: 30vw; right: 7%; bottom: 0; background: rgba(107,77,255,.2); }
.grid {
  position: absolute;
  inset: 0;
  opacity: .2;
  background-image: linear-gradient(rgba(255,255,255,.07) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.07) 1px, transparent 1px);
  background-size: 64px 64px;
  mask-image: linear-gradient(to bottom, #000, transparent 86%);
}
.login-shell {
  position: relative;
  z-index: 1;
  width: min(1080px, 100%);
  min-height: 620px;
  display: grid;
  grid-template-columns: 1.15fr .85fr;
  overflow: hidden;
  border: 1px solid rgba(255,255,255,.2);
  border-radius: 30px;
  background: rgba(255,255,255,.08);
  box-shadow: 0 32px 90px rgba(1,10,30,.42);
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
  border: 1px solid rgba(125,210,255,.2);
  border-radius: 50%;
  box-shadow: 0 0 0 52px rgba(125,210,255,.04), 0 0 0 104px rgba(125,210,255,.025);
}
.story-brand { display: flex; align-items: center; gap: 12px; font-weight: 600; letter-spacing: .06em; }
.story-brand img { width: 42px; height: 42px; border-radius: 12px; }
.story-copy { margin: 82px 0 54px; }
.story-copy > p { margin: 0 0 16px; color: #7fd8ff; font-size: 11px; font-weight: 600; letter-spacing: .14em; }
.story-copy h1 { margin: 0; font-size: clamp(36px, 4vw, 52px); font-weight: 300; line-height: 1.08; letter-spacing: -.035em; }
.story-copy > span { display: block; max-width: 460px; margin-top: 22px; color: rgba(255,255,255,.68); font-size: 15px; line-height: 1.7; }
.role-network { position: relative; display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
.network-line { position: absolute; left: 11%; right: 11%; top: 18px; height: 1px; background: linear-gradient(90deg, #4e7299, #65d8ff, #7c6ffe); opacity: .68; }
.role-node { position: relative; z-index: 1; display: flex; flex-direction: column; align-items: center; }
.role-node i { width: 36px; height: 36px; display: grid; place-items: center; border: 1px solid rgba(255,255,255,.24); border-radius: 50%; background: #102b58; color: rgba(255,255,255,.68); font-size: 10px; font-style: normal; }
.role-node.active i { border-color: #72dcff; color: #fff; box-shadow: 0 0 20px rgba(101,216,255,.25); }
.role-node b { margin-top: 10px; font-size: 12px; font-weight: 600; }
.role-node small { margin-top: 4px; color: rgba(255,255,255,.46); font-size: 9px; }
.story-status { margin-top: auto; color: rgba(255,255,255,.58); font-size: 11px; }
.story-status i { display: inline-block; width: 7px; height: 7px; margin-right: 7px; border-radius: 50%; background: #32d296; box-shadow: 0 0 0 4px rgba(50,210,150,.12); }
.login-card {
  width: auto;
  align-self: center;
  margin: 14px;
  border: 1px solid rgba(255,255,255,.78);
  border-radius: 22px;
  background: rgba(249,251,255,.92);
  box-shadow: inset 0 1px 0 #fff, 0 18px 46px rgba(3,16,47,.18);
}
.login-card :deep(.el-card__body) { padding: 34px; }
.brand-login { display: flex; align-items: center; gap: 12px; }
.brand-login img { width: 46px; height: 46px; border-radius: 13px; }
.site-title { margin: 0 0 5px; color: #0d253d; font-size: 22px; font-weight: 600; letter-spacing: -.02em; }
.site-sub { margin: 0; color: #758197; font-size: 12px; }
.access-badges { display: flex; gap: 8px; margin: 26px 0; }
.access-badges span { padding: 6px 10px; border: 1px solid rgba(83,58,253,.12); border-radius: 999px; background: #f2f0ff; color: #4b3ed0; font-size: 10px; font-weight: 600; }
.auth-form :deep(.el-form-item) { margin-bottom: 20px; }
.auth-form :deep(.el-form-item__label) { height: auto; margin-bottom: 7px; color: #526077; font-size: 12px; line-height: 1.2; }
.auth-form :deep(.el-input__wrapper) { min-height: 42px; border-radius: 11px; }
.submit-btn { width: 100%; height: 42px; margin-top: 4px; font-weight: 600; box-shadow: 0 8px 20px rgba(83,58,253,.2); }
.auth-note { margin: 18px 0 0; color: #8993a4; font-size: 10px; text-align: center; }
@media (max-width: 820px) {
  .login-wrap { padding: 18px; }
  .login-shell { grid-template-columns: 1fr; min-height: auto; }
  .login-story { display: none; }
  .login-card { margin: 0; }
}
@media (prefers-reduced-transparency: reduce) {
  .login-shell { background: #0a315b; backdrop-filter: none; }
  .login-card { background: #fff; }
}
</style>
