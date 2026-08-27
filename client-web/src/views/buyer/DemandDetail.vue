<template>
  <div v-loading="loading">
    <div class="actions">
      <el-button v-if="demand.status === 'RETURNED'" type="warning" @click="goEdit">退回修改</el-button>
      <el-button v-if="demand.status === 'PUBLISHED'" type="danger" @click="goCancel">取消并重新发布</el-button>
      <el-button v-if="demand.status === 'THINKING'" type="success" @click="decide('CONTINUE')">继续竞标</el-button>
      <el-button v-if="demand.status === 'THINKING'" type="danger" @click="decide('CANCEL')">取消需求</el-button>
      <el-button v-if="demand.status === 'SOLUTION_GENERATED' && hasActiveSolution" type="primary" @click="$router.push('/buyer/solutions/' + demand.id)">看方案</el-button>
      <el-button v-else-if="demand.status === 'SOLUTION_GENERATED'" disabled>等待运营下发方案</el-button>
      <el-button v-if="demand.status === 'SOLUTION_CONFIRMED'" disabled>等待运营派单</el-button>
      <el-button v-if="demand.status === 'SOLUTION_SELECTED' || demand.status === 'CONTRACTED' || demand.status === 'IN_PRODUCTION' || demand.status === 'COMPLETED'" @click="$router.push('/buyer/orders')">去订单</el-button>
    </div>
      <el-alert
        v-if="demand.status === 'RETURNED'"
        type="warning"
        :closable="false"
        :title="'已退回修改：' + (demand.returnReason || '未填写原因') + '。改完提交后需运营审核通过，工厂才能看到。'"
        style="margin-bottom:12px"
      />
      <el-alert
        v-if="demand.status === 'PENDING_AUDIT'"
        type="info"
        :closable="false"
        title="已提交申请发布，等待运营审核。通过后才进意向期，工厂才能看到。"
        style="margin-bottom:12px"
      />
      <el-alert
        v-if="demand.status === 'CANCELLED' && demand.cancelReason"
        type="info"
        :closable="false"
        :title="'已取消：' + demand.cancelReason"
        style="margin-bottom:12px"
      />
      <el-card shadow="never" class="now">
        <div class="now-title">{{ label(DEMAND_STATUS, demand.status) }} · {{ nextHint }}</div>
        <IntentionCountdown v-if="demand.status==='PUBLISHED'" :end-at="demand.intentionEndAt" />
        <IntentionCountdown v-else-if="demand.status==='LOCKING'" :end-at="demand.lockingEndAt" />
        <div style="margin-top:12px"><CoverageBars :items="coverage" /></div>
      </el-card>
      <el-descriptions :column="2" border style="margin-top:12px">
        <el-descriptions-item label="标题" :span="2">{{ demand.title }}</el-descriptions-item>
        <el-descriptions-item label="产品">{{ demand.productName }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ demand.quantity }}</el-descriptions-item>
        <el-descriptions-item label="硬交期">{{ demand.deadlineHard }}</el-descriptions-item>
        <el-descriptions-item label="弹性交期">{{ demand.deadlineFlexible || '-' }}</el-descriptions-item>
        <el-descriptions-item label="交付地址" :span="2">{{ demand.deliveryAddress }}</el-descriptions-item>
        <el-descriptions-item label="来源需求">{{ demand.sourceDemandId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="取消原因">{{ demand.cancelReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ demand.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-collapse style="margin:12px 0">
        <el-collapse-item title="技术要求（材料/公差/热处理等）" name="tech">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="类别">{{ demand.category || '-' }}</el-descriptions-item>
            <el-descriptions-item label="图号/版本">{{ demand.partRevision || '-' }}</el-descriptions-item>
            <el-descriptions-item label="材料">{{ demand.material }}</el-descriptions-item>
            <el-descriptions-item label="一般公差">{{ demand.generalTolerance || '-' }}</el-descriptions-item>
            <el-descriptions-item label="关键公差">{{ demand.tolerance || '-' }}</el-descriptions-item>
            <el-descriptions-item label="粗糙度 Ra">{{ extra.roughness || '-' }}</el-descriptions-item>
            <el-descriptions-item label="表面处理">{{ demand.surfaceTreatment || '-' }}</el-descriptions-item>
            <el-descriptions-item label="热处理">{{ extra.heatTreatment || '-' }}</el-descriptions-item>
            <el-descriptions-item label="年用量">{{ extra.annualQty ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="AQL">{{ demand.aql }}</el-descriptions-item>
            <el-descriptions-item label="检验方式">{{ demand.inspectMode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="认证">{{ demand.certification || '-' }}</el-descriptions-item>
            <el-descriptions-item label="最低良率">{{ demand.minYield }}</el-descriptions-item>
            <el-descriptions-item label="最低信用分">{{ demand.minCreditScore }}</el-descriptions-item>
            <el-descriptions-item label="包装">{{ demand.packaging || '-' }}</el-descriptions-item>
            <el-descriptions-item label="意向天数">{{ demand.intentionDays }}</el-descriptions-item>
          </el-descriptions>
        </el-collapse-item>
      </el-collapse>

      <el-alert
        v-if="cancelStats.warn"
        type="warning"
        :closable="false"
        :title="'近 90 天已取消 ' + cancelStats.last90Days + ' 次'"
        style="margin:12px 0"
      />

      <div v-if="['CONTRACTED','IN_PRODUCTION','COMPLETED'].includes(demand.status)" style="margin-top:16px">
        <h4>生产进度 {{ pano.progressPercent ?? 0 }}%</h4>
        <el-progress :percentage="pano.progressPercent || 0" style="margin-bottom:10px" />
        <el-table :data="pano.stages || []" border size="small">
          <el-table-column prop="processName" label="工序" />
          <el-table-column prop="factoryName" label="工厂" />
          <el-table-column label="完成" width="120">
            <template #default="{ row }">{{ row.doneQty || 0 }} / {{ row.quantity || 0 }}</template>
          </el-table-column>
          <el-table-column label="进度" width="160">
            <template #default="{ row }">
              <el-progress :percentage="row.progress || 0" :stroke-width="10" />
            </template>
          </el-table-column>
          <el-table-column prop="promisedDate" label="承诺交期" width="120" />
        </el-table>
      </div>

      <h4>工序</h4>
      <el-table :data="processes" border size="small">
        <el-table-column prop="processNo" label="序号" width="70" />
        <el-table-column prop="processName" label="工序" />
        <el-table-column prop="quantity" label="数量" width="100" />
        <el-table-column prop="requirement" label="要求" />
      </el-table>

      <h4>附件</h4>
      <el-table :data="attachments" border size="small">
        <el-table-column prop="fileName" label="文件名" />
        <el-table-column prop="fileType" label="类型" width="80" />
        <el-table-column prop="fileSize" label="大小" width="120" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="viewFile(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    <el-dialog v-model="fileOpen" :title="fileTitle" width="720px">
      <pre class="file-preview">{{ fileText }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getDetail, getCoverage, getCancelStats, cancelPublished, decide as decideDemand, getPanorama } from '../../api/demand'
import { listByDemand } from '../../api/solution'
import { fetchAttachment, saveBlob } from '../../api/file'
import CoverageBars from '../../components/CoverageBars.vue'
import IntentionCountdown from '../../components/IntentionCountdown.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DEMAND_STATUS, label } from '../../utils/labels'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const demand = ref({})
const processes = ref([])
const attachments = ref([])
const coverage = ref([])
const cancelStats = ref({ last90Days: 0, warn: false })
const fileOpen = ref(false)
const fileTitle = ref('')
const fileText = ref('')
const hasActiveSolution = ref(false)
const nextHint = computed(() => {
  const s = demand.value.status
  return ({
    PENDING_AUDIT: '等待运营审核',
    RETURNED: '请按退回原因改完再提交',
    PUBLISHED: '意向期报名中',
    THINKING: '请决定继续或取消',
    REVIEWING: '等待运营审核取消',
    LOCKING: '工厂正在锁定报价',
    SOLUTION_GENERATED: hasActiveSolution.value ? '可以去选方案' : '等待运营下发方案',
    SOLUTION_CONFIRMED: '方案已确认，等待运营派单',
    SOLUTION_SELECTED: '请按厂上传并签署合同',
    CONTRACTED: '等待工厂开工',
    IN_PRODUCTION: '履约进行中，可在订单页看进度',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    FLOW_FAILED: '已流拍',
  })[s] || '查看详情'
})

const extra = computed(() => parseJson(demand.value.extraJson))
const pano = ref({})

function parseJson(raw) {
  try { return raw ? JSON.parse(raw) : {} } catch { return {} }
}

function goEdit() {
  router.push('/buyer/publish?id=' + demand.value.id)
}

async function decide(action) {
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
  await decideDemand(demand.value.id, action, reason)
  ElMessage.success('操作成功')
  load()
}

async function goCancel() {
  const { value } = await ElMessageBox.prompt('已发布需求不能改内容。取消后发新单，请填写原因。', '取消并重新发布', {
    confirmButtonText: '取消并去发布',
    cancelButtonText: '再想想',
    inputPlaceholder: '必填',
    inputValidator: (v) => !!String(v || '').trim() || '请填写原因',
  })
  await cancelPublished(demand.value.id, String(value).trim())
  ElMessage.success('已取消，请发布新需求')
  router.push('/buyer/publish?from=' + demand.value.id)
}

function isTextFile(row, fileName) {
  const t = (row.fileType || '').toLowerCase()
  const n = (fileName || row.fileName || '').toLowerCase()
  return t === 'md' || n.endsWith('.md') || t === 'txt' || n.endsWith('.txt')
}
async function viewFile(row) {
  try {
    const { blob, fileName } = await fetchAttachment(row.id)
    if (isTextFile(row, fileName)) {
      fileTitle.value = fileName || row.fileName
      fileText.value = await blob.text()
      fileOpen.value = true
    } else {
      saveBlob(blob, fileName || row.fileName)
    }
  } catch (e) {
    ElMessage.error(e.message || '无法打开文件')
  }
}

async function load() {
  loading.value = true
  try {
    const view = await getDetail(route.params.id)
    demand.value = view.demand || {}
    processes.value = view.processes || []
    attachments.value = view.attachments || []
    const cov = await getCoverage(route.params.id)
    coverage.value = cov.processes || []
    cancelStats.value = await getCancelStats()
    try {
      const sols = await listByDemand(route.params.id)
      hasActiveSolution.value = (sols || []).length > 0
    } catch { hasActiveSolution.value = false }
    try {
      if (['CONTRACTED', 'IN_PRODUCTION', 'COMPLETED'].includes(demand.value.status)) {
        pano.value = await getPanorama(route.params.id)
      } else {
        pano.value = {}
      }
    } catch { pano.value = {} }
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.now { margin-bottom: 8px; }
.now-title { font-weight: 600; margin-bottom: 8px; }
.actions { margin-bottom: 12px; }
h4 { margin: 16px 0 8px; }
.file-preview { white-space: pre-wrap; word-break: break-word; margin: 0; font-size: 13px; line-height: 1.6; }
</style>
