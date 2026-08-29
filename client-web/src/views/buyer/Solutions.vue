<template>
  <div>
      <el-alert type="info" :closable="false" :title="topHint" style="margin-bottom:12px" />
      <el-empty v-if="!solutions.length" description="方案尚未下发，请等待运营审阅" />
      <el-row :gutter="16">
        <el-col :span="12" v-for="s in solutions" :key="s.id">
          <el-card :header="'AI 方案 ' + (s.type || '')">
            <p class="meta">生成时间 {{ fmtTime(s.createdAt) }}</p>
            <p class="meta">{{ parseRationale(s.rationaleJson).rationale }}</p>
            <el-alert
              v-for="(r, i) in parseRationale(s.rationaleJson).risks"
              :key="i"
              type="warning"
              :closable="false"
              :title="r"
              style="margin-bottom:8px"
            />
            <div v-if="parseRationale(s.rationaleJson).milestones.length" class="milestones">
              <div class="meta" style="font-weight:600">分期节点</div>
              <div v-for="(m, i) in parseRationale(s.rationaleJson).milestones" :key="i" class="meta">· {{ m }}</div>
            </div>
            <PagedBox :data="parseCombo(s.finalComboJson)" :page-size="8" v-slot="{ rows }">
            <el-table :data="rows" size="small" border>
              <el-table-column prop="processName" label="工序" width="80" />
              <el-table-column label="工厂" min-width="180">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openFactory(row.factoryId)">{{ row.factoryName || ('工厂-' + row.factoryId) }}</el-button>
                  <div class="meta">信用 {{ row.creditScore ?? '-' }} · {{ authText(row.authStatus) }}</div>
                  <div class="meta">认证 {{ row.certs || '-' }}</div>
                  <div v-if="row.reason" class="meta">理由：{{ row.reason }}</div>
                  <div v-if="row.capacityCheck" class="meta">产能：{{ row.capacityCheck }}</div>
                </template>
              </el-table-column>
              <el-table-column label="分量(件)" width="80">
                <template #default="{ row }">{{ row.quantity ?? '-' }}</template>
              </el-table-column>
              <el-table-column label="单价" width="76">
                <template #default="{ row }">{{ row.unitPrice ?? '-' }}</template>
              </el-table-column>
              <el-table-column prop="price" label="小计" width="80" />
              <el-table-column prop="days" label="工期" width="56" />
              <el-table-column v-if="canEdit" label="" width="110">
                <template #default="{ row }">
                  <el-button size="small" link type="primary" @click="openReplace(s, row)">换厂</el-button>
                  <el-button size="small" link type="warning" @click="openAlloc(s, row)">调分量</el-button>
                </template>
              </el-table-column>
            </el-table>
            </PagedBox>
            <el-button v-if="canEdit" type="primary" style="width:100%;margin-top:10px" @click="select(s)">确认此方案</el-button>
          </el-card>
        </el-col>
      </el-row>

    <el-dialog v-model="allocOpen" title="调整工序分量（同工序可多厂分摊）" width="720px" :close-on-click-modal="false">
      <p class="meta">工序「{{ allocProcess?.processName }}」需求 {{ allocNeed }} 件。给每个厂分配 0~承接量之间的件数，合计须等于需求量；填 0 表示不选该厂。</p>
      <PagedBox :data="allocRows" :page-size="8" v-slot="{ rows }">
      <el-table :data="rows" size="small" border>
        <el-table-column prop="factoryName" label="工厂" min-width="140" />
        <el-table-column prop="creditScore" label="信用" width="64" />
        <el-table-column prop="unitPrice" label="单价" width="80" />
        <el-table-column prop="maxQty" label="承接上限" width="90" />
        <el-table-column prop="dailyCapacitySum" label="日产能" width="80" />
        <el-table-column label="分配(件)" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.allocQty" :min="0" :max="row.maxQty || allocNeed" size="small" />
          </template>
        </el-table-column>
      </el-table>
      </PagedBox>
      <div class="meta" style="margin-top:8px">
        已分配 <b :style="{ color: allocSum === allocNeed ? '#67c23a' : '#f56c6c' }">{{ allocSum }}</b> / {{ allocNeed }} 件
      </div>
      <template #footer>
        <el-button @click="allocOpen = false">取消</el-button>
        <el-button type="primary" :disabled="allocSum !== allocNeed" @click="doReallocate">保存分配</el-button>
      </template>
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
        <el-descriptions-item label="体系认证" :span="2">{{ factoryInfo.certs || '-' }}</el-descriptions-item>
        <el-descriptions-item label="企业介绍" :span="2">{{ factoryInfo.introduction || '-' }}</el-descriptions-item>
      </el-descriptions>
      <h4 style="margin:12px 0 8px">设备</h4>
      <PagedBox :data="factoryInfo.devices || []" :page-size="8" v-slot="{ rows }">
      <el-table :data="rows" border size="small">
        <el-table-column prop="name" label="设备" min-width="120" />
        <el-table-column prop="processNames" label="适用工序" min-width="120" />
        <el-table-column prop="dailyCapacity" label="日产能" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="deviceStatusType(row.status)" size="small">{{ label(DEVICE_STATUS, row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="添加时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      </PagedBox>
    </el-dialog>

    <el-dialog v-model="repOpen" title="换厂（价格不变）" width="720px" :close-on-click-modal="false">
      <p>工序 {{ currentProcess?.processName }}，原价 {{ currentProcess?.price }} 不可改。</p>
      <PagedBox :data="alts" :page-size="8" v-slot="{ rows }">
      <el-table :data="rows" size="small" border>
        <el-table-column prop="factoryName" label="工厂" />
        <el-table-column prop="creditScore" label="信用" width="70" />
        <el-table-column prop="authStatus" label="认证" width="90">
          <template #default="{ row }">{{ authText(row.authStatus) }}</template>
        </el-table-column>
        <el-table-column prop="maxQty" label="承接量" width="80" />
        <el-table-column prop="dailyCapacity" label="日产能合计" width="110" />
        <el-table-column label="" width="80">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="doReplace(row)">选用</el-button>
          </template>
          </el-table-column>
        </el-table>
      </PagedBox>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { listByDemand, alternatives, replaceFactory, selectSolution, allocationCandidates, reallocate } from '../../api/solution'
import { getDetail } from '../../api/demand'
import api from '../../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import PagedBox from '../../components/PagedBox.vue'
import { fmtTime, DEVICE_STATUS, label, deviceStatusType } from '../../utils/labels'

const route = useRoute()
const demandId = route.params.demandId
const solutions = ref([])
const demandStatus = ref('')
const demandProcesses = ref([])
const repOpen = ref(false)
const alts = ref([])
const currentSol = ref(null)
const currentProcess = ref(null)
const allocOpen = ref(false)
const allocRows = ref([])
const allocProcess = ref(null)
const allocSol = ref(null)
const allocNeed = ref(0)
const allocSum = computed(() => allocRows.value.reduce((s, r) => s + (Number(r.allocQty) || 0), 0))
const factoryOpen = ref(false)
const factoryInfo = ref({})

async function openFactory(id) {
  factoryInfo.value = await api.get(`/enterprise/${id}/public-profile`)
  factoryOpen.value = true
}

const canEdit = computed(() => demandStatus.value === 'SOLUTION_GENERATED')
const topHint = computed(() => {
  if (demandStatus.value === 'SOLUTION_CONFIRMED') return '方案已确认，等待运营派单。确认后不可再换厂。'
  return '运营审过并下发的方案才会出现在这里。换厂只改工厂，价格不可改。确认后由运营派单给中标厂。'
})

function parseCombo(json) {
  try { return JSON.parse(json) } catch { return [] }
}
function parseRationale(raw) {
  if (!raw) return { rationale: '', risks: [], milestones: [] }
  try {
    const o = JSON.parse(raw)
    if (o && typeof o === 'object' && (o.rationale != null || o.risks)) {
      return {
        rationale: o.rationale || '',
        risks: Array.isArray(o.risks) ? o.risks : [],
        milestones: Array.isArray(o.milestones) ? o.milestones : [],
      }
    }
  } catch { /* 旧数据是纯文本 */ }
  return { rationale: raw, risks: [], milestones: [] }
}
function authText(s) {
  return s === 'APPROVED' ? '已认证' : (s || '未认证')
}

async function load() {
  solutions.value = await listByDemand(demandId)
  try {
    const view = await getDetail(demandId)
    demandStatus.value = view.demand?.status || ''
    demandProcesses.value = view.processes || []
  } catch { demandStatus.value = '' }
}

async function openReplace(s, row) {
  currentSol.value = s
  currentProcess.value = row
  alts.value = await alternatives(s.id, row.processNo)
  repOpen.value = true
}

async function openAlloc(s, row) {
  allocSol.value = s
  allocProcess.value = row
  const proc = demandProcesses.value.find(p => p.processNo === row.processNo)
  allocNeed.value = proc?.quantity || 0
  const combo = parseCombo(s.finalComboJson).filter(c => c.processNo === row.processNo)
  const list = await allocationCandidates(s.id, row.processNo)
  allocRows.value = (list || []).map(c => ({
    ...c,
    allocQty: combo.find(x => x.factoryId === c.factoryId)?.quantity || 0,
  }))
  allocOpen.value = true
}

async function doReallocate() {
  const allocations = allocRows.value
    .filter(r => (Number(r.allocQty) || 0) > 0)
    .map(r => ({ factoryId: r.factoryId, quantity: Number(r.allocQty) }))
  if (!allocations.length) {
    ElMessage.warning('至少给一个厂分配数量')
    return
  }
  await reallocate(allocSol.value.id, allocProcess.value.processNo, allocations)
  ElMessage.success('分配已更新')
  allocOpen.value = false
  load()
}

async function doReplace(row) {
  await replaceFactory(currentSol.value.id, currentProcess.value.processNo, row.factoryId)
  ElMessage.success('已换厂，价格未改')
  repOpen.value = false
  load()
}

async function select(s) {
  await ElMessageBox.confirm('确认后将提交给运营派单，此后不可再换厂或更换方案。', '确认方案', { type: 'warning' })
  await selectSolution(demandId, s.id)
  ElMessage.success('已确认，等待运营派单')
  load()
}

onMounted(load)
</script>

<style scoped>
.meta { color: #909399; font-size: 12px; }
.milestones { margin-bottom: 8px; padding: 6px 8px; background: #f5f7fa; border-radius: 4px; }
</style>
