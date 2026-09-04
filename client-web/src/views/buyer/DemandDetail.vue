<template>
  <div v-loading="loading">
    <div class="actions">
      <el-button v-if="demand.status === 'RETURNED'" type="warning" @click="goEdit">退回修改</el-button>
      <el-button v-if="demand.status === 'PUBLISHED'" type="danger" @click="goCancel">取消并重新发布</el-button>
      <el-button v-if="demand.status === 'THINKING'" type="success" @click="decide('CONTINUE')">继续竞标</el-button>
      <el-button v-if="demand.status === 'THINKING'" type="danger" @click="decide('CANCEL')">取消需求</el-button>
      <el-button v-if="demand.status === 'BUYER_THINKING'" type="success" @click="buyerContinue">继续并交保证金</el-button>
      <el-button v-if="demand.status === 'BUYER_THINKING'" type="danger" @click="buyerCancel">取消需求（全退）</el-button>
      <el-button v-if="demand.status === 'SOLUTION_GENERATED' && hasActiveSolution" type="primary" @click="$router.push('/buyer/solutions/' + demand.id)">参考推荐并选定工厂</el-button>
      <el-button v-else-if="demand.status === 'SOLUTION_GENERATED'" disabled>等待运营审核推荐方案</el-button>
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
        <IntentionCountdown v-else-if="demand.status==='FACTORY_THINKING'" :end-at="demand.factoryThinkingEndAt" />
        <IntentionCountdown v-else-if="demand.status==='BUYER_THINKING'" :end-at="demand.buyerThinkingEndAt" />
        <IntentionCountdown v-else-if="demand.status==='LOCKING'" :end-at="demand.lockingEndAt" />
        <div v-if="demand.status==='BUYER_THINKING'" class="deposit-box">
          按各工序最低单价预估总价：<b>￥{{ money(demand.estimatedTotal) }}</b>，
          继续需冻结 5% 保证金 <b>￥{{ money(buyerDeposit) }}</b>（尾款抵扣，流单/验收剩余全退）。
        </div>
        <div style="margin-top:12px"><CoverageBars :items="coverage" /></div>
      </el-card>
      <el-descriptions :column="2" border style="margin-top:12px">
        <el-descriptions-item label="标题" :span="2">{{ demand.title }}</el-descriptions-item>
        <el-descriptions-item label="产品">{{ demand.productName }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ demand.quantity }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ fmtTime(demand.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="发布/意向开始">{{ fmtTime(demand.publishedAt) }}</el-descriptions-item>
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
            <el-descriptions-item label="AQL">{{ demand.aql || '-' }}</el-descriptions-item>
            <el-descriptions-item label="检验方式">{{ formatInspectMode(demand.inspectMode) }}</el-descriptions-item>
            <el-descriptions-item label="认证">{{ demand.certification || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="demand.minYield != null" label="最低良率">{{ demand.minYield }}</el-descriptions-item>
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
        <h4>生产进度 {{ progressPercent }}%</h4>
        <el-progress :percentage="progressPercent" style="margin-bottom:10px" />
        <PagedBox :data="stageList" :page-size="8" v-slot="{ rows }">
        <el-table :data="rows" border size="small">
          <el-table-column prop="processName" label="工序" />
          <el-table-column prop="factoryName" label="工厂" />
          <el-table-column label="承接区间" width="140">
            <template #default="{ row }">{{ (row.minQty ?? '-') }} ~ {{ (row.maxQty ?? '-') }} 件</template>
          </el-table-column>
          <el-table-column label="进度" width="160">
            <template #default="{ row }">
              <el-progress :percentage="row.actualProgress || 0" :stroke-width="10" />
            </template>
          </el-table-column>
          <el-table-column prop="promisedDate" label="承诺交期" width="120" />
          <el-table-column label="创建时间" width="160">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        </PagedBox>
      </div>

      <h4>报名工厂</h4>
      <el-empty v-if="!factories.length" description="暂无工厂报名" :image-size="60" />
      <el-table v-else :data="factories" border size="small">
        <el-table-column label="工厂" min-width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="openFactory(row)">{{ row.name }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="承接区间" width="160">
          <template #default="{ row }">{{ (row.minQty ?? '-') }} ~ {{ (row.maxQty ?? '-') }} 件</template>
        </el-table-column>
        <el-table-column prop="creditScore" label="信用分" width="80" />
        <el-table-column label="认证" width="80">
          <template #default="{ row }">{{ row.authStatus === 'APPROVED' ? '已认证' : '未认证' }}</template>
        </el-table-column>
        <el-table-column label="能力档案" min-width="180">
          <template #default="{ row }">
            日产能 {{ row.dailyCapacitySum ?? 0 }} 件/天
            <div class="hint">{{ (row.certs || []).join('、') || '未填体系认证' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="阶段" width="90">
          <template #default="{ row }">{{ row.bidStage === 'QUOTED' ? '已报价' : '已报名' }}</template>
        </el-table-column>
        <el-table-column v-if="showFactoryQuotes" label="报价（单价 / 承接区间）" min-width="280">
          <template #default="{ row }">
            <div v-if="!(row.quotes || []).length">-</div>
            <div v-for="q in (row.quotes || [])" :key="q.processNo" class="hint">
              {{ q.processName }}：¥{{ q.unitPrice ?? '-' }}/件 · {{ q.minQty ?? 1 }}~{{ q.maxQty ?? '-' }} 件 · {{ q.promisedDays ?? '-' }} 天
            </div>
          </template>
        </el-table-column>
      </el-table>

      <h4>工序</h4>
      <el-table :data="processes" border size="small">
        <el-table-column prop="processNo" label="序号" width="70" />
        <el-table-column prop="processName" label="工序" />
        <el-table-column prop="requirement" label="要求" />
      </el-table>

      <h4>附件</h4>
      <el-table :data="attachments" border size="small">
        <el-table-column prop="fileName" label="文件名" />
        <el-table-column prop="fileType" label="类型" width="80" />
        <el-table-column prop="fileSize" label="大小" width="120" />
        <el-table-column label="上传时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="viewFile(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-card v-if="confirmedPlan.groups.length" shadow="never" class="confirmed-plan">
        <template #header>
          <span>已确认方案</span>
          <el-tag type="info" size="small" style="margin-left:8px">不可修改</el-tag>
        </template>
        <p class="hint" style="margin-top:0">{{ confirmedPlan.hint }}</p>
        <div v-for="g in confirmedPlan.groups" :key="g.processNo" class="proc-block">
          <div class="proc-head">
            <span class="proc-title">{{ g.processName }} / {{ g.need }}件</span>
            <span class="alloc-ok">已分配 {{ g.allocated }} / {{ g.need }}</span>
          </div>
          <div class="line head">
            <span class="col-fac">工厂</span>
            <span class="col-range">承接区间</span>
            <span class="col-qty">分量(件)</span>
            <span class="col-amt">金额</span>
          </div>
          <div v-for="(line, i) in g.lines" :key="g.processNo + '-' + i" class="line">
            <span class="col-fac">
              <el-button v-if="line.factoryId" link type="primary" @click="openFactoryById(line.factoryId)">
                {{ line.factoryName || ('工厂-' + line.factoryId) }}
              </el-button>
              <span v-else>{{ line.factoryName || '-' }}</span>
            </span>
            <span class="col-range">{{ (line.minQty ?? '-') + ' ~ ' + (line.maxQty ?? '-') }}</span>
            <span class="col-qty">{{ line.quantity ?? '-' }}</span>
            <span class="col-amt">{{ money(lineAmount(line)) }}</span>
          </div>
        </div>
        <div class="total-bar">
          <span>总计金额</span>
          <strong>{{ money(confirmedPlan.total) }} 元</strong>
        </div>
      </el-card>
    <el-dialog v-model="fileOpen" :title="fileTitle" width="720px">
      <pre class="file-preview">{{ fileText }}</pre>
    </el-dialog>
    <el-dialog v-model="factoryOpen" title="工厂详情" width="640px" :close-on-click-modal="false">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="名称" :span="2">{{ factoryInfo.name }}</el-descriptions-item>
        <el-descriptions-item label="信用分">{{ factoryInfo.creditScore ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="认证">{{ factoryInfo.authStatus === 'APPROVED' ? '已认证' : '未认证' }}</el-descriptions-item>
        <el-descriptions-item label="质检合格率">{{ factoryInfo.inspectionPassRate != null ? factoryInfo.inspectionPassRate + '%' : '暂无记录' }}</el-descriptions-item>
        <el-descriptions-item label="已结算工单">{{ factoryInfo.settledStages ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="买家评分">{{ factoryInfo.surveyAvg ?? '暂无' }}</el-descriptions-item>
        <el-descriptions-item label="日产能合计">{{ factoryInfo.dailyCapacitySum ?? 0 }} 件/天</el-descriptions-item>
        <el-descriptions-item label="体系认证" :span="2">{{ (factoryInfo.certs || []).join('、') || '-' }}</el-descriptions-item>
        <el-descriptions-item label="企业介绍" :span="2">{{ factoryInfo.introduction || '-' }}</el-descriptions-item>
      </el-descriptions>
      <h4 style="margin:12px 0 8px">设备与状态</h4>
      <el-table :data="factoryInfo.devices || []" border size="small">
        <el-table-column prop="name" label="设备" min-width="120" />
        <el-table-column prop="processNames" label="适用工序" min-width="120" />
        <el-table-column prop="dailyCapacity" label="日产能" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="deviceStatusType(row.status)" size="small">{{ label(DEVICE_STATUS, row.status) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getDetail, getCoverage, getCancelStats, cancelPublished, decide as decideDemand, buyerDecide as buyerDecideApi, listBidFactories } from '../../api/demand'
import { listOrders, stages as listStages } from '../../api/order'
import { listByDemand } from '../../api/solution'
import { fetchAttachment, saveBlob } from '../../api/file'
import api from '../../api'
import CoverageBars from '../../components/CoverageBars.vue'
import IntentionCountdown from '../../components/IntentionCountdown.vue'
import PagedBox from '../../components/PagedBox.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DEMAND_STATUS, DEVICE_STATUS, label, fmtTime, deviceStatusType, formatInspectMode } from '../../utils/labels'

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
const solutions = ref([])
const factories = ref([])
const factoryOpen = ref(false)
const factoryInfo = ref({})
const showFactoryQuotes = computed(() => ['SOLUTION_GENERATED', 'SOLUTION_CONFIRMED', 'SOLUTION_SELECTED', 'CONTRACTED', 'IN_PRODUCTION', 'COMPLETED'].includes(demand.value.status))
const nextHint = computed(() => {
  const s = demand.value.status
  return ({
    PENDING_AUDIT: '等待运营审核',
    RETURNED: '请按退回原因改完再提交',
    PUBLISHED: '意向期报名中',
    FACTORY_THINKING: '工厂正在填报实施方案与单价',
    BUYER_THINKING: '请决定是否继续：继续需交 5% 保证金',
    THINKING: '请决定继续或取消',
    REVIEWING: '等待运营审核取消',
    LOCKING: '工厂正在锁定报价',
    SOLUTION_GENERATED: hasActiveSolution.value ? '可参考推荐方案，按各厂承接区间自行分配后确认' : '等待运营审核推荐方案',
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
const stageList = ref([])
const progressPercent = computed(() => {
  const list = stageList.value || []
  if (!list.length) return 0
  const sum = list.reduce((s, r) => s + (r.actualProgress || 0), 0)
  return Math.round(sum / list.length)
})
const buyerDeposit = computed(() => {
  const t = Number(demand.value.estimatedTotal || 0)
  return t > 0 ? (t * 0.05) : 0
})

function money(v) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '0.00'
  return n.toFixed(2)
}

function lineAmount(line) {
  const p = Number(line?.price)
  if (Number.isFinite(p)) return p
  const unit = Number(line?.unitPrice)
  const qty = Number(line?.quantity)
  if (Number.isFinite(unit) && Number.isFinite(qty)) return Math.round(unit * qty * 100) / 100
  return 0
}

function parseCombo(json) {
  try {
    const v = typeof json === 'string' ? JSON.parse(json) : json
    return Array.isArray(v) ? v : []
  } catch { return [] }
}

const confirmedPlan = computed(() => {
  const st = demand.value.status
  if (!['SOLUTION_CONFIRMED', 'SOLUTION_SELECTED', 'CONTRACTED', 'IN_PRODUCTION', 'COMPLETED'].includes(st)) {
    return { groups: [], total: 0, hint: '' }
  }
  const sols = solutions.value || []
  const picked = sols.find(s => Number(s.isFinal) === 1)
    || sols.find(s => Number(s.final) === 1)
    || sols.find(s => s.source === 'FINAL')
    || sols.find(s => (s.type || '') === 'CUSTOM')
  if (!picked) return { groups: [], total: 0, hint: '' }
  const items = parseCombo(picked.finalComboJson)
  const map = new Map()
  for (const p of processes.value || []) {
    map.set(p.processNo, {
      processNo: p.processNo,
      processName: p.processName || ('工序' + p.processNo),
      need: Number(p.quantity) || 0,
      allocated: 0,
      lines: [],
    })
  }
  for (const it of items) {
    const no = it.processNo
    if (!map.has(no)) {
      map.set(no, {
        processNo: no,
        processName: it.processName || ('工序' + no),
        need: 0,
        allocated: 0,
        lines: [],
      })
    }
    const g = map.get(no)
    g.lines.push(it)
    g.allocated += Number(it.quantity) || 0
    if (!g.need) g.need = g.allocated
  }
  const groups = [...map.values()].filter(g => g.lines.length)
  const total = items.reduce((s, it) => s + lineAmount(it), 0)
  const kind = (picked.type || '').startsWith('AI') ? '已按推荐方案确认' : '已按自选分配确认'
  return { groups, total, hint: kind + '，等待运营派单后进入履约。确认后不可再改。' }
})

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

async function buyerContinue() {
  await ElMessageBox.confirm(
    `继续将冻结保证金 ￥${money(buyerDeposit.value)}（预估总价 ￥${money(demand.value.estimatedTotal)} 的 5%），` +
    '该保证金在尾款支付时抵扣，流单或验收后剩余全额退回。确定继续？',
    '继续并交保证金', { type: 'warning', confirmButtonText: '确认冻结并继续', cancelButtonText: '再想想' },
  )
  await buyerDecideApi(demand.value.id, 'CONTINUE')
  ElMessage.success('保证金已冻结，AI 正在生成方案')
  load()
}

async function buyerCancel() {
  const box = await ElMessageBox.prompt('取消后所有工厂的意向金/保证金全额退回，需求作废。请填写取消理由。', '取消需求', {
    confirmButtonText: '确认取消',
    cancelButtonText: '再想想',
    inputPlaceholder: '必填',
    inputValidator: (v) => !!String(v || '').trim() || '请填写理由',
  })
  await buyerDecideApi(demand.value.id, 'CANCEL', String(box.value).trim())
  ElMessage.success('已取消，相关资金已退回')
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
      solutions.value = sols || []
      hasActiveSolution.value = (sols || []).length > 0
    } catch {
      solutions.value = []
      hasActiveSolution.value = false
    }
    try {
      if (['CONTRACTED', 'IN_PRODUCTION', 'COMPLETED'].includes(demand.value.status)) {
        const orders = await listOrders()
        const order = (orders || []).find(o => String(o.demandId) === String(route.params.id))
        stageList.value = order ? await listStages(order.id) : []
      } else {
        stageList.value = []
      }
    } catch { stageList.value = [] }
    try { factories.value = await listBidFactories(route.params.id) } catch { factories.value = [] }
  } finally {
    loading.value = false
  }
}

function openFactory(row) {
  factoryInfo.value = row || {}
  factoryOpen.value = true
}

async function openFactoryById(id) {
  const hit = (factories.value || []).find(f => f.id === id)
  if (hit) {
    factoryInfo.value = hit
    factoryOpen.value = true
    return
  }
  factoryInfo.value = await api.get(`/enterprise/${id}/public-profile`)
  factoryOpen.value = true
}

onMounted(load)
</script>

<style scoped>
.now { margin-bottom: 8px; }
.deposit-box { margin-top: 10px; padding: 10px 12px; background: #fdf6ec; border-radius: 6px; color: #b88230; font-size: 13px; }
.now-title { font-weight: 600; margin-bottom: 8px; }
.actions { margin-bottom: 12px; }
h4 { margin: 16px 0 8px; }
.file-preview { white-space: pre-wrap; word-break: break-word; margin: 0; font-size: 13px; line-height: 1.6; }
.hint { color: #909399; font-size: 12px; }
.confirmed-plan { margin-top: 16px; }
.proc-block { margin-bottom: 16px; padding-bottom: 4px; border-bottom: 1px solid #ebeef5; }
.proc-block:last-of-type { border-bottom: none; }
.proc-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 6px; }
.proc-title { font-weight: 600; font-size: 13px; color: #303133; }
.alloc-ok { font-size: 12px; color: #67c23a; }
.line { display: flex; align-items: center; gap: 8px; flex-wrap: nowrap; margin-bottom: 8px; }
.line.head { color: #909399; font-size: 12px; margin-bottom: 4px; }
.col-fac { flex: 1 1 auto; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.col-range { flex: 0 0 100px; width: 100px; color: #909399; font-size: 12px; white-space: nowrap; }
.col-qty { flex: 0 0 80px; width: 80px; text-align: right; }
.col-amt { flex: 0 0 100px; width: 100px; text-align: right; white-space: nowrap; font-variant-numeric: tabular-nums; }
.total-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin: 8px 0 0;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 13px;
  color: #606266;
}
.total-bar strong { font-size: 16px; color: #303133; font-variant-numeric: tabular-nums; }
</style>
