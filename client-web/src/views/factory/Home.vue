<template>
  <div>
      <el-alert v-if="capLoaded && !capComplete" type="warning" :closable="false" style="margin-bottom:12px"
        title="能力档案未填完，无法意向报名。" />
      <h3>待办</h3>
      <el-empty v-if="!todos.length" description="暂无待办" :image-size="60" />
      <div v-else class="todos">
        <el-card v-for="t in todos" :key="t.type + t.link" shadow="hover" class="todo">
          <div>{{ t.title }}</div>
          <el-button type="primary" size="small" style="margin-top:8px" @click="goTodo(t)">去办理</el-button>
        </el-card>
      </div>
      <h3>信用事件</h3>
      <PagedBox :data="credits" v-slot="{ rows }">
      <el-table :data="rows" border>
        <el-table-column label="需求ID" width="90">
          <template #default="{ row }">{{ row.demandId || '-' }}</template>
        </el-table-column>
        <el-table-column label="需求名称" min-width="160">
          <template #default="{ row }">{{ row.demandTitle || '-' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="150">
          <template #default="{ row }">{{ label(CREDIT_TYPE, row.type) }}</template>
        </el-table-column>
        <el-table-column prop="scoreChange" label="分数变化" width="100" />
        <el-table-column prop="remark" label="说明" />
      </el-table>
      </PagedBox>
      <h3 style="margin-top:20px">我的资金流水</h3>
      <PagedBox :data="funds" v-slot="{ rows }">
      <el-table :data="rows" border>
        <el-table-column label="需求ID" width="90">
          <template #default="{ row }">{{ row.demandId || '-' }}</template>
        </el-table-column>
        <el-table-column label="需求名称" min-width="160">
          <template #default="{ row }">{{ row.demandTitle || '-' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ label(FUND_TYPE, row.type) }}</template>
        </el-table-column>
        <el-table-column label="方向" width="100">
          <template #default="{ row }">{{ label(FUND_DIR, row.direction) }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" />
      </el-table>
      </PagedBox>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import api from '../../api'
import PagedBox from '../../components/PagedBox.vue'
import { FUND_TYPE, FUND_DIR, CREDIT_TYPE, label, fmtTime } from '../../utils/labels'

const funds = ref([])
const credits = ref([])
const capComplete = ref(true)
const capLoaded = ref(false)
const router = useRouter()
const todos = ref([])

async function goTodo(t) {
  if (t.type === 'STAGE_CLOSED' && t.link) {
    const m = String(t.link).match(/\/factory\/quotations\/(\d+)/)
    if (m) {
      try { await api.post('/common/todos/ack', { type: 'STAGE_CLOSED', bizId: Number(m[1]) }) } catch { /* 已读失败仍跳转 */ }
    }
  }
  router.push(t.link)
}

async function load() {
  try { todos.value = await api.get('/common/todos') } catch { todos.value = [] }
  funds.value = await api.get('/common/funds')
  credits.value = await api.get('/common/credits')
  try {
    const cap = await api.get('/enterprise/capability')
    capComplete.value = !!cap.complete
    capLoaded.value = true
  } catch {
    capLoaded.value = true
  }
}
onMounted(load)
</script>

<style scoped>
.todos { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; justify-content: center; }
.todo { width: 260px; }
</style>
