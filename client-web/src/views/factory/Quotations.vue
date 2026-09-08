<template>
  <div>
    <el-table :data="rows" border>
      <el-table-column prop="demandId" label="需求ID" width="90" />
      <el-table-column prop="demandTitle" label="需求标题" min-width="160" />
      <el-table-column label="报名时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="140">
        <template #default="{ row }">
          <el-tag v-if="row.lost" type="danger" size="small">已落选</el-tag>
          <span v-else>{{ label(DEMAND_STATUS, row.demandStatus) }}</span>
        </template>
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
        quoteStatuses: [],
        actionKeys: [],
      })
    }
    const g = map.get(q.demandId)
    if (q.createdAt && (!g.createdAt || q.createdAt < g.createdAt)) g.createdAt = q.createdAt
    if (q.status) g.quoteStatuses.push(q.status)
    if (q.actionKey) g.actionKeys.push(q.actionKey)
  }
  return [...map.values()].map((g) => {
    const afterConfirm = ['SOLUTION_CONFIRMED', 'SOLUTION_SELECTED', 'CONTRACTED', 'IN_PRODUCTION', 'COMPLETED']
    const won = g.quoteStatuses.includes('WIN')
    const lost = !won && afterConfirm.includes(g.demandStatus)
      && (g.quoteStatuses.includes('LOSE') || g.actionKeys.includes('LOSE'))
    return { ...g, lost }
  })
})

async function load() {
  quotations.value = await api.get('/bidding/mine')
}

onMounted(load)
</script>
