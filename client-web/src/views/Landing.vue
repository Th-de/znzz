<template>
  <div class="land">
    <div ref="vantaEl" class="vanta-bg" aria-hidden="true"></div>
    <div class="hero-glow" aria-hidden="true"></div>

    <nav class="global-nav" :class="{ hide: navHidden }">
      <a class="brand" href="#top" @click.prevent="go('#top')">
        <img src="/logo.png" alt="" />
        <span>智能制造云平台</span>
      </a>
      <div class="nav-links">
        <a href="#ideas" @click.prevent="go('#ideas')">理念</a>
        <a href="#caps" @click.prevent="go('#caps')">能力</a>
        <a href="#flow" @click.prevent="go('#flow')">链路</a>
        <a href="#data" @click.prevent="go('#data')">数据</a>
      </div>
    </nav>

    <section id="top" class="hero">
      <p class="eyebrow rise">智能制造云平台</p>
      <h1 class="rise">把分散的制造协作，<br />变成可验证的履约链路</h1>
      <p class="lead rise">从需求发布、整品分配到独立质检与资金结算<br />四方遵循同一套规则，每一步有记录，每一笔资金有依据。</p>
      <div class="cta rise">
        <button type="button" class="pill" @click="$router.push('/login')">进入平台</button>
      </div>
    </section>

    <section class="scene scene-hero">
      <div class="scene-photo">
        <img class="scene-media" src="https://images.unsplash.com/photo-1581091226825-a6a2a5aee158?auto=format&fit=crop&w=1800&q=80" alt="" />
        <div class="scene-wash wash-blue"></div>
        <div class="scene-note">
          <span>真实制造场景</span>
          <strong>设备、订单与履约状态，在同一条链路里同步</strong>
        </div>
      </div>
      <figure class="product" aria-hidden="true">
        <div class="mock">
          <div class="mock-bar">
            <img src="/logo.png" alt="" />
            <div class="mock-brand">
              <b>智能制造云平台</b>
              <em>协同控制台</em>
            </div>
            <div class="mock-nav">
              <span class="on">全局态势</span>
              <span>履约网络</span>
              <span>资金安全</span>
            </div>
            <span class="mock-live"><i></i>全链路在线</span>
          </div>
          <div class="mock-body">
            <div class="mock-heading">
              <div>
                <span>正在履约</span>
                <strong>铝合金壳体精密制造</strong>
              </div>
              <em>订单 #A-260908</em>
            </div>

            <div class="command-grid">
              <div class="route-card">
                <div class="route-head">
                  <span>制造主线</span>
                  <b>68%</b>
                </div>
                <div class="route-line">
                  <div class="route-step done"><i>01</i><span>需求确认</span></div>
                  <div class="route-step done"><i>02</i><span>工厂匹配</span></div>
                  <div class="route-step active"><i>03</i><span>分期生产</span></div>
                  <div class="route-step"><i>04</i><span>质检结算</span></div>
                </div>
                <div class="factory-row">
                  <div><i class="factory-dot cyan"></i><span>华东精工</span><b>320 件</b></div>
                  <div><i class="factory-dot violet"></i><span>宁波智造</span><b>180 件</b></div>
                </div>
              </div>

              <div class="signal-card">
                <span class="signal-label">协同健康度</span>
                <div class="signal-orbit">
                  <i class="orbit-ring ring-one"></i>
                  <i class="orbit-ring ring-two"></i>
                  <div class="signal-core"><b>96</b><span>稳定</span></div>
                  <i class="orbit-node node-one"></i>
                  <i class="orbit-node node-two"></i>
                  <i class="orbit-node node-three"></i>
                </div>
              </div>
            </div>

            <div class="command-footer">
              <div><span>托管资金</span><b>¥ 428,600</b></div>
              <div><span>当前工序</span><b>五轴精铣</b></div>
              <div><span>质检闸门</span><b class="safe">已就绪</b></div>
            </div>
          </div>
        </div>
      </figure>
    </section>

    <section id="ideas" class="band">
      <p class="eyebrow">商业理念</p>
      <h2>把不确定，拆成可执行的步骤</h2>
      <ul class="ideas">
        <li v-for="m in mechanisms" :key="m.title" class="idea pane">
          <strong>{{ m.title }}</strong>
          <p>{{ m.desc }}</p>
        </li>
      </ul>
    </section>

    <section class="scene scene-slim scene-quality">
      <img class="scene-media" src="https://images.unsplash.com/photo-1581092918056-0c4c3acd3789?auto=format&fit=crop&w=1800&q=80" alt="" />
      <div class="scene-wash wash-cyan"></div>
      <div class="scene-copy">
        <span>独立质检</span>
        <h3>结论与资金分离，责任边界清晰</h3>
        <p>质检负责判断，运营负责审核，平台只执行已确认的结果。</p>
      </div>
    </section>

    <section id="data" class="band">
      <p class="eyebrow">我们致力于什么</p>
      <h2>让每一笔履约，都能被看见、被核对、被结算</h2>
      <p class="mission">平台不替任何一方做决定。买家发布、工厂承接、质检判定、运营审核，角色边界清楚；资金只执行已经被确认的结果。</p>
      <div class="stats">
        <div class="pane">
          <b><CountUp prefix="¥" :value="12800000" /></b>
          <span>演示 · 托管资金</span>
        </div>
        <div class="pane">
          <b><CountUp :value="1864" /></b>
          <span>演示 · 履约工单</span>
        </div>
        <div class="pane">
          <b><CountUp :value="98.6" suffix="%" /></b>
          <span>演示 · 准时率</span>
        </div>
      </div>
    </section>

    <section id="caps" class="band">
      <p class="eyebrow">四端能力</p>
      <h2>同一套规则，四个入口</h2>
      <div class="caps">
        <article
          v-for="(p, i) in portals"
          :key="p.en"
          ref="portalEls"
          class="cap spot"
          :class="p.tone"
        >
          <span class="pane-no">{{ String(i + 1).padStart(2, '0') }}</span>
          <p class="en">{{ p.en }}</p>
          <h3>{{ p.name }}</h3>
          <p class="tag">{{ p.desc }}</p>
          <div class="mini">{{ p.mock }}</div>
          <button type="button" class="arrow" @click="enter(p)">{{ p.cta }} →</button>
        </article>
      </div>
    </section>

    <section class="scene scene-slim scene-fulfil">
      <img class="scene-media" src="/factory-flow-transition.jpg" alt="" />
      <div class="scene-wash wash-amber"></div>
      <div class="scene-copy">
        <span>制造现场</span>
        <h3>确认之后，每一步都有据可查</h3>
        <p>从签约、生产到交付，让现场进度自然过渡到平台履约链路。</p>
      </div>
    </section>

    <section id="flow" class="band">
      <p class="eyebrow">履约链路</p>
      <h2>八步，从发布到结算</h2>
      <div class="flow-wrap">
        <FlowMap :steps="steps" />
      </div>
    </section>

    <section class="close">
      <h2>现在就开始一次确定的制造</h2>
      <button type="button" class="pill" @click="$router.push('/login')">进入平台</button>
    </section>

    <footer class="foot">
      <div>
        <b>智能制造云平台</b>
        <p>买家 / 工厂 / 运营 / 质检</p>
      </div>
      <div class="ports">
        <a href="/login">买家 · 工厂 127.0.0.1:5173</a>
        <a href="http://127.0.0.1:5174/" target="_blank" rel="noreferrer">运营 · 质检 127.0.0.1:5174</a>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import FlowMap from '../components/FlowMap.vue'
