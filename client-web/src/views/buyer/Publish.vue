<template>
  <div class="publish-page">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px" class="publish-form" @submit.prevent>
        <el-card shadow="never" class="block">
          <template #header>基础</template>
          <el-form-item label="需求标题" prop="title"><el-input v-model="form.title" /></el-form-item>
          <el-form-item label="产品名称" prop="productName"><el-input v-model="form.productName" /></el-form-item>
          <el-form-item label="产品类别" prop="category">
            <el-select v-model="form.category" style="width:100%">
              <el-option label="机加" value="机加" />
              <el-option label="齿轮" value="齿轮" />
              <el-option label="注塑" value="注塑" />
              <el-option label="钣金" value="钣金" />
              <el-option label="其他" value="其他" />
            </el-select>
          </el-form-item>
          <el-form-item label="图号/版本" prop="partRevision"><el-input v-model="form.partRevision" placeholder="如 GEAR-001 / A" /></el-form-item>
          <el-form-item label="数量" prop="quantity"><el-input-number v-model="form.quantity" :min="1" /></el-form-item>
        </el-card>

        <el-card shadow="never" class="block">
          <template #header>技术规格</template>
          <el-form-item label="材料牌号" prop="material">
            <el-input v-model="form.material" placeholder="如 20CrMnTi 或 6061-T6，不要只写钢材" />
          </el-form-item>
          <el-form-item label="一般公差" prop="generalTolerance">
            <el-select v-model="form.generalTolerance" style="width:100%">
              <el-option label="ISO 2768-m" value="ISO 2768-m" />
              <el-option label="ISO 2768-f" value="ISO 2768-f" />
              <el-option label="按图纸" value="按图纸" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键公差" prop="tolerance"><el-input v-model="form.tolerance" placeholder="如配合孔 ±0.01mm" /></el-form-item>
          <el-form-item label="粗糙度 Ra" prop="roughness">
            <el-select v-model="form.roughness" style="width:100%">
              <el-option label="Ra 3.2" value="3.2" />
              <el-option label="Ra 1.6" value="1.6" />
              <el-option label="Ra 0.8" value="0.8" />
            </el-select>
          </el-form-item>
          <el-form-item label="表面处理" prop="surfaceTreatment"><el-input v-model="form.surfaceTreatment" /></el-form-item>
          <el-form-item label="热处理" prop="heatTreatment"><el-input v-model="form.heatTreatment" /></el-form-item>
        </el-card>

        <el-card shadow="never" class="block">
          <template #header>
            <div class="card-head">
              <span>质量门槛</span>
              <InspectDeliveryRules />
            </div>
          </template>
          <el-form-item label="检验方式" prop="inspectMode">
            <el-radio-group v-model="form.inspectMode" class="inspect-modes" @change="onInspectMode">
              <el-radio value="AQL">AQL 抽样（{{ INSPECT_UNIT.AQL }} 元/件，按本批实交）</el-radio>
              <el-radio value="FULL">全检（{{ INSPECT_UNIT.FULL }} 元/件，按本批实交）</el-radio>
            </el-radio-group>
            <div class="hint" style="margin-left:0">二选一。首件由工厂内部完成，平台只做交货后的抽检或全检。费用按平台标价 × 实交件数，发布时不必填单价。</div>
          </el-form-item>
          <el-form-item v-if="needAql" label="AQL" prop="aql">
            <el-select v-model="form.aql" style="width:100%">
              <el-option label="0.65" value="0.65" />
              <el-option label="1.0" value="1.0" />
              <el-option label="1.5" value="1.5" />
              <el-option label="2.5" value="2.5" />
            </el-select>
            <div class="hint" style="margin-left:0">按 GB/T 2828.1 水平 II 一次正常抽样查 n 与 Ac/Re。选了 AQL 不必填最低良率。</div>
          </el-form-item>
          <el-form-item v-if="needFull" label="最低良率" prop="minYield">
            <el-input-number v-model="form.minYield" :min="0" :max="1" :step="0.01" />
            <div class="hint" style="margin-left:0">仅全检需要。无关键超差且良率不低于该值才算公差合格。</div>
          </el-form-item>
          <el-form-item label="认证要求" prop="certList">
            <el-select v-model="form.certList" multiple filterable allow-create default-first-option
                       placeholder="下拉选平台常用项，也可输入后回车自定义" style="width:100%">
              <el-option v-for="c in CERT_OPTIONS" :key="c" :label="c" :value="c" />
            </el-select>
            <div class="hint" style="margin-left:0">ISO9001 / IATF16949 / CE 是常用项，不是平台限定。可输入 AS9100、ISO13485 等后回车添加。</div>
          </el-form-item>
          <el-form-item label="最低信用分"><el-input-number v-model="form.minCreditScore" :min="0" :max="100" /></el-form-item>
        </el-card>

        <el-card shadow="never" class="block">
          <template #header>交期与交付</template>
          <el-form-item label="硬交期" prop="deadlineHard"><el-date-picker v-model="form.deadlineHard" type="date" value-format="YYYY-MM-DD" /></el-form-item>
          <el-form-item label="交付地址" prop="deliveryAddress"><el-input v-model="form.deliveryAddress" /></el-form-item>
          <el-form-item label="包装要求" prop="packaging"><el-input v-model="form.packaging" /></el-form-item>
          <el-form-item label="分期交付次数">
            <div class="step">
              <button type="button" class="step-btn" @click="setTimes(deliveryTimes - 1)">−</button>
              <span class="step-num">{{ deliveryTimes }}</span>
              <button type="button" class="step-btn" @click="setTimes(deliveryTimes + 1)">+</button>
            </div>
            <div class="hint" style="margin-left:0">后一期开始日必须等于前一期截止日；最后一期截止日必须等于硬交期。逾期未完成由买家另行确定返工期限，不延长原交期。</div>
          </el-form-item>
          <el-form-item v-for="(row, i) in deliveryPlan" :key="'plan-' + i" :label="`第${i + 1}期`">
            <div class="period-row">
              <div class="qty-wrap">
                <div class="step">
                  <button type="button" class="step-btn" @click="bumpPercent(i, -1)">−</button>
                  <input
                    class="step-input"
                    type="number"
                    min="1"
                    max="100"
                    v-model.number="row.percent"
                    @blur="clampPercent(i)"
                  />
                  <button type="button" class="step-btn" @click="bumpPercent(i, 1)">+</button>
                </div>
                <span class="qty-unit">%</span>
              </div>
              <el-date-picker
                v-model="row.startAt"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="开始日期"
                :disabled="i > 0"
              />
              <el-date-picker
                v-model="row.endAt"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="截止日期"
                :disabled="i === deliveryPlan.length - 1"
                @change="onPeriodEndChange(i)"
              />
            </div>
          </el-form-item>
          <div class="hint" style="margin:-4px 0 12px 130px">已分配 {{ planPercentSum }}% / 100%</div>
        </el-card>

        <el-card shadow="never" class="block">
          <template #header>工序 / 意向 / 图纸</template>
          <el-form-item label="工序列表" prop="processes">
            <div class="hint" style="margin:0 0 8px">
              一单一品：中标工厂做完下列全部工序后按期交货，工序只描述工艺路线，<b>不再单独填工序数量</b>，件数一律等于需求数量 {{ form.quantity || 0 }} 件。
            </div>
            <div class="row head">
              <span class="col-name">工序名</span>
              <span class="col-req">特殊要求</span>
              <span class="col-actions"></span>
            </div>
            <div v-for="(p, i) in form.processes" :key="i" class="row">
              <el-input v-model="p.processName" placeholder="如 粗车 / 热处理" class="col-name" />
              <el-input v-model="p.requirement" placeholder="该工序特殊要求，可空" class="col-req" />
              <div class="proc-actions">
                <el-button type="danger" :icon="Delete" circle plain @click="removeProcess(i)" />
                <el-button v-if="i === form.processes.length - 1" type="primary" :icon="Plus" plain @click="addProcess">添加工序</el-button>
              </div>
            </div>
          </el-form-item>
          <el-form-item label="意向期天数">
            <div class="step">
              <button type="button" class="step-btn" @click="form.intentionDays = Math.max(1, form.intentionDays - 1)">−</button>
              <span class="step-num">{{ form.intentionDays }}</span>
              <button type="button" class="step-btn" @click="form.intentionDays = Math.min(30, form.intentionDays + 1)">+</button>
            </div>
          </el-form-item>
          <el-form-item label="图纸/文档" prop="fileName">
            <input type="file" accept=".md,.pdf,.png,.jpg,.jpeg,.dwg,.dxf,.stp,.step,.zip" @change="onFile" />
            <span v-if="form.fileName" class="hint">已选：{{ form.fileName }}</span>
            <div class="hint">支持图纸或 .md 需求说明，不超过 5MB</div>
          </el-form-item>
          <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
        </el-card>

        <el-alert
          v-if="cancelStats.warn"
          type="warning"
          :closable="false"
          :title="'近 90 天已取消 ' + cancelStats.last90Days + ' 次，请确认仍要发布。'"
          style="margin-bottom:12px"
        />
        <el-alert v-if="editingId" type="warning" :closable="false" title="改完后先交运营审核，通过前工厂看不到。" style="margin-bottom:12px" />
        <el-alert v-else-if="copyFromId" type="info" :closable="false" :title="'将发布新需求，来源单 #' + copyFromId + ' 已取消并留档。'" style="margin-bottom:12px" />
        <el-button type="primary" native-type="button" :loading="saving" @click="submit">提交申请发布</el-button>
      </el-form>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import api from '../../api'
