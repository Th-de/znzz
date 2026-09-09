<template>
  <span class="count">{{ text }}</span>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { motionReady, prefersReducedMotion } from '../motion/core'

const props = defineProps({
  value: { type: [Number, String], default: 0 },
  prefix: { type: String, default: '' },
  suffix: { type: String, default: '' },
})

const text = ref(format(props.value))

function num(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}

function format(v) {
  return props.prefix + num(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 }) + props.suffix
}

function play(from, to) {
  if (prefersReducedMotion()) {
    text.value = format(to)
    return
  }
  const gsap = motionReady()
  const obj = { n: from }
  gsap.to(obj, {
    n: to,
    duration: 0.85,
    ease: 'power2.out',
    overwrite: true,
    onUpdate() { text.value = format(obj.n) },
  })
}

onMounted(() => play(0, num(props.value)))
watch(() => props.value, (next, prev) => play(num(prev), num(next)))
</script>

<style scoped>
.count { font-variant-numeric: tabular-nums; }
</style>