import CountUp from '../components/CountUp.vue'
import { motionReady, prefersReducedMotion } from '../motion/core'
import { startLandingScroll } from '../motion/lenisLanding'
import { attachMagnet, attachSpotlight, riseIn } from '../motion/recipes'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

const router = useRouter()
const portalEls = ref([])
const navHidden = ref(false)
const steps = ['发布需求', '意向报名', '思考期报价', '方案确认', '分厂签约', '分期生产', '质检托管', '完工结算']
const mechanisms = [
  { title: '两阶段竞标', desc: '意向金降低门槛，思考期锁价。先占位，再承诺，避免一上来就押满保证金。' },
  { title: '整品分配', desc: '一厂一个单价，承接全部工序。件数落在区间并凑齐总量，不把一件产品拆给多家。' },
  { title: '判定与放款分离', desc: '质检只填结论，运营只审核，资金只执行结果。任何一端都不能同时当裁判和出纳。' },
  { title: '托管后结算', desc: '合格工钱先入平台；完工按约定佣金分摊。钱跟着被确认的状态走，而不是跟着口头约定走。' },
]
const portals = [
  { en: 'BUYER', name: '买家', tone: 'tone-buyer', desc: '发布规格、确认方案、签约托管、验收结算。', cta: '进入买家门户', to: '/login', mock: '需求表 · 方案确认 · 托管余额' },
  { en: 'FACTORY', name: '工厂', tone: 'tone-factory', desc: '浏览需求、意向报名、锁价报工、按期交付。', cta: '进入工厂门户', to: '/login', mock: '报名队列 · 工期报工 · 设备档案' },
  { en: 'OPS', name: '运营', tone: 'tone-ops', desc: '审方案与合同，监管资金，不改质检判定。', cta: '打开指挥台', href: 'http://127.0.0.1:5174/', mock: '审核队列 · 资金总览 · 合同状态' },
  { en: 'INSPECT', name: '质检', tone: 'tone-inspect', desc: '独立填报结论。未见费工单不可见。', cta: '打开指挥台', href: 'http://127.0.0.1:5174/', mock: '抽检计划 · 合格判定 · 未见费屏蔽' },
]

