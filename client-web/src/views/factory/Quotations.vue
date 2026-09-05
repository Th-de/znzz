<template>
  <div>
    <el-table :data="rows" border>
      <el-table-column prop="demandId" label="需求ID" width="90" />
          <el-table-column prop="demandTitle" label="需求标题" min-width="160" />
          <el-table-column label="需求详情" width="100">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="openDetail(row)">查看详情</el-button>
            </template>
          </el-table-column>
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
          <el-table-column label="操作" width="320">
            <template #default="{ row }">
              <el-button v-if="row.actionKey==='PAY'" size="small" type="primary" @click="pay(row)">继续支付</el-button>
              <template v-else-if="row.actionKey==='WAIT_INTENTION'">
                <span class="op-text">已报名，等待意向期结束</span>
                <el-button size="small" type="danger" @click="cancel(row)">取消报名</el-button>
              </template>
              <template v-else-if="row.actionKey==='COMMIT'">
                <el-button size="small" type="warning" @click="openCommit(row)">填报报价</el-button>
                <el-button size="small" type="danger" @click="cancel(row)">取消报名</el-button>
              </template>
              <span v-else-if="row.actionKey==='WAIT_BUYER'" class="op-text">等待买家决定</span>
              <span v-else-if="row.actionKey==='WAIT_SOLUTION'" class="op-text">等待买家确认方案</span>
              <span v-else-if="row.actionKey==='WAIT_DISPATCH'" class="op-text">等待运营派单</span>
              <span v-else-if="row.actionKey==='WAIT_ISSUE'" class="op-text">待下发合同</span>
              <template v-else-if="row.actionKey==='SIGN'">
                <el-button size="small" type="primary" @click="openSign(row)">签署合同</el-button>
                <el-button size="small" type="danger" @click="cancelOrder(row)">取消订单</el-button>
              </template>
              <span v-else-if="row.actionKey==='PENDING_REVIEW'" class="op-text">待运营确认</span>
              <span v-else-if="row.actionKey==='SIGNED'" class="op-text">已签约</span>
              <span v-else-if="row.actionKey==='LOSE'" class="op-text">未中标</span>
              <el-button v-else-if="row.actionKey==='REAPPLY'" size="small" type="primary" @click="$router.push('/factory/demands')">重新填报意向</el-button>
              <span v-else class="op-text">-</span>
            </template>
          </el-table-column>
    </el-table>

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
        <el-descriptions-item label="分期交付">{{ detail.demand.deliveryTimes || 1 }} 期</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.demand.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="意向金">{{ intentionLabel }}</el-descriptions-item>
        <el-descriptions-item label="保证金">{{ depositLabel }}</el-descriptions-item>
        <el-descriptions-item label="承接区间">{{ qtyRangeLabel }}</el-descriptions-item>
        <el-descriptions-item label="报价单价">{{ unitPriceLabel }}</el-descriptions-item>
        <el-descriptions-item label="总报价">{{ totalPriceLabel }}</el-descriptions-item>
      </el-descriptions>
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
      <h4 style="margin:14px 0 6px">图纸/附件</h4>
      <div v-if="(detail.attachments || []).length">
        <div v-for="a in detail.attachments" :key="a.id">
          <el-button link type="primary" @click="downloadAttachment(a.id)">{{ a.fileName }}</el-button>
        </div>
      </div>
      <div v-else class="tip">无附件</div>
    </el-dialog>

    <el-dialog v-model="commitDialog" title="填报报价" width="560px" :close-on-click-modal="false">
      <p class="tip">报该品全部工序的一件单价。承接量为意向报名时填写的区间，不可改。工期与最低良率按买家发布需求执行。提交后按「单价 × 承接区间最高值」冻结 5% 保证金（只冻一次）。</p>
      <el-form label-width="120px">
        <el-form-item label="实施方案" required>
          <el-input v-model="commitForm.planText" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="承接区间">{{ commitRangeText }}</el-form-item>
        <el-form-item label="单价(元/件)" required>
          <el-input-number v-model="commitUnit" :min="0.01" :precision="2" />
        </el-form-item>
      </el-form>
      <div class="summary">总报价：<b>￥{{ commitTotal }}</b>　需冻结保证金(5%)：<b>￥{{ commitDeposit }}</b></div>
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

    <el-dialog v-model="signOpen" :title="alreadySigned ? '合同详情' : '阅读并签署合同'" width="520px" :close-on-click-modal="false">
      <p>订单 #{{ signOrderId }} · {{ contract.factoryName || '' }}</p>
      <p>状态：{{ alreadySigned ? '已签约' : (contract.status || '待签署') }}</p>
      <p v-if="contract.attachmentId">
        合同文件
        <el-button size="small" link type="primary" @click="openContractFile(contract.attachmentId)">{{ contract.fileName || ('附件 #' + contract.attachmentId) }}</el-button>
      </p>
      <p v-else>买家尚未下发与你的合同</p>
      <template v-if="!alreadySigned">
        <el-checkbox v-model="signRead">我已阅读合同全文</el-checkbox>
        <p>手写签名</p>
        <SignPad @change="signData = $event" />
      </template>
      <template #footer>
        <el-button v-if="alreadySigned" type="primary" @click="signOpen=false">关闭</el-button>
        <template v-else>
          <el-button @click="signOpen=false">取消</el-button>
          <el-button type="primary" :disabled="!signRead || !signData || !contract.attachmentId" @click="doSign">提交签名</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted } from 'vue'
