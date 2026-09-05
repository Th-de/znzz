<template>
  <div>
      <div style="margin-bottom:12px">
        <el-button v-if="canCloseSolution" type="danger" @click="closeOrder">关闭订单</el-button>
      </div>
      <el-alert type="info" :closable="false" :title="topHint" style="margin-bottom:12px" />
      <el-card v-if="factoryQuoteList.length" shadow="never" style="margin-bottom:12px">
        <template #header>各工厂报价</template>
        <el-table :data="factoryQuoteList" size="small" border>
          <el-table-column label="工厂" min-width="180">
            <template #default="{ row }">
              <el-button link type="primary" @click="openFactory(row.factoryId)">{{ row.factoryName }}</el-button>
            </template>
          </el-table-column>
          <el-table-column prop="unitPrice" label="单价" width="90" />
          <el-table-column label="承接区间" width="140">
            <template #default="{ row }">{{ (row.minQty ?? '-') }} ~ {{ (row.maxQty ?? '-') }} 件</template>
          </el-table-column>
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
            <el-table :data="factoryAllocRows(s.finalComboJson)" size="small" border>
              <el-table-column label="工厂" min-width="160">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openFactory(row.factoryId)">{{ row.factoryName || ('工厂-' + row.factoryId) }}</el-button>
                  <div class="meta">信用 {{ row.creditScore ?? '-' }} · {{ authText(row.authStatus) }}</div>
                </template>
              </el-table-column>
              <el-table-column label="分配(件)" width="88">
                <template #default="{ row }">{{ row.quantity ?? '-' }}</template>
              </el-table-column>
              <el-table-column label="单价" width="76">
                <template #default="{ row }">{{ row.unitPrice ?? '-' }}</template>
              </el-table-column>
              <el-table-column label="小计" width="90">
                <template #default="{ row }">{{ moneyText(row.price) }}</template>
              </el-table-column>
              <el-table-column prop="days" label="工期" width="56" />
            </el-table>
            <div class="total-bar">
              <span>总计金额</span>
              <strong>{{ moneyText(comboTotal(s.finalComboJson)) }} 元</strong>
            </div>
            <el-button v-if="canEdit" type="primary" style="width:100%;margin-top:10px" @click="select(s)">按此分配确认方案</el-button>
          </el-card>
        </el-col>
        <el-col v-if="canEdit" :span="12">
          <el-card header="自选方案">
            <p class="meta">按零件件数把整单分给已报名工厂。可增删行；各厂件数合计须等于需求量 {{ demandNeed }} 件，且落在该厂承接区间内。小计 = 单价 × 分配件数。</p>
            <div class="proc-head">
              <span class="proc-title">需求 {{ demandNeed }} 件</span>
              <span class="alloc-status" :class="allocSum === demandNeed ? 'ok' : 'bad'">
                已分配 {{ allocSum }} / {{ demandNeed }}
              </span>
            </div>
            <div class="line head">
              <span class="col-del" />
              <span class="col-fac">报名工厂</span>
              <span class="col-range">承接区间</span>
              <span class="col-qty">分配(件)</span>
              <span class="col-amt">小计</span>
            </div>
            <div v-for="(line, i) in customLines" :key="'c-' + i" class="line">
              <span class="col-del">
                <el-button type="danger" :icon="Delete" circle plain size="small" @click="removeLine(i)" />
              </span>
              <el-select v-model="line.factoryId" placeholder="选择工厂" class="col-fac" size="small" filterable clearable>
                <el-option
                  v-for="f in factoryQuoteList"
                  :key="f.factoryId"
                  :label="f.factoryName"
                  :value="f.factoryId"
                  :disabled="usedFactory(f.factoryId, i)"
                />
              </el-select>
              <span class="col-range">{{ rangeText(line.factoryId) }}</span>
              <el-input-number v-model="line.quantity" :min="1" class="col-qty" size="small" controls-position="right" />
              <span class="col-amt">{{ moneyText(lineAmount(line)) }}</span>
            </div>
            <el-button type="primary" :icon="Plus" plain size="small" @click="addLine">添加工厂</el-button>
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
import { getDetail, listBidFactories, closeSolution } from '../../api/demand'
import api from '../../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import { fmtTime, DEVICE_STATUS, label, deviceStatusType } from '../../utils/labels'

const route = useRoute()
const router = useRouter()
const demandId = route.params.demandId
const solutions = ref([])
const demandStatus = ref('')
const demandNeed = ref(0)
const factoryOpen = ref(false)
const factoryInfo = ref({})
const factoryQuotes = ref([])
const customLines = ref([{ factoryId: null, quantity: null }])
const savingCustom = ref(false)

async function openFactory(id) {
  factoryInfo.value = await api.get(`/enterprise/${id}/public-profile`)
  factoryOpen.value = true
}

const canEdit = computed(() => demandStatus.value === 'SOLUTION_GENERATED')
const canCloseSolution = computed(() => ['SOLUTION_GENERATED', 'SOLUTION_CONFIRMED'].includes(demandStatus.value))
const aiSolutions = computed(() => (solutions.value || []).filter(s => (s.type || '').startsWith('AI')))
const factoryQuoteList = computed(() => {
  const out = []
  const seen = new Set()
  for (const f of factoryQuotes.value) {
    if (seen.has(f.id)) continue
    seen.add(f.id)
    const q = (f.quotes || [])[0] || {}
    out.push({
      factoryId: f.id,
      factoryName: f.name,
      unitPrice: q.unitPrice,
      minQty: q.minQty,
      maxQty: q.maxQty,
    })
  }
  return out
})
const topHint = computed(() => {
  if (demandStatus.value === 'SOLUTION_CONFIRMED') return '方案已确认，等待运营派单。确认后不可再改分配。'
  return '上方是各厂报价。AI 推荐仅供参考且不可改；可在自选方案里按零件件数把整单分给工厂后确认。'
})