import { getDetail, publish, republish, getCancelStats } from '../../api/demand'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import InspectDeliveryRules from '../../components/InspectDeliveryRules.vue'

const router = useRouter()
const route = useRoute()
const formRef = ref()
const saving = ref(false)
const saved = ref(false)
const snapshot = ref('')
const file = ref(null)
const editingId = ref(null)
const copyFromId = ref(null)
const existingAttachmentId = ref(null)
const cancelStats = ref({ last90Days: 0, warn: false })
const require = (msg) => [{ required: true, message: msg, trigger: 'blur' }]
const rules = {
  title: require('请填写需求标题'),
  productName: require('请填写产品名称'),
  category: require('请选择产品类别'),
  partRevision: require('请填写图号/版本'),
  quantity: require('请填写数量'),
  material: require('请填写材料牌号'),
  generalTolerance: require('请选择一般公差'),
  tolerance: require('请填写关键公差'),
  roughness: require('请选择粗糙度'),
  surfaceTreatment: require('请填写表面处理'),
  heatTreatment: require('请填写热处理'),
  aql: [{
    validator: (_r, v, cb) => {
      if (needAql.value && !v) return cb(new Error('请选择 AQL'))
      cb()
    },
    trigger: 'change',
  }],
  inspectMode: require('请选择检验方式'),
  certList: [{ type: 'array', required: true, min: 1, message: '请选择认证要求', trigger: 'change' }],
  minYield: [{
    validator: (_r, v, cb) => {
      if (needFull.value && (v == null || v === '')) return cb(new Error('全检请填写最低良率'))
      cb()
    },
    trigger: 'change',
  }],
  deadlineHard: require('请选择硬交期'),
  deliveryAddress: require('请填写交付地址'),
  packaging: require('请填写包装要求'),
  processes: [{
    validator: (_r, _v, cb) => {
      const named = (form.processes || []).filter(p => p.processName && p.processName.trim())
      if (named.length < 1) return cb(new Error('请至少填写 1 道工序'))
      cb()
    },
    trigger: ['blur', 'change'],
  }],
  fileName: [{
    validator: (_r, _v, cb) => {
      (file.value || existingAttachmentId.value) ? cb() : cb(new Error('请上传图纸或文档'))
    },
    trigger: ['blur', 'change'],
  }],
}
const form = reactive({
  title: '', productName: '', category: '机加', partRevision: '',
  quantity: null,
  material: '', generalTolerance: 'ISO 2768-m', tolerance: '',
  roughness: '3.2', surfaceTreatment: '', heatTreatment: '',
  aql: '1.0', inspectMode: 'AQL', certList: [],
  minYield: null, minCreditScore: 60,
  deadlineHard: '',
  deliveryAddress: '', packaging: '',
  multiProcess: 1, intentionDays: 5,
  remark: '', fileName: '', processes: [
    { processNo: 1, processName: '', requirement: '' },
  ],
})

