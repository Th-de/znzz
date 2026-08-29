<template>
  <div class="paged-box">
    <slot :rows="rows" />
    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="sizes"
        layout="total, sizes, prev, pager, next, jumper"
        background
      />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  data: { type: Array, default: () => [] },
  pageSize: { type: Number, default: 10 },
})

const page = ref(1)
const size = ref(props.pageSize)
const sizes = [5, 10, 20, 50]
const total = computed(() => (props.data || []).length)
const rows = computed(() => {
  const list = props.data || []
  const start = (page.value - 1) * size.value
  return list.slice(start, start + size.value)
})

watch(() => props.data, () => { page.value = 1 })
watch(() => props.pageSize, (v) => { size.value = v })
</script>

<style scoped>
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
