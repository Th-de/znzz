<template>
  <div>
      <el-form :model="form" label-width="130px" style="max-width:800px">
        <el-card shadow="never" class="block">
          <template #header>基础</template>
          <el-form-item label="需求标题" required><el-input v-model="form.title" /></el-form-item>
          <el-form-item label="产品名称" required><el-input v-model="form.productName" /></el-form-item>
          <el-form-item label="产品类别" required>
            <el-select v-model="form.category" style="width:100%">
              <el-option label="机加" value="机加" />
              <el-option label="齿轮" value="齿轮" />
              <el-option label="注塑" value="注塑" />
              <el-option label="钣金" value="钣金" />
              <el-option label="其他" value="其他" />
            </el-select>
          </el-form-item>
          <el-form-item label="图号/版本" required><el-input v-model="form.partRevision" placeholder="如 GEAR-001 / A" /></el-form-item>
          <el-form-item label="数量" required><el-input-number v-model="form.quantity" :min="1" /></el-form-item>
          <el-form-item label="年用量" required><el-input-number v-model="form.annualQty" :min="0" /></el-form-item>
        </el-card>

        <el-card shadow="never" class="block">
          <template #header>技术规格</template>
          <el-form-item label="材料牌号" required>
            <el-input v-model="form.material" placeholder="如 20CrMnTi 或 6061-T6，不要只写钢材" />
          </el-form-item>
          <el-form-item label="一般公差" required>
            <el-select v-model="form.generalTolerance" style="width:100%">
              <el-option label="ISO 2768-m" value="ISO 2768-m" />
              <el-option label="ISO 2768-f" value="ISO 2768-f" />
              <el-option label="按图纸" value="按图纸" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键公差" required><el-input v-model="form.tolerance" placeholder="如配合孔 ±0.01mm" /></el-form-item>
          <el-form-item label="粗糙度 Ra" required>
            <el-select v-model="form.roughness" style="width:100%">
              <el-option label="Ra 3.2" value="3.2" />
              <el-option label="Ra 1.6" value="1.6" />
              <el-option label="Ra 0.8" value="0.8" />
            </el-select>
          </el-form-item>
          <el-form-item label="表面处理" required><el-input v-model="form.surfaceTreatment" /></el-form-item>
          <el-form-item label="热处理" required><el-input v-model="form.heatTreatment" /></el-form-item>
        </el-card>

        <el-card shadow="never" class="block">
          <template #header>质量门槛</template>
          <el-form-item label="AQL" required>
            <el-select v-model="form.aql" style="width:100%">
              <el-option label="0.65" value="0.65" />
              <el-option label="1.0" value="1.0" />
              <el-option label="1.5" value="1.5" />
              <el-option label="2.5" value="2.5" />
            </el-select>
          </el-form-item>
          <el-form-item label="检验方式" required>
            <el-select v-model="form.inspectMode" style="width:100%">
              <el-option label="仅首件 FAI" value="FAI" />
              <el-option label="AQL 抽样" value="AQL" />
              <el-option label="全检" value="FULL" />
            </el-select>
          </el-form-item>
          <el-form-item label="认证要求" required>
            <el-select v-model="form.certList" multiple style="width:100%">
              <el-option label="ISO9001" value="ISO9001" />
              <el-option label="IATF16949" value="IATF16949" />
              <el-option label="CE" value="CE" />
            </el-select>
          </el-form-item>
          <el-form-item label="最低良率" required><el-input-number v-model="form.minYield" :min="0" :max="1" :step="0.01" /></el-form-item>
          <el-form-item label="最低信用分"><el-input-number v-model="form.minCreditScore" :min="0" :max="100" /></el-form-item>
        </el-card>

        <el-card shadow="never" class="block">
          <template #header>交期与交付</template>
          <el-form-item label="硬交期" required><el-date-picker v-model="form.deadlineHard" type="date" value-format="YYYY-MM-DD" /></el-form-item>
          <el-form-item label="弹性交期"><el-date-picker v-model="form.deadlineFlexible" type="date" value-format="YYYY-MM-DD" /></el-form-item>
          <el-form-item label="交付地址" required><el-input v-model="form.deliveryAddress" /></el-form-item>
          <el-form-item label="包装要求" required><el-input v-model="form.packaging" /></el-form-item>
        </el-card>

        <el-card shadow="never" class="block">
          <template #header>工序 / 意向 / 图纸</template>
          <el-form-item label="工序列表" required>
            <div class="hint" style="margin:0 0 8px">至少 2 道工序，按工序报名才能看出覆盖度和方案拆分。</div>
            <div v-for="(p, i) in form.processes" :key="i" class="row">
              <el-input v-model="p.processName" placeholder="工序名" style="width:140px" />
              <el-input-number v-model="p.quantity" :min="1" />
              <el-input v-model="p.requirement" placeholder="特殊要求" style="width:200px" />
              <el-button @click="form.processes.splice(i,1)">删</el-button>
            </div>
            <el-button @click="addProcess">加工序</el-button>
          </el-form-item>
          <el-form-item label="意向期天数"><el-input-number v-model="form.intentionDays" :min="1" :max="30" /></el-form-item>
          <el-form-item label="权重">
            <div class="row">
              <span>成本</span><el-input-number v-model="form.weightCost" :min="0" :max="1" :step="0.01" />
              <span>工期</span><el-input-number v-model="form.weightTime" :min="0" :max="1" :step="0.01" />
              <span>质量</span><el-input-number v-model="form.weightQuality" :min="0" :max="1" :step="0.01" />
            </div>
            <span class="hint">三者之和须为 1</span>
          </el-form-item>
          <el-form-item label="图纸/文档" required>
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
import { useRoute, useRouter } from 'vue-router'
import api from '../../api'
import { getDetail, publish, republish, getCancelStats } from '../../api/demand'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const saving = ref(false)
const file = ref(null)
const editingId = ref(null)
const copyFromId = ref(null)
const existingAttachmentId = ref(null)
const cancelStats = ref({ last90Days: 0, warn: false })
const form = reactive({
  title: '', productName: '', category: '机加', partRevision: '',
  quantity: null, annualQty: null,
  material: '', generalTolerance: 'ISO 2768-m', tolerance: '',
  roughness: '3.2', surfaceTreatment: '', heatTreatment: '',
  aql: '1.0', inspectMode: 'AQL', certList: [],
  minYield: 0.97, minCreditScore: 60,
  deadlineHard: '', deadlineFlexible: null,
  deliveryAddress: '', packaging: '',
  multiProcess: 1, intentionDays: 5,
  weightCost: 0.34, weightTime: 0.33, weightQuality: 0.33,
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
      annualQty: form.annualQty,
    }),
    attachmentId,
    weightCost: form.weightCost,
    weightTime: form.weightTime,
    weightQuality: form.weightQuality,
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
  const weight = parseJson(d.weightJson)
  form.title = d.title || ''
  form.productName = d.productName || ''
  form.category = d.category || '机加'
  form.partRevision = d.partRevision || ''
  form.quantity = d.quantity
  form.annualQty = extra.annualQty ?? null
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
  form.weightCost = weight.cost ?? 0.34
  form.weightTime = weight.time ?? 0.33
  form.weightQuality = weight.quality ?? 0.33
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