function enter(p) {
  if (p.href) window.open(p.href, '_blank', 'noreferrer')
  else router.push(p.to)
}

const vantaEl = ref(null)
let vantaEffect = null
let stopScroll = () => {}
const magnets = []
let lenisRef = null
let lastY = 0

async function startWaves() {
  if (prefersReducedMotion() || !vantaEl.value) return
  try {
    const THREE = await import('three')
    const WAVES = (await import('vanta/dist/vanta.waves.min')).default
    vantaEffect = WAVES({
      el: vantaEl.value,
      THREE,
      mouseControls: true,
      touchControls: true,
      gyroControls: false,
      minHeight: 200,
      minWidth: 200,
      scale: 1,
      scaleMobile: 1,
      color: 0x041939,
      shininess: 30,
      waveHeight: 15,
      waveSpeed: 1,
      zoom: 1,
    })
  } catch {
    vantaEffect = null
  }
}

function go(id) {
  if (lenisRef) lenisRef.scrollTo(id, { offset: -24 })
  else document.querySelector(id)?.scrollIntoView({ behavior: 'smooth' })
}

function onScrollPos() {
  const y = lenisRef?.scroll ?? window.scrollY
  if (y > lastY + 8 && y > 80) navHidden.value = true
  else if (y < lastY - 8) navHidden.value = false
  lastY = y
}

onMounted(async () => {
  const gsap = motionReady()
  const scroller = startLandingScroll(() => {
    ScrollTrigger.update()
    onScrollPos()
  })
  lenisRef = scroller.lenis
  stopScroll = scroller.stop
  if (!lenisRef) window.addEventListener('scroll', onScrollPos, { passive: true })

  riseIn('.hero .rise', { stagger: 0.1, y: 28 })
  await nextTick()
  await startWaves()

  if (!prefersReducedMotion()) {
    gsap.fromTo('.product', { opacity: 0, y: 28 }, {
      opacity: 1, y: 0, duration: 0.7, delay: 0.15, ease: 'power2.out',
    })
    document.querySelectorAll('.scene-media').forEach((el) => {
      gsap.fromTo(el, { yPercent: -8 }, {
        yPercent: 8,
        ease: 'none',
        scrollTrigger: { trigger: el.parentElement, start: 'top bottom', end: 'bottom top', scrub: true },
      })
    })
    document.querySelectorAll('.scene-slim').forEach((el) => {
      const cap = el.querySelector('.scene-copy')
      if (!cap) return
      gsap.fromTo(cap, { opacity: 0, y: 18 }, {
        opacity: 1,
        y: 0,
        duration: 0.55,
        ease: 'power2.out',
        scrollTrigger: { trigger: el, start: 'top 78%' },
      })
    })
    gsap.fromTo('.idea', { opacity: 0, y: 18 }, {
      opacity: 1,
      y: 0,
      duration: 0.55,
      stagger: 0.08,
      ease: 'power2.out',
      scrollTrigger: { trigger: '#ideas', start: 'top 82%' },
    })
  }

  await nextTick()
  for (const card of portalEls.value || []) {
    magnets.push(attachMagnet(card, 8))
    magnets.push(attachSpotlight(card))
  }
})