const CERT_OPTIONS = ['ISO9001', 'IATF16949', 'CE', 'ISO14001', 'AS9100', 'ISO13485']
const deliveryTimes = ref(1)
const deliveryPlan = ref([])

function todayStr() {
  const today = new Date()
  return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
}

function evenPercents(times) {
  const n = Math.max(1, times)
  const base = Math.floor(100 / n)
  const rest = 100 - base * n
  return Array.from({ length: n }, (_, i) => base + (i === n - 1 ? rest : 0))
}

function blankPeriod(prev = {}, percent = 100) {
  return {
    percent: Math.min(100, Math.max(1, Number(percent) || Number(prev.percent) || 1)),
    startAt: prev.startAt || '',
    endAt: prev.endAt || '',
  }
}

function fillEmptyPeriodDates() {
  const rows = deliveryPlan.value
  if (!rows.length) return
  if (!rows[0].startAt) rows[0].startAt = todayStr()
  chainPeriodStarts()
  applyHardOnLast()
}

function chainPeriodStarts() {
  const rows = deliveryPlan.value
  for (let i = 1; i < rows.length; i++) {
    rows[i].startAt = rows[i - 1].endAt || ''
  }
}

function applyHardOnLast() {
  const rows = deliveryPlan.value
  const hard = form.deadlineHard || ''
  if (!rows.length || !hard) return
  rows[rows.length - 1].endAt = hard
}

