<template>
  <span class="status-pill" :data-tone="tone || 'slate'">{{ text }}</span>
</template>

<script setup>
defineProps({
  tone: { type: String, default: 'slate' },
  text: { type: String, default: '-' },
})
</script>
