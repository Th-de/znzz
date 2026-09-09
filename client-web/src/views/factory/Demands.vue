<template>
  <div>
      <PagedBox :data="demands" v-slot="{ rows }">
      <el-table :data="rows" border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="productName" label="产品" />
        <el-table-column prop="quantity" label="数量" width="90" />
        <el-table-column label="阶段" width="150">
          <template #default="{ row }">
            <StatusPill :tone="statusTone(row.status)" :text="label(DEMAND_STATUS, row.status)" />
          </template>
        </el-table-column>
        <el-table-column label="发布/意向开始" width="160">
          <template #default="{ row }">{{ fmtTime(row.publishedAt) }}</template>
        </el-table-column>
        <el-table-column label="倒计时" min-width="150">
          <template #default="{ row }">
            <IntentionCountdown v-if="row.status==='PUBLISHED'" :end-at="row.intentionEndAt" />
            <IntentionCountdown v-else-if="row.status==='FACTORY_THINKING'" :end-at="row.factoryThinkingEndAt" />
            <IntentionCountdown v-else-if="row.status==='LOCKING'" :end-at="row.lockingEndAt" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="详情" width="90">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230">
          <template #default="{ row }">
            <el-button v-if="row.status==='PUBLISHED' && row.applied" size="small" disabled>已报名</el-button>
            <el-tooltip v-else-if="row.status==='PUBLISHED' && !creditOk(row)" :content="'当前信用分 ' + myCredit + '，未达到最低要求 ' + (row.minCreditScore ?? 0)" placement="top">
              <el-button size="small" disabled>信用分不足</el-button>
            </el-tooltip>
            <el-button v-else-if="row.status==='PUBLISHED'" size="small" type="primary" @click="openIntention(row)">意向报名</el-button>
            <template v-if="row.status==='FACTORY_THINKING' && row.applied">
              <el-button v-if="row.myQuoteStatus==='LOCKED'" size="small" disabled>已报价</el-button>
              <template v-else>
                <el-button size="small" type="warning" @click="openCommit(row)">填报报价</el-button>
                <el-button size="small" type="danger" @click="exitThinking(row)">取消报名</el-button>
              </template>
            </template>
          </template>
        </el-table-column>
      </el-table>
      </PagedBox>

      <!-- 需求全量详情 -->
      <el-dialog v-model="detailDialog" :title="'需求详情 - ' + (detail.demand?.title || '')" width="720px" :close-on-click-modal="false">
        <el-descriptions :column="2" border v-if="detail.demand">
          <el-descriptions-item label="产品">{{ detail.demand.productName }}</el-descriptions-item>
          <el-descriptions-item label="类别">{{ detail.demand.category }}</el-descriptions-item>
          <el-descriptions-item label="数量">{{ detail.demand.quantity }}</el-descriptions-item>
          <el-descriptions-item label="材料">{{ detail.demand.material }}</el-descriptions-item>
          <el-descriptions-item label="关键公差">{{ detail.demand.tolerance }}</el-descriptions-item>
          <el-descriptions-item label="一般公差">{{ detail.demand.generalTolerance }}</el-descriptions-item>
          <el-descriptions-item label="表面处理">{{ detail.demand.surfaceTreatment }}</el-descriptions-item>
          <el-descriptions-item label="AQL">{{ detail.demand.aql || '-' }}</el-descriptions-item>
          <el-descriptions-item label="认证要求">{{ detail.demand.certification }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.demand.minYield != null" label="最低良率">{{ detail.demand.minYield }}</el-descriptions-item>
          <el-descriptions-item label="发布/意向开始">{{ fmtTime(detail.demand.publishedAt) }}</el-descriptions-item>
          <el-descriptions-item label="意向截止">{{ fmtTime(detail.demand.intentionEndAt) }}</el-descriptions-item>
          <el-descriptions-item label="硬交期">{{ detail.demand.deadlineHard }}</el-descriptions-item>
          <el-descriptions-item label="交付地址">{{ detail.demand.deliveryAddress }}</el-descriptions-item>
          <el-descriptions-item label="包装">{{ detail.demand.packaging }}</el-descriptions-item>
          <el-descriptions-item label="检验方式">{{ formatInspectMode(detail.demand.inspectMode) }}</el-descriptions-item>
          <el-descriptions-item label="图号/版本">{{ detail.demand.partRevision }}</el-descriptions-item>
          <el-descriptions-item label="最低信用分">{{ detail.demand.minCreditScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="分期交付">{{ detail.demand.deliveryTimes || 1 }} 期</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.demand.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
        <h4 style="margin:14px 0 6px">买家信息</h4>
        <BuyerInfoBlock :buyer="detail.buyer" />
        <h4 style="margin:14px 0 6px">每期交付要求</h4>
        <div v-if="deliveryPlanOf(detail.demand).length">
          <div v-for="(t, i) in deliveryPlanOf(detail.demand)" :key="i" class="tip">第{{ i + 1 }}期：{{ t }}</div>
        </div>
        <div v-else class="tip">未分期</div>
        <h4 style="margin:14px 0 6px">工序</h4>
        <el-table :data="detail.processes || []" size="small" border>
          <el-table-column prop="processNo" label="#" width="50" />
          <el-table-column prop="processName" label="工序" />
          <el-table-column prop="requirement" label="要求" />
        </el-table>
        <h4 style="margin:14px 0 6px">零件覆盖</h4>
        <CoverageBars :items="detailCoverage" />
        <h4 style="margin:14px 0 6px">图纸/附件</h4>
        <div v-if="(detail.attachments || []).length">
          <div v-for="a in detail.attachments" :key="a.id">
            <el-button link type="primary" @click="downloadAttachment(a.id)">{{ a.fileName }}</el-button>
          </div>
        </div>
        <div v-else class="tip">无附件</div>
      </el-dialog>

      <el-dialog v-model="dialog" :title="'意向报名 - ' + current.title" width="640px" :close-on-click-modal="false">
        <p class="tip">本需求按「一单一品」承接：报名后由本厂完成全部工序列表中的工序，并按需求件数组织生产。每个需求收取意向金 1000 元，仅冻结一次。</p>
        <h4>工序</h4>
        <el-table :data="processes" size="small" border style="margin-bottom:12px">
          <el-table-column prop="processNo" label="#" width="50" />
          <el-table-column prop="processName" label="工序" />
          <el-table-column prop="requirement" label="要求" />
        </el-table>
        <h4>零件覆盖</h4>
        <CoverageBars :items="coverage" />
        <p class="tip">质检与交付：<InspectDeliveryRules /></p>
        <el-form label-width="110px">
          <el-form-item label="最小承接量">
            <el-input-number v-model="wholeQty.minQty" :min="1" />
            <div class="tip">平台分配给本厂的件数不会低于该值。</div>
          </el-form-item>
          <el-form-item label="最大承接量">
            <el-input-number v-model="wholeQty.maxQty" :min="1" />
            <div class="tip">平台分配给本厂的件数不会高于该值。分配数量始终落在最小与最大承接量之间。</div>
          </el-form-item>
          <el-form-item label=" ">
            <el-checkbox v-model="confirmIntent">确认冻结意向金 1000 元（一单一次）</el-checkbox>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialog=false">取消</el-button>
          <el-button type="primary" :disabled="!confirmIntent" @click="submitIntention">提交报名并支付意向金</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="commitDialog" title="填报报价" width="560px" :close-on-click-modal="false">
        <p class="tip">请填写完成全部工序后的单件价格。承接数量区间以意向报名时填写的为准，提交后不可修改。交期与质量标准按买家发布的需求执行，无需在此填写。提交后按「单价 × 承接区间上限」冻结 5% 履约保证金，每个需求只冻结一次。</p>
        <el-form label-width="120px">
          <el-form-item label="实施方案" required>
            <el-input v-model="commitForm.planText" type="textarea" :rows="3"
                      placeholder="如何组织生产、工艺路线、质量控制措施等" />
          </el-form-item>
          <el-form-item label="工艺">{{ processes.map(p => p.processName).join('、') || '-' }}</el-form-item>
          <el-form-item label="承接区间">{{ commitRangeText }}</el-form-item>
          <el-form-item label="单价(元/件)" required>
            <el-input-number v-model="commitUnit" :min="0.01" :precision="2" />
          </el-form-item>
        </el-form>
        <div class="summary">
          总报价：<b>￥{{ commitTotal }}</b>　需冻结保证金(5%)：<b>￥{{ commitDeposit }}</b>
        </div>
        <el-form label-width="120px">
          <el-form-item label=" ">
            <el-checkbox v-model="commitForm.confirm">确认提交并冻结保证金 ￥{{ commitDeposit }}</el-checkbox>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="commitDialog=false">取消</el-button>
          <el-button type="warning" :disabled="!commitForm.confirm" @click="submitCommit">提交方案并冻结保证金</el-button>
        </template>
      </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import api from '../../api'
import { intention, commit, exitDemand, listMine } from '../../api/bidding'
import { getCoverage } from '../../api/demand'
import { downloadAttachment } from '../../api/file'
import CoverageBars from '../../components/CoverageBars.vue'
import IntentionCountdown from '../../components/IntentionCountdown.vue'
import BuyerInfoBlock from '../../components/BuyerInfoBlock.vue'
import PagedBox from '../../components/PagedBox.vue'
import InspectDeliveryRules from '../../components/InspectDeliveryRules.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DEMAND_STATUS, label, fmtTime, formatDeliveryPeriod, formatInspectMode, statusTone } from '../../utils/labels'
import StatusPill from '../../components/StatusPill.vue'

const router = useRouter()
const demands = ref([])
const myCredit = ref(0)
const processes = ref([])
const dialog = ref(false)
const detailDialog = ref(false)
const commitDialog = ref(false)
const current = ref({})
const detail = ref({})
const coverage = ref([])
const detailCoverage = ref([])
const selectedProcessNos = ref([])
const itemForms = reactive({})
const wholeQty = reactive({ minQty: null, maxQty: null })
const confirmIntent = ref(false)
const commitForm = reactive({ demandId: null, planText: '', deliveryPlan: [], items: [], confirm: false })
const commitUnit = ref(null)
const buyerDeliveryPlan = ref([])

const commitMinQty = computed(() => Number(commitForm.items[0]?.minQty) || 1)
const commitMaxQty = computed(() => {
  const max = Number(commitForm.items[0]?.maxQty)
  const qty = Number(commitForm.items[0]?.quantity)
  return Math.max(Number.isFinite(max) ? max : 0, Number.isFinite(qty) ? qty : 0, commitMinQty.value || 0)
})
const commitRangeText = computed(() => {
  const min = commitMinQty.value
  const max = commitMaxQty.value
  if (!max) return '-'
  return min + ' ~ ' + max + ' 件'
})
const commitTotal = computed(() => ((commitUnit.value || 0) * (commitMaxQty.value || 0)).toFixed(2))
const commitDeposit = computed(() => (Number(commitTotal.value) * 0.05).toFixed(2))

function processName(pno) {
  return processes.value.find(p => p.processNo === pno)?.processName || ('工序' + pno)
}

function deliveryPlanOf(demand) {
  try {
    const arr = JSON.parse(demand?.deliveryPlanJson || '[]')
    return Array.isArray(arr) ? arr.map(formatDeliveryPeriod).filter(Boolean) : []
  } catch { return [] }
}

function buyerPlanHint(i) {
  const t = buyerDeliveryPlan.value[i]
  return t ? `买家要求：${t}` : ''
}

async function load() {
  demands.value = await api.get('/demand/for-factory')
  try {
    const me = await api.get('/enterprise/mine')
    myCredit.value = Number(me?.creditScore) || 0
  } catch {
    myCredit.value = 0
  }
}

function creditOk(row) {
  return myCredit.value >= (Number(row.minCreditScore) || 0)
}

async function ensureProfile() {
  const data = await api.get('/enterprise/capability')
  if (!data.complete) {
    if (!(data.deviceCount > 0)) {
      ElMessage.warning('请先在「我的设备」中至少添加一台设备')
      router.push('/factory/devices')
      return false
    }
    ElMessage.warning('请先完善能力档案')
    router.push('/factory/profile')
    return false
  }
  return true
}

async function loadProcesses(row) {
  const ps = await api.get(`/demand/${row.id}/processes`)
  processes.value = ps.length ? ps : [{ processNo: 1, processName: '整单', quantity: row.quantity }]
}

async function openDetail(row) {
  const d = await api.get(`/demand/${row.id}`)
  detail.value = d
  try {
    const cov = await getCoverage(row.id)
    detailCoverage.value = cov.processes || []
  } catch {
    detailCoverage.value = []
  }
  detailDialog.value = true
}

async function openIntention(row) {
  if (!creditOk(row)) {
    return ElMessage.warning('信用分 ' + myCredit.value + ' 未达到该需求最低要求 ' + (row.minCreditScore ?? 0) + '，无法报名')
  }
  if (!(await ensureProfile())) return
  current.value = row
  selectedProcessNos.value = []
  Object.keys(itemForms).forEach(k => delete itemForms[k])
  confirmIntent.value = false
  await loadProcesses(row)
  const cap = row.quantity || processes.value[0]?.quantity || 1
  wholeQty.minQty = 1
  wholeQty.maxQty = cap
  processes.value.forEach(p => {
    itemForms[p.processNo] = { minQty: 1, maxQty: cap }
    selectedProcessNos.value.push(p.processNo)
  })
  const cov = await getCoverage(row.id)
  coverage.value = cov.processes || []
  dialog.value = true
}

async function submitIntention() {
  if (!wholeQty.minQty || !wholeQty.maxQty) return ElMessage.warning('请填写承接量')
  if (wholeQty.minQty > wholeQty.maxQty) return ElMessage.warning('最小量不能大于最大量')
  const cap = current.value.quantity
  if (cap && wholeQty.maxQty > cap) return ElMessage.warning('最大承接量不能超过需求数量 ' + cap)
  const items = [{ processNo: 1, minQty: wholeQty.minQty, maxQty: wholeQty.maxQty }]
  if (!items.length) return ElMessage.warning('没有可报工序')
  const data = await intention({ demandId: current.value.id, items })
  if (data?.payUrl) {
    window.open(data.payUrl, '_blank')
    ElMessage.success('已打开支付宝沙箱，付完 1000 元意向金后刷新「我的报名」')
  } else {
    ElMessage.success('报名成功，已冻结意向金 1000 元')
  }
  dialog.value = false
  load()
}

async function openCommit(row) {
  current.value = row
  await loadProcesses(row)
  const d = await api.get(`/demand/${row.id}`)
  buyerDeliveryPlan.value = deliveryPlanOf(d.demand)
  const times = d.demand?.deliveryTimes || 1
  const mine = (await listMine()).filter(q => q.demandId === row.id && q.status === 'INTENTION')
  if (!mine.length) return ElMessage.warning('没有可填报的报名')
  commitForm.demandId = row.id
  commitForm.planText = ''
  commitForm.deliveryPlan = Array.from({ length: times }, () => '')
  commitForm.items = mine.map(q => ({
    processNo: q.processNo, minQty: q.minQty, maxQty: q.maxQty, quantity: q.maxQty, unitPrice: null,
  }))
  commitUnit.value = null
  commitForm.confirm = false
  commitDialog.value = true
}

async function submitCommit() {
  if (!commitForm.planText.trim()) return ElMessage.warning('请填写实施方案')
  if (!commitUnit.value) return ElMessage.warning('请填写该品单价')
  await commit({
    demandId: commitForm.demandId,
    planText: commitForm.planText,
    items: commitForm.items.map(it => ({
      processNo: it.processNo, unitPrice: commitUnit.value,
    })),
  })
  ElMessage.success('方案已提交，保证金已冻结')
  commitDialog.value = false
  load()
}

async function exitThinking(row) {
  await ElMessageBox.confirm('确认取消报名？意向金将退回，取消后不能再参加该需求。', '取消报名', { type: 'warning' })
  await exitDemand(row.id)
  ElMessage.success('已取消报名，意向金已退回')
  load()
}

onMounted(load)
</script>

<style scoped>
.tip { color: #606266; font-size: 13px; margin: 0 0 12px; }
.process-block { border: 1px solid #ebeef5; border-radius: 6px; padding: 10px 14px; margin-bottom: 10px; }
.process-block h4 { margin: 0 0 8px; }
.summary { margin: 12px 0; font-size: 14px; }
</style>