function onPeriodEndChange(i) {
  const rows = deliveryPlan.value
  if (i === rows.length - 1) {
    applyHardOnLast()
    return
  }
  chainPeriodStarts()
}

function setTimes(n) {
  const oldTimes = deliveryTimes.value
  const times = Math.min(10, Math.max(1, Number(n) || 1))
  deliveryTimes.value = times
  const kept = deliveryPlan.value || []
  const percents = evenPercents(times)
  deliveryPlan.value = Array.from({ length: times }, (_, i) => blankPeriod(kept[i], percents[i]))
  if (times > oldTimes) {
    const formerLast = deliveryPlan.value[oldTimes - 1]
    if (formerLast && formerLast.endAt && formerLast.endAt === form.deadlineHard) {
      formerLast.endAt = ''
    }
  }
  fillEmptyPeriodDates()
}

function bumpPercent(i, delta) {
  const row = deliveryPlan.value[i]
  if (!row) return
  row.percent = Math.min(100, Math.max(1, (Number(row.percent) || 1) + delta))
}

function clampPercent(i) {
  const row = deliveryPlan.value[i]
  if (!row) return
  const n = Number(row.percent)
  if (!Number.isFinite(n)) {
    row.percent = 1
    return
  }
  row.percent = Math.min(100, Math.max(1, Math.round(n)))
}

const planPercentSum = computed(() => deliveryPlan.value.reduce((s, r) => s + (Number(r.percent) || 0), 0))

const INSPECT_UNIT = { AQL: 5, FULL: 3 }
const needAql = computed(() => form.inspectMode === 'AQL')
const needFull = computed(() => form.inspectMode === 'FULL')
function onInspectMode(mode) {
  if (mode !== 'AQL') {
    form.aql = ''
    if (form.minYield == null) form.minYield = 0.97
  } else {
    form.aql = form.aql || '1.0'
    form.minYield = null
  }
}

watch(() => form.deadlineHard, () => fillEmptyPeriodDates())
setTimes(1)

function addProcess() {
  form.processes.push({ processNo: form.processes.length + 1, processName: '', requirement: '' })
}

