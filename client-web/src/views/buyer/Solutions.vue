<template>
  <div>
      <el-alert type="info" :closable="false" :title="topHint" style="margin-bottom:12px" />
      <el-card v-if="quoteRows.length" shadow="never" style="margin-bottom:12px">
        <template #header>各工厂报价</template>
        <el-table :data="quoteRows" size="small" border>
          <el-table-column label="工厂" min-width="180">
            <template #default="{ row }">
              <el-button link type="primary" @click="openFactory(row.factoryId)">{{ row.factoryName }}</el-button>
            </template>
          </el-table-column>
          <el-table-column prop="processName" label="工序" width="100" />
          <el-table-column prop="unitPrice" label="单价" width="90" />
          <el-table-column label="承接区间" width="120">
            <template #default="{ row }">{{ (row.minQty ?? '-') }} ~ {{ row.maxQty ?? '-' }} 件</template>
          </el-table-column>
          <el-table-column prop="promisedDays" label="工期(天)" width="90" />
        </el-table>
      </el-card>
      <el-empty v-if="!aiSolutions.length && !canEdit" description="推荐方案尚未审核下发，请等待运营" />
      <el-row :gutter="16">
        <el-col :span="12" v-for="s in aiSolutions" :key="s.id">
          <el-card :header="'推荐方案 ' + (s.type || '') + '（仅供参考，不可改）'">
            <p class="meta">生成时间 {{ fmtTime(s.createdAt) }}</p>
            <p class="meta">{{ parseRationale(s.rationaleJson).rationale }}</p>
            <el-alert
              v-for="(r, i) in parseRationale(s.rationaleJson).risks"
              :key="i"
              type="warning"
              :closable="false"
              :title="r"
              style="margin-bottom:8px"
            />
            <div v-if="parseRationale(s.rationaleJson).milestones.length" class="milestones">
              <div class="meta" style="font-weight:600">分期节点</div>
              <div v-for="(m, i) in parseRationale(s.rationaleJson).milestones" :key="i" class="meta">· {{ m }}</div>
            </div>
            <el-table :data="parseCombo(s.finalComboJson)" size="small" border>
              <el-table-column prop="processName" label="工序" width="80" />
              <el-table-column label="工厂" min-width="160">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openFactory(row.factoryId)">{{ row.factoryName || ('工厂-' + row.factoryId) }}</el-button>
                  <div class="meta">信用 {{ row.creditScore ?? '-' }} · {{ authText(row.authStatus) }}</div>
                </template>
              </el-table-column>
              <el-table-column label="分量(件)" width="80">
                <template #default="{ row }">{{ row.quantity ?? '-' }}</template>
              </el-table-column>
              <el-table-column label="单价" width="76">
                <template #default="{ row }">{{ row.unitPrice ?? '-' }}</template>
              </el-table-column>
              <el-table-column prop="price" label="小计" width="80" />
              <el-table-column prop="days" label="工期" width="56" />
            </el-table>
            <el-button v-if="canEdit" type="primary" style="width:100%;margin-top:10px" @click="select(s)">按此分配确认方案</el-button>
          </el-card>
        </el-col>
        <el-col v-if="canEdit" :span="12">
          <el-card header="自选方案">
            <p class="meta">按工序下拉选择已报名工厂，填写分量。可增删行；每道工序分量合计须等于该工序需求量，且落在该厂承接区间内。</p>
            <div v-for="g in customGroups" :key="g.processNo" class="proc-block">
              <div class="proc-head">
                <span class="proc-title">{{ g.processName }} / {{ g.need }}件</span>
                <span class="alloc-status" :class="groupSum(g) === g.need ? 'ok' : 'bad'">
                  已分配 {{ groupSum(g) }} / {{ g.need }}
                </span>
              </div>
              <div class="line head">
                <span class="col-del" />
                <span class="col-fac">报名工厂</span>
                <span class="col-range">承接区间</span>
                <span class="col-qty">分量(件)</span>
                <span class="col-amt">金额</span>
              </div>
              <div v-for="(line, i) in g.lines" :key="g.processNo + '-' + i" class="line">
                <span class="col-del">
                  <el-button type="danger" :icon="Delete" circle plain size="small" @click="removeLine(g, i)" />
                </span>
                <el-select v-model="line.factoryId" placeholder="选择工厂" class="col-fac" size="small" filterable clearable>
                  <el-option
                    v-for="f in factoriesOf(g.processNo)"
                    :key="f.factoryId"
                    :label="f.factoryName"
                    :value="f.factoryId"
                    :disabled="usedFactory(g, f.factoryId, i)"
                  />
                </el-select>
                <span class="col-range">{{ rangeText(g.processNo, line.factoryId) }}</span>
                <el-input-number v-model="line.quantity" :min="1" class="col-qty" size="small" controls-position="right" />
                <span class="col-amt">{{ moneyText(lineAmount(g, line)) }}</span>
              </div>
              <el-button type="primary" :icon="Plus" plain size="small" @click="addLine(g)">添加工厂</el-button>
            </div>
            <div class="total-bar">
              <span>总计金额</span>
              <strong>{{ moneyText(customTotal) }} 元</strong>
            </div>
            <el-button type="primary" style="width:100%;margin-top:10px" :loading="savingCustom" @click="confirmCustom">确认自选方案</el-button>
          </el-card>
        </el-col>
      </el-row>

    <el-dialog v-model="factoryOpen" title="工厂详情" width="640px" :close-on-click-modal="false">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="名称" :span="2">{{ factoryInfo.name }}</el-descriptions-item>
        <el-descriptions-item label="信用分">{{ factoryInfo.creditScore ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="认证">{{ factoryInfo.authStatus === 'APPROVED' ? '已认证' : '未认证' }}</el-descriptions-item>
        <el-descriptions-item label="质检合格率">{{ factoryInfo.inspectionPassRate != null ? factoryInfo.inspectionPassRate + '%' : '暂无记录' }}</el-descriptions-item>
        <el-descriptions-item label="已结算工单">{{ factoryInfo.settledStages ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="买家评分">{{ factoryInfo.surveyAvg ?? '暂无' }}</el-descriptions-item>
        <el-descriptions-item label="日产能合计">{{ factoryInfo.dailyCapacitySum ?? 0 }} 件/天</el-descriptions-item>
        <el-descriptions-item label="体系认证" :span="2">{{ certsText(factoryInfo.certs) }}</el-descriptions-item>
        <el-descriptions-item label="企业介绍" :span="2">{{ factoryInfo.introduction || '-' }}</el-descriptions-item>
      </el-descriptions>
      <h4 style="margin:12px 0 8px">设备</h4>
      <el-table :data="factoryInfo.devices || []" border size="small">
        <el-table-column prop="name" label="设备" min-width="120" />
        <el-table-column prop="processNames" label="适用工序" min-width="120" />
        <el-table-column prop="dailyCapacity" label="日产能" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="deviceStatusType(row.status)" size="small">{{ label(DEVICE_STATUS, row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="添加时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listByDemand, selectSolution, saveCustom } from '../../api/solution'
import { getDetail, listBidFactories } from '../../api/demand'
import api from '../../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import { fmtTime, DEVICE_STATUS, label, deviceStatusType } from '../../utils/labels'

const route = useRoute()
const router = useRouter()
const demandId = route.params.demandId
const solutions = ref([])
const demandStatus = ref('')
const demandProcesses = ref([])
const factoryOpen = ref(false)
const factoryInfo = ref({})
const factoryQuotes = ref([])
const customGroups = ref([])
const savingCustom = ref(false)

async function openFactory(id) {
  factoryInfo.value = await api.get(`/enterprise/${id}/public-profile`)
  factoryOpen.value = true
}

const canEdit = computed(() => demandStatus.value === 'SOLUTION_GENERATED')
const aiSolutions = computed(() => (solutions.value || []).filter(s => (s.type || '').startsWith('AI')))
const quoteRows = computed(() => {
  const rows = []
  for (const f of factoryQuotes.value) {
    for (const q of (f.quotes || [])) {
      rows.push({ factoryName: f.name, factoryId: f.id, ...q })
    }
  }
  return rows
})
const topHint = computed(() => {
  if (demandStatus.value === 'SOLUTION_CONFIRMED') return '方案已确认，等待运营派单。确认后不可再改分配。'
  return '上方是各厂报价。AI 推荐仅供参考且不可改；可在自选方案里按工序选择工厂并分配分量后确认。'
})

function parseCombo(json) {
  try { return JSON.parse(json) } catch { return [] }
}
function parseRationale(raw) {
  if (!raw) return { rationale: '', risks: [], milestones: [] }
  try {
    const o = JSON.parse(raw)
    if (o && typeof o === 'object' && (o.rationale != null || o.risks)) {
      return {
        rationale: o.rationale || '',
        risks: Array.isArray(o.risks) ? o.risks : [],
        milestones: Array.isArray(o.milestones) ? o.milestones : [],
      }
    }
  } catch { /* 旧数据是纯文本 */ }
  return { rationale: raw, risks: [], milestones: [] }
}
function authText(s) {
  return s === 'APPROVED' ? '已认证' : (s || '未认证')
}
function certsText(c) {
  if (Array.isArray(c)) return c.join('、') || '-'
  return c || '-'
}
function factoriesOf(processNo) {
  return quoteRows.value.filter(r => r.processNo === processNo)
}
function usedFactory(g, factoryId, idx) {
  return g.lines.some((l, i) => i !== idx && l.factoryId === factoryId)
}
function quoteOf(processNo, factoryId) {
  return factoriesOf(processNo).find(f => f.factoryId === factoryId)
}
function rangeText(processNo, factoryId) {
  const q = quoteOf(processNo, factoryId)
  if (!q) return '-'
  return (q.minQty ?? '-') + ' ~ ' + (q.maxQty ?? '-')
}
function lineAmount(g, line) {
  const q = quoteOf(g.processNo, line.factoryId)
  const price = Number(q?.unitPrice)
  const qty = Number(line.quantity)
  if (!Number.isFinite(price) || !Number.isFinite(qty) || qty <= 0) return null
  return Math.round(price * qty * 100) / 100
}
function moneyText(n) {
  if (n == null || !Number.isFinite(n)) return '-'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
const customTotal = computed(() => {
  let total = 0
  let any = false
  for (const g of customGroups.value) {
    for (const line of g.lines || []) {
      const amt = lineAmount(g, line)
      if (amt != null) {
        total += amt
        any = true
      }
    }
  }
  return any ? Math.round(total * 100) / 100 : null
})
function groupSum(g) {
  return (g.lines || []).reduce((s, l) => s + (Number(l.quantity) || 0), 0)
}
function addLine(g) {
  g.lines.push({ factoryId: null, quantity: null })
}
function removeLine(g, i) {
  if (g.lines.length <= 1) {
    ElMessage.warning('每道工序至少保留一行')
    return
  }
  g.lines.splice(i, 1)
}
function initCustom() {
  const processes = demandProcesses.value.length
    ? demandProcesses.value
    : [{ processNo: 1, processName: '整单', quantity: 0 }]
  customGroups.value = processes.map(p => ({
    processNo: p.processNo,
    processName: p.processName || ('工序' + p.processNo),
    need: p.quantity || 0,
    lines: [{ factoryId: null, quantity: null }],
  }))
}

async function load() {
  solutions.value = await listByDemand(demandId)
  try {
    const view = await getDetail(demandId)
    demandStatus.value = view.demand?.status || ''
    demandProcesses.value = view.processes || []
  } catch { demandStatus.value = '' }
  try {
    factoryQuotes.value = await listBidFactories(demandId)
  } catch { factoryQuotes.value = [] }
  initCustom()
}

async function select(s) {
  await ElMessageBox.confirm('将按该推荐方案提交给运营派单，此后不可再改。', '确认方案', { type: 'warning' })
  await selectSolution(demandId, s.id)
  ElMessage.success('已确认，等待运营派单')
  router.push('/buyer/demand/' + demandId)
}

async function confirmCustom() {
  const items = []
  for (const g of customGroups.value) {
    const lines = g.lines.filter(l => l.factoryId && Number(l.quantity) > 0)
    if (!lines.length) {
      ElMessage.warning(`请为工序「${g.processName}」选择工厂并填写分量`)
      return
    }
    if (groupSum(g) !== g.need) {
      ElMessage.warning(`工序「${g.processName}」已分配 ${groupSum(g)} 件，须等于 ${g.need} 件`)
      return
    }
    for (const l of lines) {
      const q = quoteOf(g.processNo, l.factoryId)
      const qty = Number(l.quantity)
      if (q?.minQty != null && qty < q.minQty) {
        ElMessage.warning(`「${q.factoryName}」分量低于最小承接量 ${q.minQty}`)
        return
      }
      if (q?.maxQty != null && qty > q.maxQty) {
        ElMessage.warning(`「${q.factoryName}」分量超过最大承接量 ${q.maxQty}`)
        return
      }
      items.push({ processNo: g.processNo, factoryId: l.factoryId, quantity: qty })
    }
  }
  await ElMessageBox.confirm('确认后将按自选分配提交给运营派单，此后不可再改。', '确认自选方案', { type: 'warning' })
  savingCustom.value = true
  try {
    const s = await saveCustom(demandId, items)
    await selectSolution(demandId, s.id)
    ElMessage.success('已确认，等待运营派单')
    router.push('/buyer/demand/' + demandId)
  } finally {
    savingCustom.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.meta { color: #909399; font-size: 12px; }
.milestones { margin-bottom: 8px; padding: 6px 8px; background: #f5f7fa; border-radius: 4px; }
.proc-block { margin-bottom: 16px; padding-bottom: 4px; border-bottom: 1px solid #ebeef5; }
.proc-block:last-of-type { border-bottom: none; }
.proc-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 6px; }
.proc-title { font-weight: 600; font-size: 13px; color: #303133; }
.alloc-status { font-size: 12px; }
.alloc-status.ok { color: #67c23a; }
.alloc-status.bad { color: #f56c6c; }
.line { display: flex; align-items: center; gap: 8px; flex-wrap: nowrap; margin-bottom: 8px; }
.line.head { color: #909399; font-size: 12px; margin-bottom: 4px; }
.col-del { width: 28px; flex: 0 0 28px; }
.col-fac { flex: 1 1 auto; min-width: 0; }
.col-range { flex: 0 0 88px; width: 88px; color: #909399; font-size: 12px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.col-qty { flex: 0 0 110px; width: 110px; }
.col-amt { flex: 0 0 86px; width: 86px; text-align: right; white-space: nowrap; font-variant-numeric: tabular-nums; }
.line :deep(.el-select) { width: 100%; }
.line :deep(.el-input-number) { width: 110px; }
.total-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin: 8px 0 4px;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 13px;
  color: #606266;
}
.total-bar strong { font-size: 16px; color: #303133; font-variant-numeric: tabular-nums; }
</style>
