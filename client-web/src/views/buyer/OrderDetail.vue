<template>
  <div>
      <el-card header="订单摘要">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="需求">{{ order.title || '-' }}</el-descriptions-item>
          <el-descriptions-item label="产品">{{ order.productName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="数量">{{ order.quantity ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="硬交期">{{ order.deadlineHard || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ label(ORDER_STATUS, order.status) }}</el-descriptions-item>
          <el-descriptions-item label="总金额">{{ order.totalAmount ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="佣金">{{ order.commissionAmount ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ fmtTime(order.createdAt) }}</el-descriptions-item>
        </el-descriptions>
        <el-steps :active="order.flowActive ?? 0" finish-status="success" align-center style="margin-top:20px">
          <el-step v-for="(s, i) in (order.flowSteps || defaultSteps)" :key="i" :title="s" />
        </el-steps>
        <p class="hint" style="margin-top:12px">{{ orderHint }}</p>
      </el-card>

      <el-card header="按厂承包" style="margin-top:16px">
        <el-table :data="order.combo || []" border>
          <el-table-column prop="factoryName" label="工厂" />
          <el-table-column label="承接区间" width="150">
            <template #default="{ row }">{{ (row.minQty ?? '-') }} ~ {{ (row.maxQty ?? '-') }} 件</template>
          </el-table-column>
          <el-table-column prop="processName" label="工序" />
          <el-table-column prop="quantity" label="数量" width="90" />
          <el-table-column prop="price" label="价格" width="100" />
          <el-table-column prop="days" label="工期(天)" width="90" />
        </el-table>
      </el-card>

      <el-card header="按厂合同" style="margin-top:16px">
        <el-table :data="contractList" border>
          <el-table-column prop="factoryName" label="工厂" />
          <el-table-column label="承接区间" width="150">
            <template #default="{ row }">{{ (row.minQty ?? '-') }} ~ {{ (row.maxQty ?? '-') }} 件</template>
          </el-table-column>
          <el-table-column prop="processNames" label="承包工序" />
          <el-table-column label="合同文件" min-width="180">
            <template #default="{ row }">
              <span v-if="!row.attachmentId">未上传</span>
              <el-button v-else size="small" link type="primary" @click="openFile(row.attachmentId)">{{ row.fileName || ('附件 #' + row.attachmentId) }}</el-button>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="160">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="签署时间" width="160">
            <template #default="{ row }">{{ fmtTime(row.signedAt) }}</template>
          </el-table-column>
          <el-table-column label="签署" min-width="220">
            <template #default="{ row }">
              <div>{{ contractStatus(row.status) }}</div>
              <div class="hint">{{ row.signHint || '' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button v-if="row.status!=='SIGNED'" size="small" type="primary" @click="openUpload(row)">
                {{ row.attachmentId ? '更换合同' : '上传合同' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button
          type="success"
          style="margin-top:12px"
          :disabled="!canUnifySign"
          @click="openUnifySign"
        >签名</el-button>
        <span v-if="!allContractsUploaded" class="hint" style="margin-left:10px">请先为每个工厂分别上传合同</span>
        <span v-else-if="buyerAllSigned" class="hint" style="margin-left:10px">买家已统一签名，等待各厂签署</span>
      </el-card>

      <el-card header="工单与阶段款" style="margin-top:16px">
        <el-table :data="stageList" border>
          <el-table-column prop="factoryName" label="工厂" min-width="140" />
          <el-table-column label="承接区间" width="150">
            <template #default="{ row }">{{ (row.minQty ?? '-') }} ~ {{ (row.maxQty ?? '-') }} 件</template>
          </el-table-column>
          <el-table-column prop="processName" label="工序" />
          <el-table-column prop="quantity" label="数量" width="80" />
          <el-table-column prop="promisedDate" label="承诺交期" width="120" />
          <el-table-column label="创建时间" width="160">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="进度" width="140">
            <template #default="{ row }">
              <el-progress :percentage="row.actualProgress || 0" :stroke-width="10" />
              <el-tag v-if="overdueDays(row)" type="danger" size="small">逾期 {{ overdueDays(row) }} 天</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="amount" label="本段工钱" width="110" />
          <el-table-column label="应付托管" width="110">
            <template #default="{ row }">{{ row.payAmount != null ? row.payAmount : '-' }}</template>
          </el-table-column>
          <el-table-column label="质检" width="110">
            <template #default="{ row }">{{ label(STAGE_STATUS, row.status) }}</template>
          </el-table-column>
          <el-table-column label="托管" width="110">
            <template #default="{ row }">{{ escrowText(row.escrowStatus) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="340">
            <template #default="{ row }">
              <el-button size="small" @click="openProgress(row)">进度记录</el-button>
              <el-button v-if="row.status==='PENDING_INSPECT_PAY' && row.inspectFeePayer!=='FACTORY'" size="small" type="warning" @click="payFee(row)">支付质检费 ¥{{ row.inspectFeeAmount }}</el-button>
              <el-button v-if="['PASS','FAIL','CLOSED'].includes(row.status)" size="small" @click="openInsp(row)">质检报告</el-button>
              <el-button v-if="row.status==='FAIL'" size="small" type="warning" @click="openDecide(row)">处理结果</el-button>
              <el-button v-if="row.status==='PASS' && (row.escrowStatus==='NONE' || row.escrowStatus==='PENDING_PAY')" size="small" type="primary" @click="pay(row)">{{ row.escrowStatus==='PENDING_PAY' ? '继续支付' : ('支付 ¥' + (row.payAmount ?? row.amount)) }}</el-button>
              <el-button v-if="row.status==='PASS' && !row.surveyed" size="small" @click="openSurvey(row)">评价</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button
          v-if="order.status==='IN_PRODUCTION'"
          type="success"
          style="margin-top:12px"
          @click="doAccept"
        >完工确认</el-button>
      </el-card>

    <el-dialog v-model="upOpen" :title="'上传合同 · ' + (current?.factoryName || '')" width="520px" destroy-on-close :close-on-click-modal="false" @closed="resetUpload">
      <p>请上传与该厂约定的合同文件。</p>
      <p v-if="current?.fileName" class="hint">该厂当前合同：{{ current.fileName }}</p>
      <el-upload
        :key="uploadKey"
        v-model:file-list="uploadList"
        :auto-upload="false"
        :limit="1"
        :on-change="onFile"
        :on-remove="onUploadRemove"
        :on-exceed="onUploadExceed"
      >
        <el-button>选择文件</el-button>
      </el-upload>
      <template #footer>
        <el-button @click="upOpen=false">取消</el-button>
        <el-button type="primary" :disabled="!file" @click="doUpload">上传该厂合同</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="signOpen" title="签名" width="520px" :close-on-click-modal="false">
      <ul class="hint" style="padding-left:18px">
        <li v-for="c in contractList" :key="c.id">{{ c.factoryName }}：{{ c.fileName || (c.attachmentId ? ('附件 #' + c.attachmentId) : '未上传') }}</li>
      </ul>
      <el-checkbox v-model="read">我已阅读各厂合同全文</el-checkbox>
      <p>手写签名</p>
      <SignPad @change="sign = $event" />
      <template #footer>
        <el-button @click="signOpen=false">取消</el-button>
        <el-button type="success" :disabled="!read || !sign" @click="doSign">提交签名</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="progOpen" title="进度记录" width="560px">
      <el-timeline>
        <el-timeline-item v-for="p in progLogs" :key="p.id" :timestamp="p.createdAt">
          {{ p.doneQty }} / {{ progStage?.quantity }} 件 · {{ p.progress }}% · {{ p.remark }}
          <div v-if="p.attachmentId">
            <el-button size="small" link type="primary" @click="openFile(p.attachmentId)">{{ p.fileName || '现场照片' }}</el-button>
          </div>
        </el-timeline-item>
      </el-timeline>
      <p v-if="!progLogs.length">暂无上报</p>
    </el-dialog>
    <el-dialog v-model="inspOpen" title="质检报告" width="480px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="结论">{{ inspReport.result || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实交数量">{{ inspJson.deliveredQty ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="抽检数">{{ inspJson.sampleCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="inspJson.aqlAc != null" label="AQL Ac/Re">{{ inspJson.aqlAc }} / {{ inspJson.aqlRe }}</el-descriptions-item>
        <el-descriptions-item label="关键公差不合格">{{ inspJson.criticalFailCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="一般公差不合格">{{ inspJson.generalFailCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="合格比例">{{ inspJson.actualYield ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="公差是否合格">{{ inspJson.toleranceOk === true ? '是' : inspJson.toleranceOk === false ? '否' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="数量达标">{{ inspJson.quantityOk ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="关键尺寸">{{ inspJson.keyDimensions || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ inspJson.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
    <el-dialog v-model="decideOpen" title="处理质检结果" width="560px" :close-on-click-modal="false">
      <p>分支 {{ decideStage?.branchCode || '-' }}。让步：B 不齐但抽检/全检过关（托管已交工费 + 本阶段工费 5%）；C1 齐但轻微不良（托管全款。抽检再赔本阶段工费 5%；全检赔工费×(最低良率−实际良率)）。C2/D/E 不能让步。</p>
      <p class="hint">关闭将取消该厂后续期，并按「本期+后续工费」5% 从工厂保证金赔你；保证金不足则无法关闭。返工全程一次，期限由你填写（12～72 小时），与原分期截止无关。</p>
      <el-form label-width="120px" style="margin-top:12px">
        <el-form-item label="返工期限(小时)">
          <el-input-number v-model="reworkHours" :min="12" :max="72" :disabled="!decideStage?.canRework" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="decideOpen=false">取消</el-button>
        <el-button type="success" :disabled="!decideStage?.canConcede" @click="doDecide('CONCESSION')">让步交款</el-button>
        <el-button type="warning" :disabled="!decideStage?.canRework" @click="doDecide('REWORK')">要求返工</el-button>
        <el-button type="danger" @click="doDecide('CLOSE')">关闭本段</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="surveyOpen" title="总体评价" width="420px">
      <el-form label-width="100px">
        <el-form-item label="总体评价">
          <el-rate v-model="overall" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="surveyOpen=false">取消</el-button>
        <el-button type="primary" @click="doSurvey">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getOrder, listContracts, uploadContract, buyerSign, stages, payStage, payInspectFee, accept, submitSurvey, uploadFile, downloadAttachment, progressLog, inspectionOf, decideInspect } from '../../api/order'
import SignPad from '../../components/SignPad.vue'
import { ElMessage } from 'element-plus'
import { ORDER_STATUS, STAGE_STATUS, ESCROW_STATUS, label, fmtTime } from '../../utils/labels'

const route = useRoute()
const orderId = route.params.id
const order = ref({})
const defaultSteps = ['选定方案', '合同', '开工', '质检托管', '完工结算']
const contractList = ref([])
const stageList = ref([])
const file = ref(null)
const uploadList = ref([])
const uploadKey = ref(0)
const read = ref(false)
const sign = ref('')
const upOpen = ref(false)
const signOpen = ref(false)
const current = ref(null)
const surveyOpen = ref(false)
const surveyStage = ref(null)
const overall = ref(5)
const progOpen = ref(false)
const progLogs = ref([])
const progStage = ref(null)
const inspOpen = ref(false)
const inspReport = ref({})
const decideOpen = ref(false)
const decideStage = ref(null)
const reworkHours = ref(24)
const inspJson = computed(() => {
  try { return inspReport.value.reportJson ? JSON.parse(inspReport.value.reportJson) : {} } catch { return {} }
})
const orderHint = computed(() => {
  const list = contractList.value || []
  const needFile = list.filter(c => c.status !== 'SIGNED' && !c.attachmentId).length
  if (needFile) return '请先为每个工厂分别上传合同，再统一签名'
  const needSign = list.filter(c => c.status !== 'SIGNED' && !(c.buyerSign && String(c.buyerSign).trim())).length
  if (needSign) return '各厂合同已上传，请点「统一签名」一次签完'
  const need = list.filter(c => c.status !== 'SIGNED').length
  if (need) return '有 ' + need + ' 份合同待工厂签署或运营确认'
  const pay = (stageList.value || []).filter(s => s.status === 'PASS' && (s.escrowStatus === 'NONE' || s.escrowStatus === 'PENDING_PAY'))
  if (pay.length) return '有 ' + pay.length + ' 笔阶段款待支付'
  const wait = (stageList.value || []).filter(s => s.status === 'FAIL')
  if (wait.length) return '有 ' + wait.length + ' 个工单待处理质检结果（让步 / 返工 / 关闭）'
  if (order.value.status === 'IN_PRODUCTION') return '生产进行中，可查看各工序进度'
  if (order.value.status === 'COMPLETED') return '订单已完成'
  return '按步骤完成合同、质检与托管即可'
})

function contractStatus(s) {
  return ({ DRAFT: '待上传/签署', PENDING_REVIEW: '待平台确认', SIGNED: '已确认' })[s] || s || '-'
}
const allContractsUploaded = computed(() => {
  const list = contractList.value || []
  return list.length > 0 && list.every(c => c.status === 'SIGNED' || c.attachmentId)
})
const buyerAllSigned = computed(() => {
  const list = (contractList.value || []).filter(c => c.status !== 'SIGNED')
  return list.length > 0 && list.every(c => c.buyerSign && String(c.buyerSign).trim())
})
const canUnifySign = computed(() => allContractsUploaded.value && (contractList.value || []).some(c => c.status !== 'SIGNED'))
function escrowText(s) {
  return label(ESCROW_STATUS, s)
}
function overdueDays(row) {
  if (!row?.promisedDate) return 0
  if (['PASS', 'FAIL', 'CLOSED', 'COMPLETED'].includes(row.status) || row.escrowStatus === 'SETTLED') return 0
  const d = Math.ceil((Date.now() - new Date(row.promisedDate).getTime()) / 86400000)
  return d > 0 ? d : 0
}
async function openProgress(row) {
  progStage.value = row
  progLogs.value = await progressLog(row.id)
  progOpen.value = true
}
async function openInsp(row) {
  inspReport.value = (await inspectionOf(row.id)) || {}
  inspOpen.value = true
}
function openDecide(row) {
  decideStage.value = row
  reworkHours.value = 24
  decideOpen.value = true
}
async function doDecide(action) {
  if (action === 'REWORK' && (reworkHours.value < 12 || reworkHours.value > 72)) {
    return ElMessage.warning('返工期限须为 12～72 小时')
  }
  await decideInspect(decideStage.value.id, { action, reworkHours: reworkHours.value })
  ElMessage.success(action === 'CONCESSION' ? '已让步，请支付本阶段托管款' : (action === 'REWORK' ? '已通知工厂返工' : '本段已关闭'))
  decideOpen.value = false
  load()
}

async function load() {
  order.value = await getOrder(orderId)
  contractList.value = await listContracts(orderId)
  try { stageList.value = await stages(orderId) } catch { stageList.value = [] }
}

async function openFile(id) {
  try {
    await downloadAttachment(id)
  } catch (e) {
    ElMessage.error(e.message || '无法打开合同')
  }
}

function resetUpload() {
  file.value = null
  uploadList.value = []
}

function openUpload(row) {
  resetUpload()
  current.value = row
  uploadKey.value += 1
  upOpen.value = true
}

function openUnifySign() {
  if (!allContractsUploaded.value) {
    ElMessage.warning('请先为每个工厂分别上传合同')
    return
  }
  read.value = false
  sign.value = ''
  signOpen.value = true
}

function onFile(f, list) {
  file.value = f?.raw || null
  uploadList.value = list ? list.slice(-1) : []
}

function onUploadRemove() {
  file.value = null
  uploadList.value = []
}

function onUploadExceed(files) {
  const f = files?.[0]
  file.value = f || null
  uploadList.value = f ? [{ name: f.name, uid: Date.now(), raw: f, status: 'ready' }] : []
}

async function doUpload() {
  const up = await uploadFile(file.value, 'CONTRACT')
  await uploadContract(orderId, up.id, current.value.tenantId)
  ElMessage.success('已上传「' + (current.value.factoryName || '该厂') + '」合同')
  resetUpload()
  upOpen.value = false
  await load()
}

async function doSign() {
  await buyerSign(orderId, true, sign.value)
  ElMessage.success('已成功签名')
  signOpen.value = false
  load()
}

async function pay(row) {
  const res = await payStage(row.id)
  const data = res?.data || res
  if (data?.payUrl) {
    window.open(data.payUrl, '_blank')
    ElMessage.success(data.message || '已打开支付宝沙箱，付完后刷新本页')
  } else {
    ElMessage.success(data?.message || '已托管到平台，工厂余额不变')
  }
  load()
}

async function payFee(row) {
  await payInspectFee(row.id)
  ElMessage.success('质检费已支付，等待质检')
  load()
}

async function doAccept() {
  await accept(orderId)
    ElMessage.success('完工确认完成，已结算；佣金已从托管工钱扣除')
  load()
}

function openSurvey(row) {
  surveyStage.value = row
  overall.value = 5
  surveyOpen.value = true
}

async function doSurvey() {
  await submitSurvey(surveyStage.value.id, { overall: overall.value })
  ElMessage.success('评价已提交')
  surveyOpen.value = false
  load()
}

onMounted(load)
</script>

<style scoped>
.hint { color: #909399; font-size: 12px; }
</style>