async function removeProcess(i) {
  if (form.processes.length <= 1) {
    ElMessage.warning('至少保留 1 道工序')
    return
  }
  const p = form.processes[i]
  if (p.processName?.trim()) {
    await ElMessageBox.confirm(`删除工序「${p.processName}」？`, '删除确认', { type: 'warning' })
  }
  form.processes.splice(i, 1)
}

function onFile(e) {
  file.value = e.target.files?.[0] || null
  form.fileName = file.value ? file.value.name : ''
  formRef.value?.validateField?.('fileName')
}

function dateOnly(v) {
  if (!v) return ''
  return String(v).slice(0, 10)
}

function parseJson(raw) {
  try { return raw ? JSON.parse(raw) : {} } catch { return {} }
}

function periodPercentOf(x, fallback = 1) {
  if (x == null) return fallback
  if (typeof x === 'number') {
    const n = Math.round(x)
    return n >= 1 && n <= 100 ? n : fallback
  }
  if (typeof x === 'string') {
    const n = parseInt(x, 10)
    return n >= 1 && n <= 100 ? n : fallback
  }
  if (x.percent != null && Number(x.percent) > 0) {
    return Math.min(100, Math.max(1, Number(x.percent)))
  }
  const fromText = parseInt(String(x.text || ''), 10)
  if (String(x.text || '').includes('%') && fromText >= 1 && fromText <= 100) {
    return fromText
  }
  if (x.qty != null && Number(x.qty) >= 1 && Number(x.qty) <= 100) {
    return Number(x.qty)
  }
  return fallback
}

function buildBody(attachmentId) {
  return {
    title: form.title,
    productName: form.productName,
    category: form.category,
    quantity: form.quantity,
    material: form.material,
    tolerance: form.tolerance,
    surfaceTreatment: form.surfaceTreatment,
    inspectMode: form.inspectMode === 'FULL' ? 'FULL' : 'AQL',
    aql: form.inspectMode === 'AQL' ? form.aql : null,
    certification: (form.certList || []).join(','),
    minYield: form.inspectMode === 'FULL' ? form.minYield : null,
    minCreditScore: form.minCreditScore,
    deadlineHard: form.deadlineHard,
    deadlineFlexible: null,
    deliveryAddress: form.deliveryAddress,
    packaging: form.packaging,
    multiProcess: 1,
    intentionDays: form.intentionDays,
    remark: form.remark,
    generalTolerance: form.generalTolerance,
    partRevision: form.partRevision,
    extraJson: JSON.stringify({
      roughness: form.roughness,
      heatTreatment: form.heatTreatment,
    }),
    attachmentId,
    processes: form.processes,
    sourceDemandId: copyFromId.value,
    deliveryTimes: deliveryTimes.value,
    deliveryPlan: deliveryPlan.value.map(r => ({
      percent: Number(r.percent) || 0,
      text: (Number(r.percent) || 0) + '%',
      startAt: r.startAt,
      endAt: r.endAt,
    })),
  }
}

