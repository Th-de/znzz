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

      <el-card header="按厂承包（方案里分给各厂的工序）" style="margin-top:16px">
        <PagedBox :data="order.combo || []" :page-size="8" v-slot="{ rows }">
        <el-table :data="rows" border>
          <el-table-column prop="factoryName" label="工厂" />
          <el-table-column prop="processName" label="工序" />
          <el-table-column prop="quantity" label="数量" width="90" />
          <el-table-column prop="price" label="价格" width="100" />
          <el-table-column prop="days" label="工期(天)" width="90" />
        </el-table>
        </PagedBox>
      </el-card>

      <el-card header="按厂合同（正文你们自己拟定，一厂一份；平台只确认签过）" style="margin-top:16px">
        <PagedBox :data="contractList" :page-size="8" v-slot="{ rows }">
        <el-table :data="rows" border>
          <el-table-column prop="factoryName" label="工厂" />
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
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button v-if="row.status!=='SIGNED'" size="small" type="primary" @click="openUpload(row)">上传并签名</el-button>
            </template>
          </el-table-column>
        </el-table>
        </PagedBox>
      </el-card>

      <el-card header="工单与阶段款" style="margin-top:16px">
        <PagedBox :data="stageList" :page-size="8" v-slot="{ rows }">
        <el-table :data="rows" border>
          <el-table-column prop="factoryName" label="工厂" min-width="140" />
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
          <el-table-column label="质检" width="90">
            <template #default="{ row }">{{ label(STAGE_STATUS, row.status) }}</template>
          </el-table-column>
          <el-table-column label="托管" width="110">
            <template #default="{ row }">{{ escrowText(row.escrowStatus) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="260">
            <template #default="{ row }">
              <el-button size="small" @click="openProgress(row)">进度记录</el-button>
              <el-button v-if="['PASS','FAIL'].includes(row.status)" size="small" @click="openInsp(row)">质检报告</el-button>
              <el-button v-if="row.status==='PASS' && (row.escrowStatus==='NONE' || row.escrowStatus==='PENDING_PAY')" size="small" type="primary" @click="pay(row)">{{ row.escrowStatus==='PENDING_PAY' ? '继续支付' : '支付本阶段' }}</el-button>
              <el-button v-if="['PASS','FAIL'].includes(row.status) && !row.surveyed" size="small" @click="openSurvey(row)">问卷</el-button>
            </template>
          </el-table-column>
        </el-table>
        </PagedBox>
        <el-button
          v-if="order.status==='IN_PRODUCTION'"
          type="success"
          style="margin-top:12px"
          @click="doAccept"
        >完工确认</el-button>
      </el-card>

    <el-dialog v-model="upOpen" :title="'上传合同 · ' + (current?.factoryName || '')" width="520px">
      <p>平台不拟定合同内容。请上传你们与该厂约定的文件，并阅读后手写签名。</p>
      <el-upload :auto-upload="false" :limit="1" :on-change="onFile">
        <el-button>选择文件</el-button>
      </el-upload>
      <el-button type="primary" style="margin:8px 0" :disabled="!file" @click="doUpload">上传该厂合同</el-button>
      <el-checkbox v-model="read">我已阅读该份合同全文</el-checkbox>
      <p>手写签名</p>
      <SignPad @change="sign = $event" />
      <template #footer>
        <el-button @click="upOpen=false">取消</el-button>
        <el-button type="success" :disabled="!read || !sign || !current?.attachmentId" @click="doSign">提交签名</el-button>
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
        <el-descriptions-item label="抽检数">{{ inspJson.sampleCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="不合格数">{{ inspJson.failCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="关键尺寸">{{ inspJson.keyDimensions || '-' }}</el-descriptions-item>
        <el-descriptions-item label="是否满足">{{ inspJson.meetsRequirement ? '是' : '否' }}</el-descriptions-item>
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
import { useRoute } from 'vue-router'
import { getOrder, listContracts, uploadContract, buyerSign, stages, payStage, accept, submitSurvey, uploadFile, downloadAttachment, progressLog, inspectionOf } from '../../api/order'
import SignPad from '../../components/SignPad.vue'
import PagedBox from '../../components/PagedBox.vue'
import { ElMessage } from 'element-plus'
import { ORDER_STATUS, STAGE_STATUS, ESCROW_STATUS, label, fmtTime } from '../../utils/labels'

const route = useRoute()
const orderId = route.params.id
const order = ref({})
const defaultSteps = ['选定方案', '合同', '开工', '质检托管', '完工结算']
const contractList = ref([])
const stageList = ref([])
const file = ref(null)
const read = ref(false)
const sign = ref('')
const upOpen = ref(false)
const current = ref(null)
const surveyOpen = ref(false)
const surveyStage = ref(null)
const scores = reactive({ q1: 5, q2: 5, q3: 5, q4: 5 })
const progOpen = ref(false)
const progLogs = ref([])
const progStage = ref(null)
const inspOpen = ref(false)
const inspReport = ref({})
const inspJson = computed(() => {
  try { return inspReport.value.reportJson ? JSON.parse(inspReport.value.reportJson) : {} } catch { return {} }
})
const orderHint = computed(() => {
  const need = (contractList.value || []).filter(c => c.status !== 'SIGNED').length
  if (need) return '有 ' + need + ' 份合同待你上传或签名'
  const pay = (stageList.value || []).filter(s => s.status === 'PASS' && (s.escrowStatus === 'NONE' || s.escrowStatus === 'PENDING_PAY'))
  if (pay.length) return '有 ' + pay.length + ' 笔阶段款待支付'
  if (order.value.status === 'IN_PRODUCTION') return '生产进行中，可查看各工序进度'
  if (order.value.status === 'COMPLETED') return '订单已完成'
  return '按步骤完成合同、质检与托管即可'
})

function contractStatus(s) {
  return ({ DRAFT: '待上传/签署', PENDING_REVIEW: '待平台确认', SIGNED: '已确认' })[s] || s || '-'
}
function escrowText(s) {
  return label(ESCROW_STATUS, s)
}
function overdueDays(row) {
  if (!row?.promisedDate) return 0
  if (['PASS', 'FAIL', 'COMPLETED'].includes(row.status) || row.escrowStatus === 'SETTLED') return 0
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

function openUpload(row) {
  current.value = row
  file.value = null
  read.value = false
  sign.value = ''
  upOpen.value = true
}

function onFile(f) { file.value = f.raw }

async function doUpload() {
  const up = await uploadFile(file.value, 'CONTRACT')
  await uploadContract(orderId, up.id, current.value.tenantId)
  ElMessage.success('该厂合同已上传')
  await load()
  current.value = contractList.value.find(c => c.tenantId === current.value.tenantId)
}

async function doSign() {
  await buyerSign(orderId, true, sign.value, current.value.tenantId)
  ElMessage.success('买家已签名，等待该厂签署后由平台确认')
  upOpen.value = false
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

async function doAccept() {
  await accept(orderId)
  ElMessage.success('完工确认完成，已结算并扣佣金')
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
.hint { color: #909399; font-size: 12px; }
</style>
