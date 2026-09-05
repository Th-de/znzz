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
      <PagedBox :data="demands" v-slot="{ rows }">
      <el-table :data="rows" border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="productName" label="产品" />
        <el-table-column prop="quantity" label="数量" width="100" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }">{{ label(DEMAND_STATUS, row.status) }}</template>
        </el-table-column>
        <el-table-column label="提交时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="发布/意向开始" width="160">
          <template #default="{ row }">{{ fmtTime(row.publishedAt) }}</template>
        </el-table-column>
        <el-table-column label="倒计时" min-width="180">
          <template #default="{ row }">
            <IntentionCountdown v-if="row.status==='PUBLISHED'" :end-at="row.intentionEndAt" />
            <IntentionCountdown v-else-if="row.status==='FACTORY_THINKING'" :end-at="row.factoryThinkingEndAt" />
            <IntentionCountdown v-else-if="row.status==='BUYER_THINKING'" :end-at="row.buyerThinkingEndAt" />
            <IntentionCountdown v-else-if="row.status==='LOCKING'" :end-at="row.lockingEndAt" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="360">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="$router.push('/buyer/demand/' + row.id)">详情</el-button>
            <el-button v-if="row.status==='PENDING_AUDIT'" size="small" disabled>等待运营审核</el-button>
            <el-button v-if="row.status==='RETURNED'" size="small" type="warning" @click="$router.push('/buyer/demand/' + row.id)">退回修改</el-button>
            <el-button v-if="row.status==='THINKING'" size="small" type="success" @click="decide(row, 'CONTINUE')">继续</el-button>
            <el-button v-if="row.status==='THINKING'" size="small" type="danger" @click="decide(row, 'CANCEL')">取消</el-button>
            <el-button v-if="row.status==='BUYER_THINKING'" size="small" type="success" @click="$router.push('/buyer/demand/' + row.id)">去决定(交保证金/取消)</el-button>
            <el-button v-if="row.status==='SOLUTION_GENERATED' && canPick(row)" size="small" type="primary" @click="$router.push('/buyer/demand/'+row.id)">选定方案</el-button>
            <el-tooltip v-else-if="row.status==='SOLUTION_GENERATED'" content="运营审核通过后即可在详情页参考推荐并自行分配工厂" placement="top">
              <el-button size="small" disabled>等待运营审核推荐方案</el-button>
            </el-tooltip>
            <el-button v-if="row.status==='SOLUTION_CONFIRMED'" size="small" disabled>等待运营派单</el-button>
            <el-button v-if="row.status==='SOLUTION_GENERATED' || row.status==='SOLUTION_CONFIRMED'" size="small" type="danger" @click="closeOrder(row)">关闭订单</el-button>
          </template>
        </el-table-column>
      </el-table>
      </PagedBox>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listMine, decide as decideDemand, closeSolution, getCancelStats } from '../../api/demand'
import api from '../../api'
import IntentionCountdown from '../../components/IntentionCountdown.vue'
import PagedBox from '../../components/PagedBox.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DEMAND_STATUS, label, fmtTime } from '../../utils/labels'

const demands = ref([])
const cancelStats = ref({ last90Days: 0, warn: false })
const todos = ref([])

function canPick(row) {
  return todos.value.some(t => t.type === 'PICK_SOLUTION' && String(t.link).includes('/' + row.id))
}

async function load() {
  try {
    demands.value = await listMine() || []
  } catch {
    demands.value = []
    return
  }
  try { cancelStats.value = await getCancelStats() } catch { /* keep default */ }
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

async function closeOrder(row) {
  await ElMessageBox.confirm(
    '关闭后将扣除买家保证金 50%，按各厂承接区间最高值比重赔偿工厂，剩余保证金退回。确定关闭？',
    '关闭订单',
    { type: 'warning', confirmButtonText: '确认关闭', cancelButtonText: '再想想' },
  )
  await closeSolution(row.id)
  ElMessage.success('订单已关闭')
  load()
}

onMounted(load)
</script>

<style scoped>
.todos { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.todo { width: 260px; }
</style>