async function submit() {
  const named = (form.processes || []).filter(p => p.processName && p.processName.trim())
  if (named.length < 2) return ElMessage.warning('请至少填写 2 道工序')
  if (!form.category) return ElMessage.warning('请选择产品类别')
  if (!form.partRevision) return ElMessage.warning('请填写图号/版本')
  if (!form.tolerance) return ElMessage.warning('请填写关键公差')
  if (!form.surfaceTreatment) return ElMessage.warning('请填写表面处理')
  if (!form.heatTreatment) return ElMessage.warning('请填写热处理')
  if (form.annualQty === null || form.annualQty === undefined) return ElMessage.warning('请填写年用量')
  if (!form.packaging) return ElMessage.warning('请填写包装要求')
  if (!(form.certList || []).length) return ElMessage.warning('请选择认证要求')
  const sum = Number(form.weightCost) + Number(form.weightTime) + Number(form.weightQuality)
  if (Math.abs(sum - 1) > 0.02) return ElMessage.warning('权重之和必须为 1')
  if (!file.value && !existingAttachmentId.value) return ElMessage.warning('请上传图纸或 .md 文档')
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
    router.push('/buyer/home')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  cancelStats.value = await getCancelStats()
  if (route.query.id) await fillFrom(route.query.id, 'edit')
  else if (route.query.from) await fillFrom(route.query.from, 'copy')
})
</script>

<style scoped>
.block { margin-bottom: 12px; }
.row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.hint { margin-left: 8px; color: #909399; font-size: 12px; }
</style>
