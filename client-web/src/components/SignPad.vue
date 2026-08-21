<template>
  <div>
    <canvas
      ref="canvas"
      width="420"
      height="140"
      class="pad"
      @mousedown="start"
      @mousemove="move"
      @mouseup="end"
      @mouseleave="end"
      @touchstart.prevent="start"
      @touchmove.prevent="move"
      @touchend.prevent="end"
    />
    <div class="bar">
      <el-button size="small" @click="clear">重签</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const emit = defineEmits(['change'])
const canvas = ref(null)
let ctx = null
let drawing = false

onMounted(() => {
  ctx = canvas.value.getContext('2d')
  ctx.strokeStyle = '#111'
  ctx.lineWidth = 2
  ctx.lineCap = 'round'
})

function pos(e) {
  const r = canvas.value.getBoundingClientRect()
  const t = e.touches ? e.touches[0] : e
  return { x: t.clientX - r.left, y: t.clientY - r.top }
}

function start(e) {
  drawing = true
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

function end() {
  drawing = false
}

function clear() {
  ctx.clearRect(0, 0, canvas.value.width, canvas.value.height)
  emit('change', '')
}

defineExpose({ clear })
</script>

<style scoped>
.pad { border: 1px solid #dcdfe6; background: #fff; width: 100%; cursor: crosshair; }
.bar { margin-top: 6px; }
</style>
