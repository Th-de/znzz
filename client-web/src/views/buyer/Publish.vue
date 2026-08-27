<template>
  <div>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px" style="max-width:800px">
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
          <template #header>质量门槛</template>
          <el-form-item label="AQL" prop="aql">
            <el-select v-model="form.aql" style="width:100%">
              <el-option label="0.65" value="0.65" />
              <el-option label="1.0" value="1.0" />
              <el-option label="1.5" value="1.5" />
              <el-option label="2.5" value="2.5" />
            </el-select>
          </el-form-item>
          <el-form-item label="检验方式" prop="inspectMode">
            <el-select v-model="form.inspectMode" style="width:100%">
              <el-option label="仅首件 FAI" value="FAI" />
              <el-option label="AQL 抽样" value="AQL" />
              <el-option label="全检" value="FULL" />
            </el-select>
          </el-form-item>
          <el-form-item label="认证要求" prop="certList">
            <el-select v-model="form.certList" multiple style="width:100%">
              <el-option label="ISO9001" value="ISO9001" />
              <el-option label="IATF16949" value="IATF16949" />
              <el-option label="CE" value="CE" />
            </el-select>
          </el-form-item>
          <el-form-item label="最低良率" prop="minYield"><el-input-number v-model="form.minYield" :min="0" :max="1" :step="0.01" /></el-form-item>
          <el-form-item label="最低信用分"><el-input-number v-model="form.minCreditScore" :min="0" :max="100" /></el-form-item>
        </el-card>

        <el-card shadow="never" class="block">
          <template #header>交期与交付</template>
          <el-form-item label="硬交期" prop="deadlineHard"><el-date-picker v-model="form.deadlineHard" type="date" value-format="YYYY-MM-DD" /></el-form-item>
          <el-form-item label="弹性交期"><el-date-picker v-model="form.deadlineFlexible" type="date" value-format="YYYY-MM-DD" /></el-form-item>
          <el-form-item label="交付地址" prop="deliveryAddress"><el-input v-model="form.deliveryAddress" /></el-form-item>
          <el-form-item label="包装要求" prop="packaging"><el-input v-model="form.packaging" /></el-form-item>
        </el-card>

        <el-card shadow="never" class="block">
          <template #header>工序 / 意向 / 图纸</template>
          <el-form-item label="工序列表" prop="processes">
            <div class="hint" style="margin:0 0 8px">至少 2 道工序。数量是该工序要加工的件数。</div>
            <div class="row head">
              <span class="col-name">工序名</span>
              <span class="col-qty">本工序数量(件)</span>
              <span class="col-req">特殊要求</span>
            </div>
            <div v-for="(p, i) in form.processes" :key="i" class="row">
              <el-input v-model="p.processName" placeholder="如 粗车 / 热处理" class="col-name" />
              <el-input-number v-model="p.quantity" :min="1" class="col-qty" />
              <el-input v-model="p.requirement" placeholder="该工序特殊要求，可空" class="col-req" />
              <el-button @click="form.processes.splice(i,1)">删</el-button>
            </div>
            <el-button @click="addProcess">加工序</el-button>
          </el-form-item>
          <el-form-item label="意向期天数"><el-input-number v-model="form.intentionDays" :min="1" :max="30" /></el-form-item>
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
        <el-button type="primary" :loading="saving" @click="submit">提交申请发布</el-button>
      </el-form>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import api from '../../api'
import { getDetail, publish, republish, getCancelStats } from '../../api/demand'
import { ElMessage, ElMessageBox } from 'element-plus'

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
  aql: require('请选择 AQL'),
  inspectMode: require('请选择检验方式'),
  certList: [{ type: 'array', required: true, min: 1, message: '请选择认证要求', trigger: 'change' }],
  minYield: require('请填写最低良率'),
  deadlineHard: require('请选择硬交期'),
  deliveryAddress: require('请填写交付地址'),
  packaging: require('请填写包装要求'),
  processes: [{
    validator: (_r, _v, cb) => {
      const n = (form.processes || []).filter(p => p.processName && p.processName.trim()).length
      n < 2 ? cb(new Error('请至少填写 2 道工序')) : cb()
    },
    trigger: 'blur',
  }],
  fileName: [{
    validator: (_r, _v, cb) => {
      (file.value || existingAttachmentId.value) ? cb() : cb(new Error('请上传图纸或文档'))
    },
    trigger: 'change',
  }],
}
const form = reactive({
  title: '', productName: '', category: '机加', partRevision: '',
  quantity: null,
  material: '', generalTolerance: 'ISO 2768-m', tolerance: '',
  roughness: '3.2', surfaceTreatment: '', heatTreatment: '',
  aql: '1.0', inspectMode: 'AQL', certList: [],
  minYield: 0.97, minCreditScore: 60,
  deadlineHard: '', deadlineFlexible: null,
  deliveryAddress: '', packaging: '',
  multiProcess: 1, intentionDays: 5,
  remark: '', fileName: '', processes: [
    { processNo: 1, processName: '粗车', quantity: null, requirement: '' },
    { processNo: 2, processName: '热处理', quantity: null, requirement: '' },
    { processNo: 3, processName: '精磨', quantity: null, requirement: '' },
  ],
})