onUnmounted(() => {
  stopScroll()
  window.removeEventListener('scroll', onScrollPos)
  magnets.forEach(off => off && off())
  ScrollTrigger.getAll().forEach(t => t.kill())
  vantaEffect?.destroy()
  vantaEffect = null
})
</script>

<style scoped>
.land {
  min-height: 100vh;
  color: #0d253d;
  position: relative;
  overflow-x: hidden;
  background: #005588;
  font-family: Inter, "SF Pro Display", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
  font-feature-settings: "ss01";
}

.vanta-bg {
  position: absolute;
  inset: 0 0 auto;
  height: max(145vh, 1180px);
  z-index: 0;
  background: #005588;
}
.hero-glow {
  position: absolute;
  top: 12vh;
  left: 50%;
  width: 72vw;
  height: 46vh;
  transform: translateX(-50%);
  pointer-events: none;
  z-index: 1;
  background: radial-gradient(ellipse at center, rgba(120, 196, 255, 0.46), rgba(0, 85, 136, 0.08) 50%, transparent 74%);
}

.global-nav {
  position: sticky;
  top: 14px;
  z-index: 8;
  margin: 14px auto 0;
  width: min(1080px, calc(100% - 32px));
  height: 52px;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 16px;
  padding: 0 10px 0 12px;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.42);
  backdrop-filter: blur(40px) saturate(200%);
  -webkit-backdrop-filter: blur(40px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.55);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9), 0 8px 28px rgba(13, 37, 61, 0.1);
  transition: transform 0.35s cubic-bezier(0.23, 1, 0.32, 1), opacity 0.35s ease;
}
.global-nav.hide {
  transform: translateY(-120%);
  opacity: 0;
  pointer-events: none;
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-self: start;
  color: #0d253d;
  text-decoration: none;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.04em;
}
.brand img { width: 32px; height: 32px; border-radius: 8px; object-fit: cover; }
.nav-links { display: flex; align-items: center; gap: 22px; justify-self: center; }
.nav-links a {
  color: #0d253d;
  text-decoration: none;
  font-size: 16px;
  font-weight: 500;
}
.nav-links a:active { transform: scale(0.97); }
.global-nav .pill { padding: 8px 16px; font-size: 14px; }

