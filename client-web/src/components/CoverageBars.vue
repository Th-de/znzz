<template>
  <div v-if="items.length" class="cov">
    <div v-for="p in items" :key="p.processNo" class="row">
      <div class="name">{{ p.processName }}</div>
      <el-progress :percentage="pct(p)" :status="p.satisfied ? 'success' : undefined" />
      <div class="meta">已覆盖 {{ p.covered }} / 需要 {{ p.need }}{{ p.satisfied ? '（已满足）' : '（未满足）' }}</div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  items: { type: Array, default: () => [] },
})

function pct(p) {
  if (!p.need) return 0
  return Math.min(100, Math.round((p.covered / p.need) * 100))
}
</script>

<style scoped>
.cov { display: flex; flex-direction: column; gap: 8px; }
.row { margin-bottom: 4px; }
.name { font-size: 13px; margin-bottom: 2px; }
.meta { color: #909399; font-size: 12px; margin-top: 2px; }
</style>
