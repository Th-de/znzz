<template>
  <div class="flow-map" @mouseleave="hovered = -1">
    <ol>
      <li
        v-for="(n, i) in nodes"
        :key="n.title"
        :class="['step', n.slot, { on: i === current, dim: hovered >= 0 && hovered !== i }]"
        @pointerenter="pin(i)"
        @click="pin(i, true)"
      >
        <em>{{ String(i + 1).padStart(2, '0') }}</em>
        <strong>{{ n.title }}</strong>
        <p>{{ n.desc }}</p>
      </li>
    </ol>
    <i class="rail h c12" aria-hidden="true" />
    <i class="rail h c23" aria-hidden="true" />
    <i class="rail h c34" aria-hidden="true" />
    <i class="rail h c56" aria-hidden="true" />
    <i class="rail h c67" aria-hidden="true" />
    <i class="rail h c78" aria-hidden="true" />
    <i class="rail elbow top" aria-hidden="true" />
    <i class="rail elbow mid" aria-hidden="true" />
    <i class="rail elbow bot" aria-hidden="true" />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { prefersReducedMotion } from '../motion/core'

const props = defineProps({
  steps: { type: Array, required: true },
})

const hints = [
  '买家提交规格与数量',
  '工厂交意向金占位',
  '锁价，补保证金',
  '运营审方案，买家确认',
  '分厂签合同',
  '按期报工',
  '第三方独立填报',
  '托管释放，佣金入账',
]

const slots = ['s1', 's2', 's3', 's4', 's5', 's6', 's7', 's8']

const nodes = computed(() => props.steps.map((title, i) => ({
  title,
  desc: hints[i] || '',
  slot: slots[i] || 's1',
})))

const current = ref(0)
const hovered = ref(-1)
const locked = ref(false)
let timer = 0

function pin(i, lock = false) {
  current.value = i
  hovered.value = i
  if (lock) locked.value = true
}

function tick() {
  if (locked.value || prefersReducedMotion()) return
  current.value = (current.value + 1) % props.steps.length
}

onMounted(() => {
  if (!prefersReducedMotion()) timer = window.setInterval(tick, 2200)
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<style scoped>
.flow-map {
  --rail: rgba(83, 58, 253, 0.42);
  --rail-dot: #052659;
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 28px minmax(0, 1fr) 28px minmax(0, 1fr) 28px minmax(0, 1fr) 28px;
  grid-template-rows: auto 36px auto;
  align-items: stretch;
}

ol { display: contents; list-style: none; margin: 0; padding: 0; }

.s1 { grid-column: 1; grid-row: 1; }
.s2 { grid-column: 3; grid-row: 1; }
.s3 { grid-column: 5; grid-row: 1; }
.s4 { grid-column: 7; grid-row: 1; }
.s5 { grid-column: 7; grid-row: 3; }
.s6 { grid-column: 5; grid-row: 3; }
.s7 { grid-column: 3; grid-row: 3; }
.s8 { grid-column: 1; grid-row: 3; }

.rail {
  pointer-events: none;
  align-self: center;
  justify-self: stretch;
  position: relative;
}
.rail.h {
  height: 2px;
  background: var(--rail);
}
.rail.h::before,
.rail.h::after {
  content: "";
  position: absolute;
  top: 50%;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #fff;
  border: 2px solid var(--rail-dot);
  transform: translate(-50%, -50%);
}
.rail.h::before { left: 0; }
.rail.h::after { left: 100%; }

.c12 { grid-column: 2; grid-row: 1; }
.c23 { grid-column: 4; grid-row: 1; }
.c34 { grid-column: 6; grid-row: 1; }
.c56 { grid-column: 6; grid-row: 3; }
.c67 { grid-column: 4; grid-row: 3; }
.c78 { grid-column: 2; grid-row: 3; }

.elbow {
  position: relative;
  align-self: stretch;
  justify-self: stretch;
}
.elbow.top { grid-column: 8; grid-row: 1; }
.elbow.mid { grid-column: 8; grid-row: 2; }
.elbow.bot { grid-column: 8; grid-row: 3; }
.elbow.top::before,
.elbow.bot::after {
  content: "";
  position: absolute;
  left: 0;
  top: 50%;
  width: 14px;
  height: 2px;
  background: var(--rail);
}
.elbow.top::after {
  content: "";
  position: absolute;
  left: 12px;
  top: 50%;
  bottom: 0;
  width: 2px;
  background: var(--rail);
}
.elbow.mid::before {
  content: "";
  position: absolute;
  left: 12px;
  top: 0;
  bottom: 0;
  width: 2px;
  background: var(--rail);
}
.elbow.bot::before {
  content: "";
  position: absolute;
  left: 12px;
  top: 0;
  height: 50%;
  width: 2px;
  background: var(--rail);
}

.step {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  z-index: 1;
  text-align: left;
  padding: 16px 14px 18px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(16px) saturate(180%);
  -webkit-backdrop-filter: blur(16px) saturate(180%);
  border: 1px solid rgba(227, 232, 238, 0.95);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.88), 0 1px 3px rgba(0, 55, 112, 0.08);
  cursor: pointer;
  transition: transform 180ms cubic-bezier(0.23, 1, 0.32, 1), opacity 180ms ease, box-shadow 180ms ease;
}
.step::before {
  content: "";
  position: absolute;
  inset: -50%;
  background: linear-gradient(115deg, transparent 35%, rgba(255, 255, 255, 0.78) 50%, transparent 65%);
  transform: translateX(-70%);
  opacity: 0;
  pointer-events: none;
  z-index: 0;
}
.step > * { position: relative; z-index: 1; }
.step:active { transform: scale(0.97); }
.step.on {
  border-color: rgba(83, 58, 253, 0.45);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.92), 0 0 0 1px rgba(83, 58, 253, 0.28), 0 0 22px rgba(83, 58, 253, 0.2);
  animation: glow-beat 1.5s ease-in-out infinite;
}
.step.on::before { animation: shimmer 1.35s ease-in-out infinite; }
.step.dim { opacity: 0.72; }

em { display: block; font-style: normal; font-size: 11px; color: #64748d; margin-bottom: 6px; }
strong { display: block; font-size: 16px; letter-spacing: -0.02em; color: #0d253d; }
p { margin: 6px 0 0; font-size: 13px; line-height: 1.45; color: #273951; }

@keyframes shimmer {
  0% { transform: translateX(-70%); opacity: 0; }
  30% { opacity: 1; }
  100% { transform: translateX(70%); opacity: 0; }
}
@keyframes glow-beat {
  0%, 100% { box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.92), 0 0 0 1px rgba(83, 58, 253, 0.22), 0 0 14px rgba(83, 58, 253, 0.12); }
  50% { box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.95), 0 0 0 1px rgba(83, 58, 253, 0.5), 0 0 28px rgba(83, 58, 253, 0.3); }
}

@media (max-width: 833px) {
  .flow-map {
    display: grid;
    grid-template-columns: 1fr 1fr;
    grid-template-rows: none;
    gap: 12px;
  }
  .s1, .s2, .s3, .s4, .s5, .s6, .s7, .s8 {
    grid-column: auto;
    grid-row: auto;
  }
  .rail { display: none; }
}
@media (prefers-reduced-motion: reduce) {
  .step { transition: opacity 160ms ease; }
  .step:active { transform: none; }
  .step.on { animation: none; }
  .step.on::before { animation: none; opacity: 0; }
}
@media (prefers-reduced-transparency: reduce) {
  .step { background: #fff; backdrop-filter: none; }
}
</style>
