<template>
  <div>
      <el-alert type="info" :closable="false" title="合同未双签且平台未审过时，不能交付。质检合格后工钱先托管，完工才结算。" style="margin-bottom:12px" />
      <h4 v-if="pendingOrders.length">待签合同</h4>
      <el-table v-if="pendingOrders.length" :data="pendingOrders" border style="margin-bottom:16px">
        <el-table-column prop="title" label="需求" />
        <el-table-column prop="productName" label="产品" width="140" />
        <el-table-column prop="totalAmount" label="金额" width="120" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openSign(row)">签合同</el-button>
          </template>
        </el-table-column>
      </el-table>
      <h4>工单</h4>
      <el-table :data="stageList" border>
        <el-table-column prop="processName" label="工序" />
        <el-table-column prop="quantity" label="数量" width="80" />
        <el-table-column prop="promisedDate" label="承诺交期" width="120" />
        <el-table-column label="进度" width="140">
          <template #default="{ row }">
            <el-progress :percentage="row.actualProgress || 0" :stroke-width="10" />
            <el-tag v-if="overdueDays(row)" type="danger" size="small">逾期 {{ overdueDays(row) }} 天</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="本段工钱" width="100" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">{{ label(STAGE_STATUS, row.status) }}</template>
        </el-table-column>
        <el-table-column label="托管" width="90">
          <template #default="{ row }">{{ label(ESCROW_STATUS, row.escrowStatus) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="320">
          <template #default="{ row }">
            <el-button size="small" @click="openSign(row)">签合同</el-button>
            <el-button v-if="row.status==='PENDING' && row.contractSigned" size="small" type="success" @click="doStart(row)">开工</el-button>
            <el-button v-if="row.status==='IN_PRODUCTION'" size="small" type="warning" @click="openProg(row)">上报进度</el-button>
            <el-button v-if="row.status==='IN_PRODUCTION'" size="small" type="primary" @click="doDeliver(row)">交付</el-button>
            <el-button v-if="['PASS','FAIL'].includes(row.status)" size="small" @click="openInsp(row)">质检报告</el-button>
            <el-button v-if="['PASS','FAIL'].includes(row.status) && !row.surveyed" size="small" @click="openSurvey(row)">问卷</el-button>
          </template>
        </el-table-column>
      </el-table>

    <el-dialog v-model="signOpen" title="阅读并签署与你的合同" width="520px">
      <p>订单 #{{ signOrderId }} · {{ contract.factoryName || '' }}</p>
      <p v-if="contract.attachmentId">
        合同文件
        <el-button size="small" link type="primary" @click="openFile(contract.attachmentId)">{{ contract.fileName || ('附件 #' + contract.attachmentId) }}</el-button>
      </p>
      <p v-else>买家尚未上传与你的合同</p>
      <el-checkbox v-model="read">我已阅读合同全文</el-checkbox>
      <p>手写签名</p>
      <SignPad @change="sign = $event" />
      <template #footer>
        <el-button @click="signOpen=false">取消</el-button>
        <el-button type="primary" :disabled="!read || !sign || !contract.attachmentId" @click="doSign">提交签名</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="progOpen" title="上报进度" width="480px">
      <el-form label-width="100px">
        <el-form-item label="完成件数">
          <el-input-number v-model="progForm.doneQty" :min="1" :max="progStage?.quantity || 1" />
          <span class="hint"> / {{ progStage?.quantity }}</span>
        </el-form-item>
        <el-form-item label="说明" required>
          <el-input v-model="progForm.remark" type="textarea" />
        </el-form-item>
        <el-form-item label="现场照片">
          <el-upload :auto-upload="false" :limit="1" :on-change="onProgFile">
            <el-button>选择图片</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="progOpen=false">取消</el-button>
        <el-button type="primary" @click="submitProg">提交</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="inspOpen" title="质检报告" width="480px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="结论">{{ inspReport.result || '-' }}</el-descriptions-item>
        <el-descriptions-item label="抽检数">{{ inspJson.sampleCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="不合格数">{{ inspJson.failCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="关键尺寸">{{ inspJson.keyDimensions || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ inspJson.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
    <el-dialog v-model="surveyOpen" title="阶段问卷（1～5 分）" width="480px">
      <el-form label-width="140px">
        <el-form-item label="沟通配合"><el-rate v-model="scores.q1" /></el-form-item>
        <el-form-item label="交期兑现"><el-rate v-model="scores.q2" /></el-form-item>
        <el-form-item label="质量满意度"><el-rate v-model="scores.q3" /></el-form-item>
        <el-form-item label="是否愿意再合作"><el-rate v-model="scores.q4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="surveyOpen=false">取消</el-button>
        <el-button type="primary" @click="doSurvey">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue'
import { myStages, deliver, factorySign, getContract, submitSurvey, listOrders, downloadAttachment, startStage, reportProgress, inspectionOf, uploadFile } from '../../api/order'
import SignPad from '../../components/SignPad.vue'
import { ElMessage } from 'element-plus'
import { STAGE_STATUS, ESCROW_STATUS, label } from '../../utils/labels'

const stageList = ref([])
const pendingOrders = ref([])
const signOpen = ref(false)
const signOrderId = ref(null)
const contract = ref({})
const read = ref(false)
const sign = ref('')
const surveyOpen = ref(false)
const surveyStage = ref(null)
const scores = reactive({ q1: 5, q2: 5, q3: 5, q4: 5 })
const progOpen = ref(false)
const progStage = ref(null)
const progForm = reactive({ doneQty: 1, remark: '' })
const progFile = ref(null)
const inspOpen = ref(false)
const inspReport = ref({})
const inspJson = computed(() => {
  try { return inspReport.value.reportJson ? JSON.parse(inspReport.value.reportJson) : {} } catch { return {} }
})

async function load() {
  stageList.value = await myStages()
  const orders = await listOrders()
  const pending = []
  for (const o of orders || []) {
    if (o.status === 'COMPLETED') continue
    try {
      const c = await getContract(o.id)
      if (c && c.status !== 'SIGNED') pending.push(o)
    } catch { /* 不是我的合同 */ }
  }
  pendingOrders.value = pending
}

async function openSign(row) {
  signOrderId.value = row.orderId || row.id
  read.value = false
  sign.value = ''
  contract.value = await getContract(signOrderId.value)
  signOpen.value = true
}

async function openFile(id) {
  try {
    await downloadAttachment(id)
  } catch (e) {
    ElMessage.error(e.message || '无法打开合同')
  }
}

async function doSign() {
  await factorySign(signOrderId.value, true, sign.value)
  ElMessage.success('工厂已签名')
  signOpen.value = false
  load()
}

function overdueDays(row) {
  if (!row?.promisedDate) return 0
  if (['PASS', 'FAIL', 'COMPLETED'].includes(row.status) || row.escrowStatus === 'SETTLED') return 0
  const d = Math.ceil((Date.now() - new Date(row.promisedDate).getTime()) / 86400000)
  return d > 0 ? d : 0
}
async function doStart(row) {
  await startStage(row.id)
  ElMessage.success('已开工')
  load()
}
function openProg(row) {
  progStage.value = row
  progForm.doneQty = Math.min((row.quantity || 1), Math.max(1, (row.actualProgress || 0) * (row.quantity || 1) / 100 + 1 | 0) || 1)
  progForm.remark = ''
  progFile.value = null
  progOpen.value = true
}
function onProgFile(f) { progFile.value = f.raw }
async function submitProg() {
  if (!progForm.remark.trim()) return ElMessage.warning('请填写说明')
  let attachmentId = null
  if (progFile.value) {
    const up = await uploadFile(progFile.value, 'PROGRESS')
    attachmentId = up.id
  }
  await reportProgress(progStage.value.id, { doneQty: progForm.doneQty, remark: progForm.remark.trim(), attachmentId })
  ElMessage.success('进度已上报')
  progOpen.value = false
  load()
}
async function openInsp(row) {
  inspReport.value = (await inspectionOf(row.id)) || {}
  inspOpen.value = true
}
async function doDeliver(row) {
  await deliver(row.id)
  ElMessage.success('已交付，等待质检')
  load()
}

function openSurvey(row) {
  surveyStage.value = row
  scores.q1 = scores.q2 = scores.q3 = scores.q4 = 5
  surveyOpen.value = true
}

async function doSurvey() {
  await submitSurvey(surveyStage.value.id, scores)
  ElMessage.success('问卷已提交')
  surveyOpen.value = false
  load()
}

onMounted(load)
</script>

<style scoped>
.hint { color: #909399; font-size: 12px; margin-left: 8px; }
</style>