function addProcess() {
  form.processes.push({ processNo: form.processes.length + 1, processName: '', quantity: form.quantity || 1, requirement: '' })
}

function onFile(e) {
  file.value = e.target.files?.[0] || null
  form.fileName = file.value ? file.value.name : ''
}

function dateOnly(v) {
  if (!v) return ''
  return String(v).slice(0, 10)
}

function parseJson(raw) {
  try { return raw ? JSON.parse(raw) : {} } catch { return {} }
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
    aql: form.aql,
    certification: (form.certList || []).join(','),
    minYield: form.minYield,
    minCreditScore: form.minCreditScore,
    deadlineHard: form.deadlineHard,
    deadlineFlexible: form.deadlineFlexible || null,
    deliveryAddress: form.deliveryAddress,
    packaging: form.packaging,
    multiProcess: 1,
    intentionDays: form.intentionDays,
    remark: form.remark,
    inspectMode: form.inspectMode,
    generalTolerance: form.generalTolerance,
    partRevision: form.partRevision,
    extraJson: JSON.stringify({
      roughness: form.roughness,
      heatTreatment: form.heatTreatment,
    }),
    attachmentId,
    processes: form.processes,
    sourceDemandId: copyFromId.value,
  }
}

async function fillFrom(id, mode) {
  const view = await getDetail(id)
  const d = view.demand || {}
  if (mode === 'edit') {
    if (d.status !== 'RETURNED') {
      ElMessage.warning('只有退回修改的需求可以改内容后提交审核')
      router.replace('/buyer/home')
      return
    }
    editingId.value = d.id
  } else if (mode === 'copy') {
    if (d.status !== 'CANCELLED') {
      ElMessage.warning('只能基于已取消的需求重新发布')
      router.replace('/buyer/home')
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
  form.aql = d.aql || '1.0'
  form.inspectMode = d.inspectMode || 'AQL'
  form.certList = (d.certification || '').split(',').filter(Boolean)
  form.minYield = d.minYield
  form.minCreditScore = d.minCreditScore
  form.deadlineHard = dateOnly(d.deadlineHard)
  form.deadlineFlexible = dateOnly(d.deadlineFlexible) || null
  form.deliveryAddress = d.deliveryAddress || ''
  form.packaging = d.packaging || ''
  form.multiProcess = d.multiProcess || 0
  form.intentionDays = d.intentionDays || 5
  form.remark = d.remark || ''
  const named = (view.processes || []).filter(p => p.processName && p.processName !== '整单')
  form.processes = named.length >= 2 ? named.map(p => ({
    processNo: p.processNo,
    processName: p.processName,
    quantity: p.quantity,
    requirement: p.requirement || '',
  })) : form.processes
  form.multiProcess = 1
  const att = (view.attachments || [])[0]
  existingAttachmentId.value = att?.id || null
  form.fileName = att?.fileName || ''
}

function takeSnap() {
  snapshot.value = JSON.stringify(form)
}
function dirty() {
  return snapshot.value && snapshot.value !== JSON.stringify(form)
}

async function submit() {
  await formRef.value.validate()
  saving.value = true
  try {
    let attachmentId = existingAttachmentId.value
    if (file.value) {
      const fd = new FormData()
      fd.append('file', file.value)
      fd.append('bizType', 'DEMAND')
      const up = await api.post('/file/upload', fd)
      attachmentId = up.id
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
    router.push('/buyer/home')
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
.block { margin-bottom: 12px; }
.row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.row.head { color: #606266; font-size: 12px; margin-bottom: 4px; }
.col-name { width: 160px; }
.col-qty { width: 150px; }
.col-req { width: 220px; }
.hint { margin-left: 8px; color: #909399; font-size: 12px; }
</style>
