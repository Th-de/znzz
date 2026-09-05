<template>
  <div v-if="items.length" class="cov">
    <div v-for="p in items" :key="p.processNo + '-' + p.processName" class="row">
      <el-progress :percentage="pct(p)" :status="p.satisfied ? 'success' : undefined" />
      <div class="meta">已覆盖 {{ p.covered }} / 需要 {{ p.need }} 件{{ p.satisfied ? '（已满足）' : '（未满足）' }}</div>
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
.meta { color: #909399; font-size: 12px; }
</style>
