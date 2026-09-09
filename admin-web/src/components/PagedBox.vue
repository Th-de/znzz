<template>
  <div class="paged-box" ref="box">
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
import { computed, onMounted, onUnmounted, ref, watch, nextTick } from 'vue'
import { bindTableSurface, staggerTable } from '../motion/table'

const props = defineProps({
  data: { type: Array, default: () => [] },
  pageSize: { type: Number, default: 10 },
})

const box = ref(null)
const page = ref(1)
const size = ref(props.pageSize)
const sizes = [5, 10, 20, 50]
let unbind = () => {}

async function play() {
  await nextTick()
  const table = box.value?.querySelector('.el-table')
  if (!table) return
  if (!table.classList.contains('motion-table')) {
    unbind()
    unbind = bindTableSurface(table)
  } else {
    staggerTable(table)
  }
}
const total = computed(() => (props.data || []).length)
const rows = computed(() => {
  const list = props.data || []
  const start = (page.value - 1) * size.value
  return list.slice(start, start + size.value)
})

watch(() => props.data, () => { page.value = 1 })
watch(() => props.pageSize, (v) => { size.value = v })
watch(rows, play)
onMounted(play)
onUnmounted(() => unbind())
</script>

<style scoped>
.paged-box {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.pager {
  position: relative;
  z-index: 3;
  width: 100%;
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
}
</style>