import api from '../../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { payIntention, commit } from '../../api/bidding'
import { getContract, factorySign, cancelByFactory } from '../../api/order'
import { downloadAttachment } from '../../api/file'
import SignPad from '../../components/SignPad.vue'
import { DEMAND_STATUS, INTENTION_STATUS, DEPOSIT_STATUS, label, fmtTime, formatDeliveryPeriod, formatInspectMode } from '../../utils/labels'

const quotations = ref([])
const detailDialog = ref(false)
const commitDialog = ref(false)
const detail = ref({})
const detailQuotes = ref([])
const processes = ref([])
const commitForm = reactive({ demandId: null, planText: '', items: [], confirm: false })
const commitUnit = ref(null)
const signOpen = ref(false)
const signOrderId = ref(null)
const contract = ref({})
const signRead = ref(false)
const signData = ref('')
const alreadySigned = computed(() => contract.value.status === 'SIGNED')

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
        actionKey: q.actionKey,
        orderId: q.orderId,
        createdAt: q.createdAt,
        updatedAt: q.updatedAt,
        quotes: [],
      })
    }
    const g = map.get(q.demandId)
    g.quotes.push(q)
    if (q.createdAt && (!g.createdAt || q.createdAt < g.createdAt)) g.createdAt = q.createdAt
    const prefer = { PAY: 6, COMMIT: 6, WAIT_INTENTION: 5, SIGN: 5, PENDING_REVIEW: 5, WAIT_ISSUE: 4, WAIT_BUYER: 4, WAIT_SOLUTION: 4, WAIT_DISPATCH: 4, SIGNED: 4, REAPPLY: 2, LOSE: 1, NONE: 0 }
    if ((prefer[q.actionKey] || 0) > (prefer[g.actionKey] || 0)) {
      g.actionKey = q.actionKey
      g.orderId = q.orderId || g.orderId
    }
  }
  return [...map.values()]
})

const intentionLabel = computed(() => {
  const q = detailQuotes.value.find(x => x.intentionStatus === 'FROZEN')
    || detailQuotes.value.find(x => x.intentionStatus === 'COVERED')
    || detailQuotes.value[0]
  return q ? label(INTENTION_STATUS, q.intentionStatus) : '-'
})
const depositLabel = computed(() => {
  const q = detailQuotes.value.find(x => x.depositStatus && x.depositStatus !== 'NONE') || detailQuotes.value[0]
  return q ? label(DEPOSIT_STATUS, q.depositStatus) : '-'
})
const unitPriceLabel = computed(() => {
  const priced = detailQuotes.value.filter(q => q.unitPrice != null)
  if (!priced.length) return ''
  if (priced.length === 1) return priced[0].unitPrice
  return priced.map(q => `工序${q.processNo}: ${q.unitPrice}`).join('；')
})
const totalPriceLabel = computed(() => {
  const priced = detailQuotes.value.filter(q => q.price != null)
  if (!priced.length) return ''
  const nums = priced.map(q => Number(q.price))
  const uniq = [...new Set(nums)]
  return (uniq.length === 1 ? uniq[0] : Math.max(...nums)).toFixed(2)
})

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
const qtyRangeLabel = computed(() => {
  const q = detailQuotes.value[0]
  if (!q) return '-'
  const min = q.minQty ?? 1
  const max = q.maxQty ?? q.quantity
  if (max == null) return '-'
  return min + ' ~ ' + max + ' 件'
})

