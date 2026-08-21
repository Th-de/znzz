<template>
  <div>
      <el-alert
        v-if="cancelStats.warn"
        type="warning"
        :closable="false"
        :title="'近 90 天已取消 ' + cancelStats.last90Days + ' 次，请谨慎取消，避免影响后续匹配。'"
        style="margin-bottom:12px"
      />
      <el-alert
        v-else-if="cancelStats.last90Days > 0"
        type="info"
        :closable="false"
        :title="'近 90 天取消 ' + cancelStats.last90Days + ' 次'"
        style="margin-bottom:12px"
      />
      <h3>待办</h3>
      <el-empty v-if="!todos.length" description="暂无待办" :image-size="60" />
      <div v-else class="todos">
        <el-card v-for="t in todos" :key="t.type + t.link" shadow="hover" class="todo">
          <div>{{ t.title }}</div>
          <el-button type="primary" size="small" style="margin-top:8px" @click="$router.push(t.link)">去办理</el-button>
        </el-card>
      </div>
      <h3>我的需求</h3>
      <el-table :data="demands" border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="productName" label="产品" />
        <el-table-column prop="quantity" label="数量" width="100" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }">{{ label(DEMAND_STATUS, row.status) }}</template>
        </el-table-column>
        <el-table-column label="意向倒计时" min-width="180">
          <template #default="{ row }">
            <IntentionCountdown v-if="row.status==='PUBLISHED'" :end-at="row.intentionEndAt" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320">
          <template #default="{ row }">
            <el-button size="small" @click="$router.push('/buyer/demand/' + row.id)">查看</el-button>
            <el-button v-if="row.status==='PENDING_AUDIT'" size="small" disabled>等待运营审核</el-button>
            <el-button v-if="row.status==='RETURNED'" size="small" type="warning" @click="$router.push('/buyer/publish?id=' + row.id)">退回修改</el-button>
            <el-button v-if="row.status==='PUBLISHED'" size="small" type="danger" @click="cancelAndRepublish(row)">取消并重新发布</el-button>
            <el-button v-if="row.status==='THINKING'" size="small" type="success" @click="decide(row, 'CONTINUE')">继续</el-button>
            <el-button v-if="row.status==='THINKING'" size="small" type="danger" @click="decide(row, 'CANCEL')">取消</el-button>
            <el-button v-if="row.status==='SOLUTION_GENERATED' && canPick(row)" size="small" type="primary" @click="$router.push('/buyer/solutions/'+row.id)">看方案</el-button>
            <el-button v-else-if="row.status==='SOLUTION_GENERATED'" size="small" disabled>等待运营下发方案</el-button>
            <el-button v-if="row.status==='SOLUTION_SELECTED'" size="small" @click="$router.push('/buyer/orders')">看订单</el-button>
          </template>
        </el-table-column>
      </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listMine, decide as decideDemand, cancelPublished, getCancelStats } from '../../api/demand'
import api from '../../api'
import IntentionCountdown from '../../components/IntentionCountdown.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DEMAND_STATUS, label } from '../../utils/labels'

const router = useRouter()
const demands = ref([])
const cancelStats = ref({ last90Days: 0, warn: false })
const todos = ref([])

function canPick(row) {
  return todos.value.some(t => t.type === 'PICK_SOLUTION' && String(t.link).includes('/' + row.id))
}

async function load() {
  demands.value = await listMine()
  cancelStats.value = await getCancelStats()
  try { todos.value = await api.get('/common/todos') } catch { todos.value = [] }
}

async function decide(row, action) {
  let reason
  if (action === 'CANCEL') {
    const box = await ElMessageBox.prompt('请填写取消理由', '思考期取消', {
      confirmButtonText: '提交',
      cancelButtonText: '再想想',
      inputPlaceholder: '必填',
      inputValidator: (v) => !!String(v || '').trim() || '请填写理由',
    })
    reason = String(box.value).trim()
  }
  await decideDemand(row.id, action, reason)
  ElMessage.success('操作成功')
  load()
}

async function cancelAndRepublish(row) {
  const { value } = await ElMessageBox.prompt('已发布需求不能改内容，取消后将发一条新需求。请填写取消原因（平台会记录）。', '取消并重新发布', {
    confirmButtonText: '取消并去发布',
    cancelButtonText: '再想想',
    inputPlaceholder: '必填',
    inputValidator: (v) => !!String(v || '').trim() || '请填写原因',
  })
  await cancelPublished(row.id, String(value).trim())
  ElMessage.success('已取消，请发布新需求')
  router.push('/buyer/publish?from=' + row.id)
}

onMounted(load)
</script>

<style scoped>
.todos { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.todo { width: 260px; }
</style>
