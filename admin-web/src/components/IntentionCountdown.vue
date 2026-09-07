<template>
  <span v-if="endAt">{{ text }}</span>
  <span v-else>-</span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  endAt: { type: String, default: '' },
})

const text = computed(() => {
  if (!props.endAt) return '-'
  const end = new Date(props.endAt.replace(' ', 'T'))
  if (Number.isNaN(end.getTime())) return props.endAt
  const ms = end.getTime() - Date.now()
  if (ms <= 0) return '已截止'
  const hours = Math.floor(ms / 3600000)
  const days = Math.floor(hours / 24)
  const restHours = hours % 24
  if (days <= 0) return '剩余 ' + hours + ' 小时（' + props.endAt + '）'
  return '剩余 ' + days + ' 天 ' + restHours + ' 小时（' + props.endAt + '）'
})
</script>
