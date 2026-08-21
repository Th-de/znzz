<template>
  <div>
      <el-alert v-if="capLoaded && !capComplete" type="warning" :closable="false" style="margin-bottom:12px"
        title="能力档案未填完，无法意向报名。" />
      <h3>待办</h3>
      <el-empty v-if="!todos.length" description="暂无待办" :image-size="60" />
      <div v-else class="todos">
        <el-card v-for="t in todos" :key="t.type + t.link" shadow="hover" class="todo">
          <div>{{ t.title }}</div>
          <el-button type="primary" size="small" style="margin-top:8px" @click="$router.push(t.link)">去办理</el-button>
        </el-card>
      </div>
      <h3>信用事件</h3>
      <el-table :data="credits" border>
        <el-table-column prop="type" label="类型" width="140" />
        <el-table-column prop="scoreChange" label="分数变化" width="100" />
        <el-table-column prop="remark" label="说明" />
      </el-table>
      <h3 style="margin-top:20px">我的资金流水</h3>
      <el-table :data="funds" border>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ label(FUND_TYPE, row.type) }}</template>
        </el-table-column>
        <el-table-column label="方向" width="100">
          <template #default="{ row }">{{ label(FUND_DIR, row.direction) }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" />
      </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '../../api'
import { FUND_TYPE, FUND_DIR, label } from '../../utils/labels'

const funds = ref([])
const credits = ref([])
const capComplete = ref(true)
const capLoaded = ref(false)
const todos = ref([])

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
.todos { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.todo { width: 260px; }
</style>