async function fillFrom(id, mode) {
  const view = await getDetail(id)
  const d = view.demand || {}
  if (mode === 'edit') {
    if (d.status !== 'RETURNED') {
      ElMessage.warning('只有退回修改的需求可以改内容后提交审核')
      router.replace('/buyer/demands')
      return
    }
    editingId.value = d.id
  } else if (mode === 'copy') {
    if (d.status !== 'CANCELLED') {
      ElMessage.warning('只能基于已取消的需求重新发布')
      router.replace('/buyer/demands')
      return
    }
    copyFromId.value = d.id
  }
  const extra = parseJson(d.extraJson)
  form.title = d.title || ''
  form.productName = d.productName || ''
  form.category = d.category || '机加'
  form.partRevision = d.partRevision || ''
  form.quantity = d.quantity
  form.material = d.material || ''
  form.generalTolerance = d.generalTolerance || 'ISO 2768-m'
  form.tolerance = d.tolerance || ''
  form.roughness = extra.roughness || '3.2'
  form.surfaceTreatment = d.surfaceTreatment || ''
  form.heatTreatment = extra.heatTreatment || ''
  form.inspectMode = String(d.inspectMode || 'AQL').toUpperCase().includes('FULL') ? 'FULL' : 'AQL'
  form.aql = form.inspectMode === 'AQL' ? (d.aql || '1.0') : ''
  form.certList = (d.certification || '').split(',').filter(Boolean)
  form.minYield = form.inspectMode === 'FULL' ? (d.minYield ?? 0.97) : null
  form.minCreditScore = d.minCreditScore
  form.deadlineHard = dateOnly(d.deadlineHard)
  form.deliveryAddress = d.deliveryAddress || ''
  form.packaging = d.packaging || ''
  form.multiProcess = d.multiProcess || 0
  form.intentionDays = d.intentionDays || 5
  form.remark = d.remark || ''
  try {
    const plan = JSON.parse(d.deliveryPlanJson || '[]')
    const items = Array.isArray(plan) ? plan : []
    setTimes(d.deliveryTimes || 1)
    deliveryPlan.value = deliveryPlan.value.map((row, i) => {
      const x = items[i]
      if (x == null) return row
      if (typeof x === 'string') {
        return { ...row, percent: periodPercentOf(x, row.percent) }
      }
      return {
        percent: periodPercentOf(x, row.percent),
        startAt: dateOnly(x.startAt) || row.startAt,
        endAt: dateOnly(x.endAt) || row.endAt,
      }
    })
    fillEmptyPeriodDates()
  } catch {
    setTimes(d.deliveryTimes || 1)
  }
  const named = (view.processes || []).filter(p => p.processName && p.processName !== '整单')
  form.processes = named.length >= 1 ? named.map(p => ({
    processNo: p.processNo,
    processName: p.processName,
    requirement: p.requirement || '',
  })) : form.processes
  form.multiProcess = 1
  const att = (view.attachments || [])[0]
  existingAttachmentId.value = att?.id || null
  form.fileName = att?.fileName || ''
}

function takeSnap() {
  snapshot.value = JSON.stringify({ form, deliveryTimes: deliveryTimes.value, deliveryPlan: deliveryPlan.value })
}
function dirty() {
  return snapshot.value && snapshot.value !== JSON.stringify({ form, deliveryTimes: deliveryTimes.value, deliveryPlan: deliveryPlan.value })
}

function firstError() {
  if (!form.title?.trim()) return '请填写需求标题'
  if (!form.productName?.trim()) return '请填写产品名称'
  if (!form.partRevision?.trim()) return '请填写图号/版本'
  if (!form.quantity || form.quantity < 1) return '请填写数量'
  if (!form.material?.trim()) return '请填写材料牌号'
  if (!form.tolerance?.trim()) return '请填写关键公差'
  if (!form.surfaceTreatment?.trim()) return '请填写表面处理'
  if (!form.heatTreatment?.trim()) return '请填写热处理'
  if (!form.certList?.length) return '请选择或添加认证要求'
  if (!form.inspectMode) return '请选择检验方式'
  if (form.inspectMode === 'AQL' && !form.aql) return '选择 AQL 抽样时请填写 AQL'
  if (form.inspectMode === 'FULL' && (form.minYield == null || form.minYield === '')) return '全检请填写最低良率'
  if (!form.deadlineHard) return '请选择硬交期'
  const hard = String(form.deadlineHard).slice(0, 10)
  const today = new Date()
  const todayStr = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  if (hard <= todayStr) return '硬交期必须晚于今天'
  if (!form.deliveryAddress?.trim()) return '请填写交付地址'
  if (!form.packaging?.trim()) return '请填写包装要求'
  if (deliveryPlan.value.some(r => !r.startAt || !r.endAt)) return '请为每一期填写开始时间和截止时间'
  if (deliveryPlan.value.some(r => r.startAt && r.endAt && r.startAt > r.endAt)) return '每期开始时间不能晚于截止时间'
  for (let i = 1; i < deliveryPlan.value.length; i++) {
    if (deliveryPlan.value[i].startAt !== deliveryPlan.value[i - 1].endAt) {
      return `第${i + 1}期开始时间必须等于第${i}期截止时间`
    }
  }
  const last = deliveryPlan.value[deliveryPlan.value.length - 1]
  if (last && last.endAt !== hard) return '最后一期截止时间必须等于硬交期'
  if (deliveryPlan.value.some(r => !r.percent || r.percent < 1 || r.percent > 100)) return '请填写每一期的交付比例（1%～100%）'
  if (planPercentSum.value !== 100) return '各期交付比例之和必须为 100%'
  const named = (form.processes || []).filter(p => p.processName?.trim())
  if (named.length < 1) return '请至少填写 1 道工序'
  if (!file.value && !existingAttachmentId.value) return '请上传图纸或文档'
  return ''
}