function parseCombo(json) {
  try { return JSON.parse(json) } catch { return [] }
}
function factoryAllocRows(json) {
  const map = new Map()
  for (const it of parseCombo(json)) {
    const fid = it.factoryId
    if (fid == null) continue
    const qty = Number(it.quantity) || 0
    const unit = Number(it.unitPrice)
    if (!map.has(fid)) {
      map.set(fid, {
        factoryId: fid,
        factoryName: it.factoryName,
        creditScore: it.creditScore,
        authStatus: it.authStatus,
        unitPrice: Number.isFinite(unit) ? unit : null,
        quantity: qty,
        days: Number(it.days) || 0,
        minQty: it.minQty,
        maxQty: it.maxQty,
      })
    } else {
      const row = map.get(fid)
      if (qty > row.quantity) row.quantity = qty
      if ((Number(it.days) || 0) > row.days) row.days = Number(it.days) || 0
    }
  }
  return [...map.values()].map(r => ({
    ...r,
    price: r.unitPrice != null && r.quantity
      ? Math.round(r.unitPrice * r.quantity * 100) / 100
      : null,
  }))
}
function comboTotal(json) {
  const rows = factoryAllocRows(json)
  if (!rows.length) return null
  return Math.round(rows.reduce((s, r) => s + (Number(r.price) || 0), 0) * 100) / 100
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
function quoteOf(factoryId) {
  return factoryQuoteList.value.find(f => f.factoryId === factoryId)
}
function usedFactory(factoryId, idx) {
  return customLines.value.some((l, i) => i !== idx && l.factoryId === factoryId)
}
function rangeText(factoryId) {
  const q = quoteOf(factoryId)
  if (!q) return '-'
  return (q.minQty ?? '-') + ' ~ ' + (q.maxQty ?? '-')
}
function lineAmount(line) {
  const q = quoteOf(line.factoryId)
  const price = Number(q?.unitPrice)
  const qty = Number(line.quantity)
  if (!Number.isFinite(price) || !Number.isFinite(qty) || qty <= 0) return null
  return Math.round(price * qty * 100) / 100
}
function moneyText(n) {
  if (n == null || !Number.isFinite(n)) return '-'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
const allocSum = computed(() =>
  customLines.value.reduce((s, l) => s + (Number(l.quantity) || 0), 0)
)
const customTotal = computed(() => {
  let total = 0
  let any = false
  for (const line of customLines.value) {
    const amt = lineAmount(line)
    if (amt != null) {
      total += amt
      any = true
    }
  }
  return any ? Math.round(total * 100) / 100 : null
})
function addLine() {
  customLines.value.push({ factoryId: null, quantity: null })
}
function removeLine(i) {
  if (customLines.value.length <= 1) {
    ElMessage.warning('至少保留一行')
    return
  }
  customLines.value.splice(i, 1)
}

async function closeOrder() {
  await ElMessageBox.confirm(
    '关闭后将扣除买家保证金 50%，按各厂承接区间最高值比重赔偿工厂，剩余保证金退回。确定关闭？',
    '关闭订单',
    { type: 'warning', confirmButtonText: '确认关闭', cancelButtonText: '再想想' },
  )
  await closeSolution(demandId)
  ElMessage.success('订单已关闭')
  router.push('/buyer/demand/' + demandId)
}

async function load() {
  solutions.value = await listByDemand(demandId)
  try {
    const view = await getDetail(demandId)
    demandStatus.value = view.demand?.status || ''
    demandNeed.value = Number(view.demand?.quantity) || Number(view.processes?.[0]?.quantity) || 0
  } catch { demandStatus.value = '' }
  try {
    factoryQuotes.value = await listBidFactories(demandId)
  } catch { factoryQuotes.value = [] }
  customLines.value = [{ factoryId: null, quantity: null }]
}

async function select(s) {
  await ElMessageBox.confirm('将按该推荐方案提交给运营派单，此后不可再改。', '确认方案', { type: 'warning' })
  await selectSolution(demandId, s.id)
  ElMessage.success('已确认，等待运营派单')
  router.push('/buyer/demand/' + demandId)
}

async function confirmCustom() {
  const lines = customLines.value.filter(l => l.factoryId && Number(l.quantity) > 0)
  if (!lines.length) {
    ElMessage.warning('请选择工厂并填写分配件数')
    return
  }
  if (allocSum.value !== demandNeed.value) {
    ElMessage.warning(`已分配 ${allocSum.value} 件，须等于需求 ${demandNeed.value} 件`)
    return
  }
  const items = []
  for (const l of lines) {
    const q = quoteOf(l.factoryId)
    const qty = Number(l.quantity)
    if (q?.minQty != null && qty < q.minQty) {
      ElMessage.warning(`「${q.factoryName}」分配低于最小承接量 ${q.minQty}`)
      return
    }
    if (q?.maxQty != null && qty > q.maxQty) {
      ElMessage.warning(`「${q.factoryName}」分配超过最大承接量 ${q.maxQty}`)
      return
    }
    items.push({ factoryId: l.factoryId, quantity: qty })
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
