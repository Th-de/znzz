<template>
  <div>
    <canvas
      ref="canvas"
      class="pad"
      @pointerdown="start"
      @pointermove="move"
      @pointerup="end"
      @pointercancel="end"
      @pointerleave="end"
    />
    <div class="bar">
      <el-button size="small" @click="clear">重签</el-button>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

const emit = defineEmits(['change'])
const canvas = ref(null)
let ctx = null
let drawing = false
let observer = null

function setupCtx() {
  const c = canvas.value
  if (!c) return
  ctx = c.getContext('2d')
  ctx.strokeStyle = '#111'
  ctx.lineWidth = 2
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
}

function fit() {
  const c = canvas.value
  if (!c || drawing) return
  const r = c.getBoundingClientRect()
  if (r.width < 2 || r.height < 2) return
  const dpr = window.devicePixelRatio || 1
  const w = Math.max(1, Math.round(r.width * dpr))
  const h = Math.max(1, Math.round(r.height * dpr))
  if (c.width === w && c.height === h) return
  c.width = w
  c.height = h
  setupCtx()
}

function pos(e) {
  const c = canvas.value
  const r = c.getBoundingClientRect()
  if (!r.width || !r.height) return { x: 0, y: 0 }
  return {
    x: (e.clientX - r.left) * (c.width / r.width),
    y: (e.clientY - r.top) * (c.height / r.height),
  }
}

function start(e) {
  fit()
  drawing = true
  e.currentTarget.setPointerCapture?.(e.pointerId)
  const p = pos(e)
  ctx.beginPath()
  ctx.moveTo(p.x, p.y)
}

function move(e) {
  if (!drawing) return
  const p = pos(e)
  ctx.lineTo(p.x, p.y)
  ctx.stroke()
  emit('change', canvas.value.toDataURL('image/png'))
}

function end(e) {
  if (!drawing) return
  drawing = false
  e.currentTarget.releasePointerCapture?.(e.pointerId)
}

function clear() {
  if (!ctx || !canvas.value) return
  ctx.clearRect(0, 0, canvas.value.width, canvas.value.height)
  emit('change', '')
}

onMounted(async () => {
  await nextTick()
  setupCtx()
  fit()
  observer = new ResizeObserver(() => fit())
  observer.observe(canvas.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
})

defineExpose({ clear })
</script>

<style scoped>
.pad {
  display: block;
  border: 1px solid #dcdfe6;
  background: #fff;
  width: 100%;
  height: 140px;
  touch-action: none;
  cursor: crosshair;
}
.bar { margin-top: 6px; }
</style>