function processName(pno) {
  return processes.value.find(p => p.processNo === pno)?.processName || ('工序' + pno)
}

function deliveryPlanOf(demand) {
  try {
    const arr = JSON.parse(demand?.deliveryPlanJson || '[]')
    return Array.isArray(arr) ? arr.map(formatDeliveryPeriod).filter(Boolean) : []
  } catch { return [] }
}

async function load() {
  quotations.value = await api.get('/bidding/mine')
}

async function openDetail(row) {
  const d = await api.get(`/demand/${row.demandId}`)
  detail.value = d
  detailQuotes.value = row.quotes
  detailDialog.value = true
}

async function openCommit(row) {
  const ps = await api.get(`/demand/${row.demandId}/processes`)
  processes.value = ps.length ? ps : [{ processNo: 1, processName: '整单', quantity: 0 }]
  const mine = row.quotes.filter(q => q.status === 'INTENTION')
  if (!mine.length) return ElMessage.warning('没有可填报的报名')
  commitForm.demandId = row.demandId
  commitForm.planText = mine.find(q => q.planText)?.planText || ''
  commitForm.items = mine.map(q => ({
    processNo: q.processNo,
    minQty: q.minQty,
    maxQty: q.maxQty,
    quantity: q.maxQty,
    unitPrice: q.unitPrice || null,
  }))
  commitUnit.value = mine.find(q => q.unitPrice)?.unitPrice || null
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

async function pay(row) {
  const q = row.quotes.find(x => x.intentionStatus === 'PENDING_PAY')
  if (!q) return
  const data = await payIntention(q.id)
  if (data?.payUrl) {
    window.open(data.payUrl, '_blank')
    ElMessage.success('已打开支付宝沙箱，付完后刷新本页')
  }
}

async function cancel(row) {
  const q = row.quotes.find(x => x.status === 'INTENTION')
  if (!q) return
  await ElMessageBox.confirm('确认取消报名？意向金将退回。意向期内取消后，可重新填写承接量并再次报名。', '取消报名', { type: 'warning' })
  await api.post(`/bidding/${q.id}/cancel-intention`)
  ElMessage.success('已取消。请到「浏览需求」重新填报意向')
  load()
}

async function openSign(row) {
  if (!row.orderId) return ElMessage.warning('尚未生成订单')
  signOrderId.value = row.orderId
  signRead.value = false
  signData.value = ''
  contract.value = await getContract(row.orderId)
  signOpen.value = true
}

async function openContractFile(id) {
  try {
    await downloadAttachment(id)
  } catch (e) {
    ElMessage.error(e.message || '无法打开合同')
  }
}

async function doSign() {
  await factorySign(signOrderId.value, true, signData.value)
  ElMessage.success('已签署合同')
  signOpen.value = false
  load()
}

async function cancelOrder(row) {
  if (!row.orderId) return ElMessage.warning('尚未生成订单')
  await ElMessageBox.confirm(
    '取消后将扣除你缴纳的保证金赔偿买家，其他工厂合同继续。确定取消？',
    '取消订单',
    { type: 'warning', confirmButtonText: '再确认一次', cancelButtonText: '返回' },
  )
  await ElMessageBox.confirm(
    '请再次确认：此操作不可撤销，将扣除本厂保证金赔偿买家。',
    '二次确认',
    { type: 'warning', confirmButtonText: '确认取消订单', cancelButtonText: '返回' },
  )
  await cancelByFactory(row.orderId)
  ElMessage.success('已取消本厂订单')
  load()
}

onMounted(load)
</script>

<style scoped>
.tip { color: #606266; font-size: 13px; margin: 0 0 12px; }
.summary { margin: 12px 0; font-size: 14px; }
.op-text { color: #909399; font-size: 13px; }
</style>