async function submit() {
  const err = firstError()
  if (err) {
    ElMessage.warning(err)
    try { await formRef.value?.validate() } catch { /* 标红必填项 */ }
    document.querySelector('.el-form-item.is-error')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    return
  }
  saving.value = true
  try {
    let attachmentId = existingAttachmentId.value
    if (file.value) {
      const fd = new FormData()
      fd.append('file', file.value)
      fd.append('bizType', 'DEMAND')
      const up = await api.post('/file/upload', fd)
      attachmentId = up?.id ?? up
    }
    if (!attachmentId) {
      ElMessage.warning('请上传图纸或文档')
      return
    }
    const body = buildBody(attachmentId)
    if (editingId.value) {
      await republish(editingId.value, body)
      ElMessage.success('已提交审核，通过后工厂才能看到')
    } else {
      await publish(body)
      ElMessage.success(copyFromId.value ? '新需求已提交申请发布，旧单已留档' : '已提交申请发布，等待运营审核')
    }
    saved.value = true
    router.push('/buyer/demands')
  } catch (e) {
    if (!e?.message) ElMessage.error('提交失败，请检查填写内容或稍后重试')
  } finally {
    saving.value = false
  }
}

onBeforeRouteLeave(async () => {
  if (saved.value || !dirty()) return true
  try {
    await ElMessageBox.confirm('有未保存内容，确定离开？', '提示', { type: 'warning' })
    return true
  } catch {
    return false
  }
})

onMounted(async () => {
  cancelStats.value = await getCancelStats()
  if (route.query.id) await fillFrom(route.query.id, 'edit')
  else if (route.query.from) await fillFrom(route.query.from, 'copy')
  takeSnap()
})
</script>

<style scoped>
.publish-page { display: flex; justify-content: center; }
.publish-form { width: 100%; max-width: 800px; }
.block { margin-bottom: 12px; }
.card-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.row { display: flex; gap: 10px; align-items: center; margin-bottom: 12px; }
.row.head { color: #606266; font-size: 12px; margin-bottom: 8px; }
.col-name { width: 160px; flex-shrink: 0; }
.col-req { flex: 1; min-width: 180px; }
.proc-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.hint { margin-left: 8px; color: #909399; font-size: 12px; }
.step {
  display: inline-flex;
  align-items: stretch;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
  width: 150px;
  height: 32px;
}
.step-btn {
  width: 32px;
  border: 0;
  background: #f5f7fa;
  cursor: pointer;
  font-size: 16px;
  line-height: 32px;
}
.step-btn:hover { color: #533afd; }
.step-num {
  flex: 1;
  text-align: center;
  line-height: 32px;
  border-left: 1px solid #dcdfe6;
  border-right: 1px solid #dcdfe6;
  background: #fff;
}
.step-input {
  flex: 1;
  width: 0;
  min-width: 56px;
  height: 32px;
  border: 0;
  border-left: 1px solid #dcdfe6;
  border-right: 1px solid #dcdfe6;
  text-align: center;
  outline: none;
  background: #fff;
  font-size: 14px;
}
.step-input::-webkit-outer-spin-button,
.step-input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}
.step-input[type='number'] {
  -moz-appearance: textfield;
  appearance: textfield;
}
.period-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  width: 100%;
}
.qty-wrap {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.qty-unit { color: #606266; font-size: 14px; }
.inspect-modes { display: flex; flex-direction: column; gap: 6px; align-items: flex-start; }
</style>
