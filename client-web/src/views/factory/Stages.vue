<template>
  <div>
      <el-alert type="info" :closable="false" title="一行对应一个需求。点开后是各期订单；到开始时间后状态变为进行中，才能上报进度和交付。未到开始时间为待开启。" style="margin-bottom:12px" />
      <h4>我的工单</h4>
      <PagedBox :data="jobList" v-slot="{ rows }">
      <el-table ref="tableRef" :data="rows" border row-key="orderId" @row-click="onRowClick">
        <el-table-column type="expand">
          <template #default="{ row }">
            <el-table :data="row.periods || []" border size="small" class="period-table">
              <el-table-column label="期" width="90">
                <template #default="{ row: p }">{{ p.periodLabel || ('第' + (p.periodNo || '-') + '期') }}</template>
              </el-table-column>
              <el-table-column label="约定数量" width="90">
                <template #default="{ row: p }">{{ p.quantity ?? '-' }}</template>
              </el-table-column>
              <el-table-column label="实交数量" width="90">
                <template #default="{ row: p }">{{ p.deliveredQty ?? '-' }}</template>
              </el-table-column>
              <el-table-column label="开始时间" width="160">
                <template #default="{ row: p }">{{ fmtTime(p.periodStart) }}</template>
              </el-table-column>
              <el-table-column label="截止时间" width="160">
                <template #default="{ row: p }">{{ fmtTime(p.periodEnd || p.promisedDate) }}</template>
              </el-table-column>
              <el-table-column label="本期工费" width="100">
                <template #default="{ row: p }">{{ p.amount ?? '-' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="140">
                <template #default="{ row: p }">
                  <span
                    v-if="isReworking(p)"
                    class="rework-status"
                    @click.stop="openReworkReason(p)"
                  >返工生产中</span>
                  <span v-else>{{ periodStatusText(p) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="托管" width="100">
                <template #default="{ row: p }">{{ label(ESCROW_STATUS, p.escrowStatus) }}</template>
              </el-table-column>
              <el-table-column label="操作" min-width="280">
                <template #default="{ row: p }">
                  <el-button v-if="canProduce(p)" size="small" type="warning" @click.stop="openProg(p)">上报进度</el-button>
                  <el-button v-if="canProduce(p)" size="small" type="primary" @click.stop="openDeliver(p)">交付</el-button>
                  <el-button v-if="p.status==='PENDING_INSPECT_PAY' && p.inspectFeePayer==='FACTORY'" size="small" type="warning" @click.stop="payFee(p)">支付质检费 ¥{{ p.inspectFeeAmount }}</el-button>
                  <el-button v-if="['PASS','FAIL','CLOSED'].includes(p.status)" size="small" @click.stop="openInsp(p)">质检报告</el-button>
                  <el-button v-if="p.status==='PASS' && !p.surveyed" size="small" @click.stop="openSurvey(p)">评价</el-button>
                  <span v-if="p.status==='WAITING_OPEN' && p.contractSigned" class="hint">未到开始时间</span>
                  <span v-if="!p.contractSigned" class="hint">请到「我的报名」签署合同</span>
                </template>
              </el-table-column>
            </el-table>
          </template>
        </el-table-column>
        <el-table-column label="需求ID" width="90">
          <template #default="{ row }">{{ row.demandId ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="需求名称" min-width="160">
          <template #default="{ row }">{{ row.demandTitle || '-' }}</template>
        </el-table-column>
        <el-table-column label="需求详情" min-width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">查看</el-button>
            <span class="hint">{{ row.detail || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="工期进度" width="160">
          <template #default="{ row }">
            <el-progress :percentage="row.progress || 0" :stroke-width="10" />
          </template>
        </el-table-column>
        <el-table-column label="总计工费" width="110">
          <template #default="{ row }">{{ row.totalAmount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">{{ jobStatusText(row) }}</template>
        </el-table-column>
      </el-table>
      </PagedBox>

    <el-dialog v-model="detailOpen" :title="detailJob?.demandTitle || '需求详情'" width="520px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="需求ID">{{ detailJob?.demandId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="需求名称">{{ detailJob?.demandTitle || '-' }}</el-descriptions-item>
        <el-descriptions-item label="产品">{{ detailJob?.productName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="摘要">{{ detailJob?.detail || '-' }}</el-descriptions-item>
        <el-descriptions-item label="总计工费">{{ detailJob?.totalAmount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="期数">{{ (detailJob?.periods || []).length }}</el-descriptions-item>
      </el-descriptions>
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
    <el-dialog v-model="delOpen" title="确认交付" width="420px" :close-on-click-modal="false">
      <p>约定 {{ delStage?.quantity }} 件。请填写本次实交件数，不足约定将按数量不达标质检。</p>
      <el-form label-width="100px" style="margin-top:12px">
        <el-form-item label="实交件数">
          <el-input-number v-model="delQty" :min="1" :max="delStage?.quantity || 1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="delOpen=false">取消</el-button>
        <el-button type="primary" @click="submitDeliver">确认交付</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="reworkOpen" title="返工原因" width="480px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="本期">{{ reworkStage?.periodLabel || reworkStage?.processName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="返工截止">{{ fmtTime(reworkStage?.reworkDeadlineAt) }}</el-descriptions-item>
        <el-descriptions-item label="返工原因">{{ reworkReasonText }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="reworkOpen=false">关闭</el-button>
      </template>
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
import { computed, reactive, ref, onMounted } from 'vue'
import { myDemandJobs, deliver, submitSurvey, reportProgress, inspectionOf, uploadFile, payInspectFee } from '../../api/order'
import PagedBox from '../../components/PagedBox.vue'
import { ElMessage } from 'element-plus'
import { STAGE_STATUS, ESCROW_STATUS, label, fmtTime } from '../../utils/labels'

const jobList = ref([])
const tableRef = ref()
const surveyOpen = ref(false)
const surveyStage = ref(null)
const overall = ref(5)
const progOpen = ref(false)
const progStage = ref(null)
const progForm = reactive({ doneQty: 1, remark: '' })
const progFile = ref(null)
const inspOpen = ref(false)
const inspReport = ref({})
const delOpen = ref(false)
const delStage = ref(null)
const delQty = ref(1)
const reworkOpen = ref(false)
const reworkStage = ref(null)
const reworkReasonText = ref('')
const detailOpen = ref(false)
const detailJob = ref(null)
const inspJson = computed(() => {
  try { return inspReport.value.reportJson ? JSON.parse(inspReport.value.reportJson) : {} } catch { return {} }
})

async function load() {
  jobList.value = await myDemandJobs()
}

function onRowClick(row, column) {
  if (column?.type === 'expand') return
  tableRef.value?.toggleRowExpansion?.(row)
}

function jobStatusText(row) {
  if (!row.contractSigned) return '待签约'
  return label(STAGE_STATUS, row.status)
}

function periodStatusText(p) {
  if (!p.contractSigned && (p.status === 'PENDING' || p.status === 'WAITING_OPEN')) return '待签约'
  if (p.status === 'IN_PRODUCTION') return '进行中'
  if (p.status === 'WAITING_OPEN') return '待开启'
  return label(STAGE_STATUS, p.status)
}

function canProduce(p) {
  return p.contractSigned && p.windowOpen && p.status === 'IN_PRODUCTION' && p.status !== 'CANCELLED'
}

function isReworking(row) {
  return row?.status === 'IN_PRODUCTION' && Number(row.reworkCount) > 0
}

function openDetail(row) {
  detailJob.value = row
  detailOpen.value = true
}

function reasonFromInspect(ins, row) {
  if (row?.reworkReason) return row.reworkReason
  let r = {}
  try { r = ins?.reportJson ? JSON.parse(ins.reportJson) : {} } catch { r = {} }
  const parts = []
  if (r.quantityOk === false) parts.push('数量不达标')
  const dc = Number(r.criticalFailCount) || 0
  const dg = Number(r.generalFailCount) || 0
  if (dc > 0) parts.push('关键公差不合格 ' + dc + ' 件')
  if (r.toleranceOk === false && dc === 0) {
    if (r.aqlAc != null) parts.push('一般缺陷超过 Ac=' + r.aqlAc)
    else parts.push('抽检良率低于最低良率')
  }
  else if (dg > 0) parts.push('一般公差不合格 ' + dg + ' 件')
  if (r.actualYield != null && r.actualYield !== '') parts.push('抽检良率 ' + r.actualYield)
  if (r.remark) parts.push('质检备注：' + r.remark)
  if (r.keyDimensions) parts.push('关键尺寸：' + r.keyDimensions)
  return parts.length ? parts.join('；') : '买家要求返工'
}

async function openReworkReason(row) {
  reworkStage.value = row
  reworkReasonText.value = row.reworkReason || '加载中…'
  reworkOpen.value = true
  if (row.reworkReason) return
  try {
    const ins = await inspectionOf(row.id)
    reworkReasonText.value = reasonFromInspect(ins, row)
  } catch {
    reworkReasonText.value = '买家要求返工'
  }
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
function openDeliver(row) {
  delStage.value = row
  delQty.value = row.deliveredQty || 1
  delOpen.value = true
}
async function submitDeliver() {
  if (!delQty.value || delQty.value < 1) return ElMessage.warning('请填写实交件数')
  await deliver(delStage.value.id, { deliveredQty: delQty.value })
  ElMessage.success('已交付，请等待对应方支付质检费后再质检')
  delOpen.value = false
  load()
}

async function payFee(row) {
  await payInspectFee(row.id)
  ElMessage.success('质检费已支付，等待质检员检验')
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
.hint { color: #909399; font-size: 12px; margin-left: 8px; }
.period-table { margin: 8px 12px 12px 48px; }
.rework-status {
  color: #f56c6c;
  cursor: pointer;
  font-weight: 600;
}
.rework-status:hover { text-decoration: underline; }
</style>