.hero {
  position: relative;
  z-index: 2;
  padding: 11vh 24px 48px;
  text-align: center;
}
.hero .eyebrow,
.hero h1 {
  color: #fff;
}
.hero .lead {
  color: rgba(255, 255, 255, 0.78);
}
.eyebrow {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 500;
  letter-spacing: 0.04em;
  color: #0d253d;
}
h1 {
  margin: 0 0 20px;
  font-size: clamp(36px, 6vw, 56px);
  font-weight: 300;
  line-height: 1.03;
  letter-spacing: -0.02em;
}
.lead {
  margin: 0 auto 28px;
  max-width: 560px;
  font-size: 16px;
  font-weight: 400;
  line-height: 1.5;
  color: #273951;
}
h2 {
  margin: 0 0 36px;
  font-size: clamp(28px, 4vw, 40px);
  font-weight: 300;
  line-height: 1.12;
  letter-spacing: -0.02em;
}
.cta {
  position: relative;
  z-index: 3;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.pill {
  appearance: none;
  border: 0;
  background: #533afd;
  color: #fff;
  border-radius: 9999px;
  padding: 8px 16px;
  font: 400 16px/1 inherit;
  cursor: pointer;
  transition: transform 100ms ease-out, background-color 160ms ease;
}
.pill:hover { background: #665efd; }
.pill:active { transform: scale(0.97); background: #2e2b8c; }
.ghost {
  appearance: none;
  border: 1px solid rgba(255, 255, 255, 0.62);
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  border-radius: 9999px;
  padding: 8px 16px;
  font: 400 16px/1 inherit;
  cursor: pointer;
  text-decoration: none;
  transition: transform 100ms ease-out;
}
.ghost:active { transform: scale(0.97); }
.text-link {
  appearance: none;
  border: 0;
  background: none;
  color: #533afd;
  font: 400 14px/1 inherit;
  cursor: pointer;
  padding: 0;
}
.text-link:active { transform: scale(0.97); }

.scene {
  position: relative;
  z-index: 3;
  overflow: hidden;
}
.scene-hero {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(520px, 1.1fr);
  align-items: center;
  gap: 28px;
  min-height: auto;
  padding: 72px max(24px, calc((100vw - 1180px) / 2));
  background:
    radial-gradient(circle at 80% 35%, rgba(105, 99, 255, 0.18), transparent 34%),
    linear-gradient(180deg, rgba(0, 54, 96, 0.08), rgba(12, 34, 82, 0.2));
}
.scene-hero::after {
  content: "";
  position: absolute;
  inset: auto 0 0;
  height: 120px;
  z-index: 1;
  pointer-events: none;
  background: linear-gradient(180deg, transparent, #e8ecfc);
}
.scene-slim {
  min-height: 48vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
.scene-photo {
  position: relative;
  min-height: 500px;
  overflow: hidden;
  border-radius: 28px;
  box-shadow: 0 30px 80px rgba(3, 24, 54, 0.28);
}
.scene-media {
  position: absolute;
  inset: -12% 0;
  width: 100%;
  height: 124%;
  object-fit: cover;
  z-index: 0;
}
.scene-wash {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
}
.wash-blue {
  background: linear-gradient(180deg, rgba(0, 44, 86, 0.08), rgba(0, 85, 136, 0.18) 55%, rgba(9, 34, 70, 0.7));
}
.wash-cyan {
  background:
    linear-gradient(180deg, #e8ecfc 0%, transparent 18%, transparent 78%, #e8ecfc 100%),
    linear-gradient(90deg, rgba(8, 39, 75, 0.84), rgba(0, 85, 136, 0.38) 56%, rgba(83, 58, 253, 0.18));
}
.wash-amber {
  background:
    linear-gradient(180deg, #e8ecfc 0%, transparent 22%, transparent 72%, #e8ecfc 100%),
    linear-gradient(90deg, rgba(8, 39, 75, 0.72), rgba(0, 85, 136, 0.16) 58%, rgba(83, 58, 253, 0.12));
}
.scene-fulfil {
  background: #e8ecfc;
}
.scene-fulfil .scene-media {
  inset: -15% 0;
  height: 130%;
  opacity: 0.94;
  filter: blur(1.5px) saturate(1.08) contrast(1.02);
  -webkit-mask-image: linear-gradient(to bottom, transparent 0%, #000 20%, #000 72%, transparent 100%);
  mask-image: linear-gradient(to bottom, transparent 0%, #000 20%, #000 72%, transparent 100%);
}
.scene-note {
  position: absolute;
  z-index: 2;
  left: 28px;
  right: 28px;
  bottom: 26px;
  color: #fff;
}
.scene-note span,
.scene-copy span {
  display: block;
  margin-bottom: 10px;
  color: #9ed9ff;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.scene-note strong {
  display: block;
  max-width: 440px;
  font-size: 22px;
  font-weight: 400;
  line-height: 1.35;
}
.scene-copy {
  position: relative;
  z-index: 2;
  width: min(1080px, calc(100% - 48px));
  margin: 0 auto;
  color: #fff;
  text-shadow: 0 8px 28px rgba(13, 37, 61, 0.35);
}
.scene-copy h3 {
  max-width: 620px;
  margin: 0 0 14px;
  font-size: clamp(28px, 4.2vw, 48px);
  font-weight: 300;
  line-height: 1.08;
  letter-spacing: -0.025em;
}
.scene-copy p {
  max-width: 540px;
  margin: 0;
  color: rgba(255, 255, 255, 0.8);
  font-size: 15px;
  line-height: 1.65;
}
.product {
  margin: 0;
  width: 100%;
  position: relative;
  z-index: 4;
}
.mock {
  text-align: left;
  border-radius: 22px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(48px) saturate(200%);
  -webkit-backdrop-filter: blur(48px) saturate(200%);
  border: 1px solid rgba(255, 255, 255, 0.58);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.9),
    inset 0 -1px 0 rgba(13, 37, 61, 0.05),
    0 22px 56px rgba(13, 37, 61, 0.14);
}
.mock-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.22);
  border-bottom: 1px solid rgba(255, 255, 255, 0.35);
}
.mock-bar img { width: 36px; height: 36px; border-radius: 10px; object-fit: cover; }
.mock-brand { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.mock-brand b { font-size: 14px; font-weight: 600; color: #0d253d; line-height: 1; }
.mock-brand em { font-style: normal; font-size: 11px; font-weight: 500; color: #273951; }
.mock-nav { display: flex; gap: 16px; margin-left: 18px; flex: 1; }
.mock-nav span { font-size: 13px; font-weight: 500; color: #273951; }
.mock-nav .on { color: #0d253d; font-weight: 700; box-shadow: inset 0 -2px 0 #533afd; padding-bottom: 4px; }
.mock-live {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #273951;
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
}
.mock-live i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #1cbf8b;
  box-shadow: 0 0 0 4px rgba(28, 191, 139, 0.12);
}
.mock-body { padding: 20px; }
.mock-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.mock-heading span,
.route-head span,
.signal-label,
.command-footer span {
  display: block;
  color: #6f7c91;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.08em;
}
.mock-heading strong {
  display: block;
  margin-top: 5px;
  color: #0d253d;
  font-size: 18px;
  font-weight: 600;
}
.mock-heading em {
  color: #533afd;
  font-size: 11px;
  font-style: normal;
  font-weight: 600;
}
.command-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 168px;
  gap: 12px;
}
.route-card,
.signal-card {
  border: 1px solid rgba(83, 58, 253, 0.09);
  border-radius: 15px;
  background: rgba(255, 255, 255, 0.66);
}
.route-card { padding: 16px; }
.route-head { display: flex; align-items: center; justify-content: space-between; }
.route-head b { color: #533afd; font-size: 18px; font-weight: 600; }
.route-line {
  position: relative;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin: 22px 0 18px;
}
.route-line::before {
  content: "";
  position: absolute;
  top: 13px;
  left: 12%;
  right: 12%;
  height: 2px;
  background: linear-gradient(90deg, #533afd 0 62%, #d9def0 62%);
}
.route-step {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
  color: #8a94a5;
  font-size: 9px;
}
.route-step i {
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  border: 2px solid #d9def0;
  border-radius: 50%;
  background: #fff;
  color: #8a94a5;
  font-size: 8px;
  font-style: normal;
}
.route-step.done i {
  border-color: #533afd;
  background: #533afd;
  color: #fff;
}
.route-step.active { color: #0d253d; font-weight: 600; }
.route-step.active i {
  border-color: #533afd;
  color: #533afd;
  box-shadow: 0 0 0 5px rgba(83, 58, 253, 0.1);
}
.factory-row { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.factory-row > div {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 9px 10px;
  border-radius: 10px;
  background: #f5f7fd;
  font-size: 9px;
}
.factory-row span { flex: 1; color: #526077; }
.factory-row b { color: #0d253d; font-size: 9px; font-weight: 600; }
.factory-dot { width: 7px; height: 7px; border-radius: 50%; }
.factory-dot.cyan { background: #16b8d4; }
.factory-dot.violet { background: #7c6ffe; }
.signal-card {
  position: relative;
  overflow: hidden;
  padding: 16px;
  background: linear-gradient(145deg, #171b52, #342575);
}
.signal-label { color: rgba(255, 255, 255, 0.62); }
.signal-orbit {
  position: relative;
  width: 126px;
  height: 126px;
  margin: 8px auto 0;
  display: grid;
  place-items: center;
}
.orbit-ring,
.orbit-node { position: absolute; border-radius: 50%; }
.orbit-ring { border: 1px solid rgba(155, 203, 255, 0.28); }
.ring-one {
  inset: 12px;
  border-top-color: #76ddff;
  animation: orbit-spin 8s linear infinite;
}
.ring-two {
  inset: 27px;
  border-color: rgba(196, 181, 253, 0.38);
  border-bottom-color: #c4b5fd;
  animation: orbit-spin 6s linear infinite reverse;
}
.signal-core {
  width: 58px;
  height: 58px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  background: radial-gradient(circle at 35% 25%, #65d8ff, #533afd 68%);
  box-shadow: 0 0 28px rgba(101, 216, 255, 0.34);
}
.signal-core b { font-size: 20px; line-height: 1; font-weight: 500; }
.signal-core span { margin-top: 4px; font-size: 8px; opacity: 0.76; }
.orbit-node { width: 7px; height: 7px; background: #76ddff; box-shadow: 0 0 9px #76ddff; }
.node-one { top: 8px; left: 58px; }
.node-two { right: 9px; bottom: 34px; background: #c4b5fd; }
.node-three { left: 18px; bottom: 19px; background: #fff; }
.command-footer {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  margin-top: 12px;
  overflow: hidden;
  border-radius: 12px;
  background: rgba(83, 58, 253, 0.08);
}
.command-footer > div { padding: 12px 14px; background: rgba(255, 255, 255, 0.7); }
.command-footer b {
  display: block;
  margin-top: 5px;
  color: #0d253d;
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.command-footer .safe { color: #168760; }

@keyframes orbit-spin {
  to { transform: rotate(360deg); }
}

.pane {
  background: rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(32px) saturate(190%);
  -webkit-backdrop-filter: blur(32px) saturate(190%);
  border: 1px solid rgba(255, 255, 255, 0.58);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.88),
    0 12px 32px rgba(13, 37, 61, 0.08);
}

.band {
  position: relative;
  z-index: 1;
  padding: 88px 24px;
  background: #e8ecfc;
}
.band > .eyebrow, .band > h2, .band > .mission { text-align: center; }
#ideas { padding-top: 112px; }
.mission {
  margin: -16px auto 40px;
  max-width: 640px;
  font-size: 16px;
  line-height: 1.6;
  color: #273951;
}

.ideas {
  list-style: none;
  margin: 0 auto;
  padding: 0;
  width: min(1080px, 100%);
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.idea {
  border-radius: 18px;
  padding: 32px;
  text-align: left;
}
.idea strong { display: block; font-size: 20px; font-weight: 500; margin-bottom: 8px; }
.idea p { margin: 0; font-size: 15px; line-height: 1.5; color: #273951; }

.stats {
  width: min(920px, 100%);
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.stats div {
  border-radius: 18px;
  padding: 24px 16px;
  text-align: center;
}
.stats b {
  display: block;
  font-size: 32px;
  font-weight: 300;
  letter-spacing: -0.02em;
  font-feature-settings: "tnum";
}
.stats span { display: block; margin-top: 8px; font-size: 13px; color: #273951; }

.caps {
  width: min(1080px, 100%);
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px 16px;
}
.cap {
  --spot-x: 50%;
  --spot-y: 28%;
  --tint: rgba(255, 255, 255, 0.08);
  position: relative;
  isolation: isolate;
  overflow: hidden;
  min-height: 280px;
  padding: 32px 28px 28px;
  border-radius: 22px;
  text-align: left;
  color: #0d253d;
  background:
    radial-gradient(240px circle at var(--spot-x) var(--spot-y), rgba(255, 255, 255, 0.42), transparent 62%),
    linear-gradient(165deg, rgba(255, 255, 255, 0.52) 0%, rgba(255, 255, 255, 0.14) 46%, var(--tint) 100%);
  backdrop-filter: blur(28px) saturate(185%);
  -webkit-backdrop-filter: blur(28px) saturate(185%);
  border: 1px solid rgba(255, 255, 255, 0.62);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.92),
    inset 0 -1px 0 rgba(13, 37, 61, 0.05),
    0 16px 40px rgba(13, 37, 61, 0.1);
  transition: transform 220ms cubic-bezier(0.23, 1, 0.32, 1), box-shadow 220ms ease, backdrop-filter 220ms ease, background 220ms ease;
}
.tone-buyer { --tint: rgba(245, 233, 212, 0.28); }
.tone-factory { --tint: rgba(83, 58, 253, 0.1); }
.tone-ops { --tint: rgba(196, 181, 253, 0.22); }
.tone-inspect { --tint: rgba(234, 34, 97, 0.08); }
.cap:nth-child(2),
.cap:nth-child(3) { margin-top: 18px; }
.cap:hover {
  transform: translateY(-6px) scale(1.015);
  backdrop-filter: blur(36px) saturate(200%);
  -webkit-backdrop-filter: blur(36px) saturate(200%);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.96),
    0 22px 48px rgba(13, 37, 61, 0.14);
}
.cap:active { transform: scale(0.97); }
.pane-no {
  position: absolute;
  right: 18px;
  top: 6px;
  font-size: 72px;
  font-weight: 300;
  line-height: 1;
  letter-spacing: -0.04em;
  color: rgba(13, 37, 61, 0.14);
  pointer-events: none;
}
.en {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.12em;
  color: #0d253d;
}
.cap h3 {
  margin: 6px 0 10px;
  font-size: 32px;
  font-weight: 500;
  letter-spacing: -0.02em;
  color: #0d253d;
}
.tag {
  margin: 0 0 22px;
  max-width: 92%;
  font-size: 15px;
  font-weight: 500;
  line-height: 1.55;
  color: #0d253d;
}
.mini {
  font-size: 12px;
  font-weight: 500;
  color: #0d253d;
  background: #fff;
  border-radius: 9999px;
  padding: 6px 12px;
  display: inline-block;
  margin-bottom: 18px;
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.8);
}
.arrow {
  appearance: none;
  display: block;
  border: 0;
  background: none;
  color: #2e2b8c;
  font: 500 15px/1 inherit;
  letter-spacing: 0.01em;
  cursor: pointer;
  padding: 0;
}
.arrow:active { transform: scale(0.97); }

.flow-wrap {
  width: min(1080px, 100%);
  margin: 0 auto;
}

.close {
  background: #005588;
  color: #fff;
  text-align: center;
  padding: 96px 24px;
}
.close h2 { margin: 0 0 24px; color: #fff; }

.foot {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 48px 32px 64px;
  background: #e8ecfc;
  color: #273951;
  font-size: 13px;
}
.foot b { display: block; color: #0d253d; margin-bottom: 6px; }
.foot p { margin: 0; }
.ports { display: flex; flex-direction: column; gap: 8px; align-items: flex-end; }
.ports a { color: #533afd; text-decoration: none; }

@media (max-width: 833px) {
  .mock-nav, .mock-live { display: none; }
  .ideas, .caps, .stats { grid-template-columns: 1fr; }
  .cap:nth-child(2),
  .cap:nth-child(3) { margin-top: 0; }
  h1 { font-size: 36px; }
  .nav-links { display: none; }
  .scene-hero { grid-template-columns: 1fr; padding: 48px 20px; }
  .scene-photo { min-height: 340px; }
  .product { margin: 0; }
  .command-grid { grid-template-columns: 1fr; }
  .signal-card { display: none; }
  .factory-row, .command-footer { grid-template-columns: 1fr; }
  #ideas { padding-top: 64px; }
}
@media (prefers-reduced-motion: reduce) {
  .pill, .ghost, .cap, .global-nav { transition: none; }
  .pill:active, .ghost:active, .cap:active, .cap:hover { transform: none; }
  .hero-glow { opacity: 0.55; }
  .orbit-ring { animation: none; }
}
@media (prefers-reduced-transparency: reduce) {
  .global-nav, .mock, .pane, .cap { backdrop-filter: none; background: #fff; }
  .hero .eyebrow, .hero h1 { color: #0d253d; }
  .hero .lead { color: #273951; }
}
@media (prefers-contrast: more) {
  .global-nav, .idea, .cap { border-color: #0d253d; }
}
</style>
