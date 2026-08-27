<template>
  <div>
      <el-alert type="info" :closable="false" :title="topHint" style="margin-bottom:12px" />
      <el-empty v-if="!solutions.length" description="方案尚未下发，请等待运营审阅" />
      <el-row :gutter="16">
        <el-col :span="12" v-for="s in solutions" :key="s.id">
          <el-card :header="'AI 方案 ' + (s.type || '')">
            <p class="meta">{{ parseRationale(s.rationaleJson).rationale }}</p>
            <el-alert
              v-for="(r, i) in parseRationale(s.rationaleJson).risks"
              :key="i"
              type="warning"
              :closable="false"
              :title="r"
              style="margin-bottom:8px"
            />
            <el-table :data="parseCombo(s.finalComboJson)" size="small" border>
              <el-table-column prop="processName" label="工序" width="80" />
              <el-table-column label="工厂" min-width="180">
                <template #default="{ row }">
                  <div>{{ row.factoryName || ('工厂-' + row.factoryId) }}</div>
                  <div class="meta">信用 {{ row.creditScore ?? '-' }} · {{ authText(row.authStatus) }}</div>
                  <div class="meta">认证 {{ row.certs || '-' }}</div>
                  <div v-if="row.reason" class="meta">理由：{{ row.reason }}</div>
                  <div v-if="row.capacityCheck" class="meta">产能：{{ row.capacityCheck }}</div>
                </template>
              </el-table-column>
              <el-table-column prop="price" label="价格" width="80" />
              <el-table-column prop="days" label="工期" width="56" />
              <el-table-column v-if="canEdit" label="" width="64">
                <template #default="{ row }">
                  <el-button size="small" link type="primary" @click="openReplace(s, row)">换厂</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button v-if="canEdit" type="primary" style="width:100%;margin-top:10px" @click="select(s)">确认此方案</el-button>
          </el-card>
        </el-col>
      </el-row>

    <el-dialog v-model="repOpen" title="换厂（价格不变）" width="720px">
      <p>工序 {{ currentProcess?.processName }}，原价 {{ currentProcess?.price }} 不可改。</p>
      <el-table :data="alts" size="small" border>
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
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { listByDemand, alternatives, replaceFactory, selectSolution } from '../../api/solution'
import { getDetail } from '../../api/demand'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const demandId = route.params.demandId
const solutions = ref([])
const demandStatus = ref('')
const repOpen = ref(false)
const alts = ref([])
const currentSol = ref(null)
const currentProcess = ref(null)

const canEdit = computed(() => demandStatus.value === 'SOLUTION_GENERATED')
const topHint = computed(() => {
  if (demandStatus.value === 'SOLUTION_CONFIRMED') return '方案已确认，等待运营派单。确认后不可再换厂。'
  return '运营审过并下发的方案才会出现在这里。换厂只改工厂，价格不可改。确认后由运营派单给中标厂。'
})

function parseCombo(json) {
  try { return JSON.parse(json) } catch { return [] }
}
function parseRationale(raw) {
  if (!raw) return { rationale: '', risks: [] }
  try {
    const o = JSON.parse(raw)
    if (o && typeof o === 'object' && (o.rationale != null || o.risks)) {
      return { rationale: o.rationale || '', risks: Array.isArray(o.risks) ? o.risks : [] }
    }
  } catch { /* 旧数据是纯文本 */ }
  return { rationale: raw, risks: [] }
}
function authText(s) {
  return s === 'APPROVED' ? '已认证' : (s || '未认证')
}

async function load() {
  solutions.value = await listByDemand(demandId)
  try {
    const view = await getDetail(demandId)
    demandStatus.value = view.demand?.status || ''
  } catch { demandStatus.value = '' }
}

async function openReplace(s, row) {
  currentSol.value = s
  currentProcess.value = row
  alts.value = await alternatives(s.id, row.processNo)
  repOpen.value = true
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
</style>
