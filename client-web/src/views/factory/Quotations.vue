<template>
  <div>
    <el-table :data="rows" border>
      <el-table-column prop="demandId" label="需求ID" width="90" />
      <el-table-column prop="demandTitle" label="需求标题" min-width="160" />
      <el-table-column label="报名时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="阶段" width="120">
        <template #default="{ row }">{{ label(DEMAND_STATUS, row.demandStatus) }}</template>
      </el-table-column>
      <el-table-column label="更新时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.demandStageAt) }}</template>
      </el-table-column>
      <el-table-column label="截止时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.demandStageEndAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="$router.push('/factory/quotations/' + row.demandId)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import api from '../../api'
import { DEMAND_STATUS, label, fmtTime } from '../../utils/labels'

const quotations = ref([])

const rows = computed(() => {
  const map = new Map()
  for (const q of quotations.value) {
    if (!map.has(q.demandId)) {
      map.set(q.demandId, {
        demandId: q.demandId,
        demandTitle: q.demandTitle,
        demandStatus: q.demandStatus,
        demandStageAt: q.demandStageAt,
        demandStageEndAt: q.demandStageEndAt,
        createdAt: q.createdAt,
      })
    }
    const g = map.get(q.demandId)
    if (q.createdAt && (!g.createdAt || q.createdAt < g.createdAt)) g.createdAt = q.createdAt
  }
  return [...map.values()]
})

async function load() {
  quotations.value = await api.get('/bidding/mine')
}

onMounted(load)
</script>
