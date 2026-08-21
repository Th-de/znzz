<template>
  <div>
      <el-alert type="info" :closable="false" title="运营审过并下发的方案才会出现在这里。换厂只改工厂，价格不可改。" style="margin-bottom:12px" />
      <el-empty v-if="!solutions.length" description="方案尚未下发，请等待运营审阅" />
      <el-row :gutter="16">
        <el-col :span="8" v-for="s in solutions" :key="s.id">
          <el-card :header="(s.type && s.type.startsWith('AI') ? 'AI综合 ' : '规则 ') + s.type">
            <p>综合评分：{{ s.score }}</p>
            <p class="meta">{{ s.rationaleJson || (s.type && s.type.startsWith('AI') ? '' : '按价格/工期/良率自动打分') }}</p>
            <el-table :data="parseCombo(s.finalComboJson)" size="small" border>
              <el-table-column prop="processName" label="工序" width="80" />
              <el-table-column label="工厂" min-width="160">
                <template #default="{ row }">
                  <div>{{ row.factoryName || ('工厂-' + row.factoryId) }}</div>
                  <div class="meta">信用 {{ row.creditScore ?? '-' }} · {{ authText(row.authStatus) }}</div>
                  <div class="meta">认证 {{ row.certs || '-' }}</div>
                  <div v-if="row.reason" class="meta">理由：{{ row.reason }}</div>
                </template>
              </el-table-column>
              <el-table-column prop="price" label="价格" width="80" />
              <el-table-column prop="days" label="工期" width="56" />
              <el-table-column label="" width="64">
                <template #default="{ row }">
                  <el-button size="small" link type="primary" @click="openReplace(s, row)">换厂</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button type="primary" style="width:100%;margin-top:10px" @click="select(s)">选择此方案</el-button>
          </el-card>
        </el-col>
      </el-row>

    <el-dialog v-model="repOpen" title="换厂（价格不变）" width="640px">
      <p>工序 {{ currentProcess?.processName }}，原价 {{ currentProcess?.price }} 不可改。</p>
      <el-table :data="alts" size="small" border>
        <el-table-column prop="factoryName" label="工厂" />
        <el-table-column prop="creditScore" label="信用" width="70" />
        <el-table-column prop="authStatus" label="认证" width="90">
          <template #default="{ row }">{{ authText(row.authStatus) }}</template>
        </el-table-column>
        <el-table-column prop="maxQty" label="产能" width="80" />
        <el-table-column prop="score" label="规则分" width="80" />
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
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listByDemand, alternatives, replaceFactory, selectSolution } from '../../api/solution'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const demandId = route.params.demandId
const solutions = ref([])
const repOpen = ref(false)
const alts = ref([])
const currentSol = ref(null)
const currentProcess = ref(null)

function parseCombo(json) {
  try { return JSON.parse(json) } catch { return [] }
}
function authText(s) {
  return s === 'APPROVED' ? '已认证' : (s || '未认证')
}

async function load() {
  solutions.value = await listByDemand(demandId)
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
  const orderId = await selectSolution(demandId, s.id)
  ElMessage.success('已选定方案，请上传合同并双签')
  router.push('/buyer/order/' + orderId)
}

onMounted(load)
</script>

<style scoped>
.meta { color: #909399; font-size: 12px; }
</style>
