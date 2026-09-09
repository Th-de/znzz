<template>
  <div class="spark" :class="{ compact }" aria-hidden="true">
    <svg ref="svg" :viewBox="`0 0 ${w} ${h}`" preserveAspectRatio="none">
      <defs>
        <linearGradient :id="gid" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#052659" stop-opacity="0.28" />
          <stop offset="100%" stop-color="#052659" stop-opacity="0" />
        </linearGradient>
      </defs>
      <path v-if="area" ref="areaEl" :d="area" :fill="`url(#${gid})`" />
      <path ref="lineEl" :d="line" fill="none" stroke="#052659" :stroke-width="compact ? 1.6 : 2.2" stroke-linejoin="round" stroke-linecap="round" />
    </svg>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { drawStroke, fillArea } from '../motion/chart'

const props = defineProps({
  points: { type: Array, default: () => [] },
  compact: { type: Boolean, default: false },
})

const w = computed(() => (props.compact ? 160 : 320))
const h = computed(() => (props.compact ? 42 : 88))
const svg = ref(null)
const lineEl = ref(null)
const areaEl = ref(null)
const gid = `spark-${Math.random().toString(36).slice(2, 8)}`

const series = computed(() => {
  const raw = (props.points || []).map(n => Number(n) || 0)
  if (raw.length >= 2) return raw
  return [8, 11, 9, 14, 12, 16, 13, 18, 15, 20]
})

const mapped = computed(() => {
  const vals = series.value
  const min = Math.min(...vals)
  const max = Math.max(...vals)
  const span = max - min || 1
  const pad = 4
  return vals.map((v, i) => {
    const x = pad + (i * (w.value - pad * 2)) / (vals.length - 1)
    const y = h.value - pad - ((v - min) / span) * (h.value - pad * 2)
    return [x, y]
  })
})

const line = computed(() => mapped.value.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(' '))
const area = computed(() => {
  const pts = mapped.value
  if (pts.length < 2) return ''
  return `${line.value} L${pts[pts.length - 1][0].toFixed(1)},${h.value} L${pts[0][0].toFixed(1)},${h.value} Z`
})

async function play() {
  await nextTick()
  drawStroke(lineEl.value, props.compact ? 0.8 : 1.05)
  fillArea(areaEl.value, 0.25)
}

onMounted(play)
watch(() => props.points, play)
</script>

<style scoped>
.spark { width: 100%; height: 88px; }
.spark.compact { height: 42px; max-width: 180px; }
svg { width: 100%; height: 100%; display: block; }
</style>
