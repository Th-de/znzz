<template>
  <div>
      <el-table :data="demands" border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="productName" label="产品" />
        <el-table-column prop="quantity" label="数量" width="100" />
        <el-table-column label="阶段" width="120">
          <template #default="{ row }">{{ label(DEMAND_STATUS, row.status) }}</template>
        </el-table-column>
        <el-table-column label="倒计时" min-width="160">
          <template #default="{ row }">
            <IntentionCountdown v-if="row.status==='PUBLISHED'" :end-at="row.intentionEndAt" />
            <IntentionCountdown v-else-if="row.status==='LOCKING'" :end-at="row.lockingEndAt" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button v-if="row.status==='PUBLISHED' && row.applied" size="small" disabled>已报名</el-button>
            <el-button v-else-if="row.status==='PUBLISHED'" size="small" type="primary" @click="openIntention(row)">意向报名</el-button>
            <el-button v-if="row.status==='LOCKING' && row.myQuoteStatus==='LOCKED'" size="small" disabled>已锁定</el-button>
            <el-button v-else-if="row.status==='LOCKING' && row.applied" size="small" type="warning" @click="openLock(row)">锁定报价</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-dialog v-model="dialog" :title="'意向报名 - ' + current.title" width="560px">
        <p class="tip">不填价格。按工序报名，冻结意向金 1000 元。保证金期截止前未锁价将扣除意向金并记失信。</p>
        <CoverageBars :items="coverage" />
        <el-form label-width="120px">
          <el-form-item label="工序">
            <el-select v-model="form.processNo" placeholder="选工序" style="width:100%">
              <el-option v-for="p in processes" :key="p.processNo" :label="p.processName" :value="p.processNo" />
            </el-select>
          </el-form-item>
          <el-form-item label="投入设备" required>
            <el-checkbox-group v-model="form.deviceIds">
              <el-checkbox v-for="d in idleDevices" :key="d.id" :label="d.id">
                {{ d.name }} {{ d.model || '' }}（{{ d.dailyCapacity || '-' }}件/天 · {{ d.status === 'IDLE' ? '空闲' : d.status }}）
              </el-checkbox>
            </el-checkbox-group>
            <div class="tip" v-if="!idleDevices.length">暂无空闲设备，请先到「我的设备」添加</div>
          </el-form-item>
          <el-form-item label="日产能(件)">
            <span>{{ dailyCapacity || '见能力档案' }}</span>
          </el-form-item>
          <el-form-item label="最小承接量"><el-input-number v-model="form.minQty" :min="1" /></el-form-item>
          <el-form-item label="最大承接量"><el-input-number v-model="form.maxQty" :min="1" /></el-form-item>
          <el-form-item label=" ">
            <el-checkbox v-model="form.confirm">确认冻结意向金 1000 元</el-checkbox>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialog=false">取消</el-button>
          <el-button type="primary" :disabled="!form.confirm" @click="submitIntention">提交报名并支付意向金</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="lockDialog" title="锁定报价（绑定，取消将扣保证金）" width="560px">
        <el-form label-width="120px">
          <el-form-item label="工序">
            <el-select v-model="lockForm.processNo" style="width:100%">
              <el-option v-for="p in processes" :key="p.processNo" :label="p.processName" :value="p.processNo" />
            </el-select>
          </el-form-item>
          <el-form-item label="锁定报价"><el-input-number v-model="lockForm.price" :min="1" /></el-form-item>
          <el-form-item label="良率承诺"><el-input-number v-model="lockForm.yieldRate" :min="0" :max="1" :step="0.01" /></el-form-item>
          <el-form-item label="工期(天)"><el-input-number v-model="lockForm.promisedDays" :min="1" /></el-form-item>
          <el-form-item label="分段数">
            <el-input-number v-model="lockForm.stageCount" :min="1" :max="8" />
            <span class="tip">大于 1 时按段拆工单，金额均分</span>
          </el-form-item>
          <el-form-item label="最小承接量"><el-input-number v-model="lockForm.minQty" :min="1" /></el-form-item>
          <el-form-item label="最大承接量"><el-input-number v-model="lockForm.maxQty" :min="1" /></el-form-item>
          <el-form-item label=" ">
            <el-checkbox v-model="lockForm.confirm">确认冻结保证金</el-checkbox>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="lockDialog=false">取消</el-button>
          <el-button type="warning" :disabled="!lockForm.confirm" @click="submitLock">提交锁定报价</el-button>
        </template>
      </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import api from '../../api'
import { intention, lock } from '../../api/bidding'
import { getCoverage } from '../../api/demand'
import CoverageBars from '../../components/CoverageBars.vue'
import IntentionCountdown from '../../components/IntentionCountdown.vue'
import { ElMessage } from 'element-plus'
import { DEMAND_STATUS, label } from '../../utils/labels'

const router = useRouter()
const demands = ref([])
const processes = ref([])
const dialog = ref(false)
const lockDialog = ref(false)
const current = ref({})
const cap = ref({})
const coverage = ref([])
const form = reactive({ demandId: null, processNo: null, minQty: null, maxQty: null, deviceIds: [], confirm: false })
const idleDevices = ref([])
const lockForm = reactive({ demandId: null, processNo: null, price: null, yieldRate: 0.98, promisedDays: 10, minQty: null, maxQty: null, stageCount: 1, confirm: false })

const dailyCapacity = computed(() => {
  const rows = cap.value.capacityByProcess || []
  return rows[0]?.dailyCapacity || ''
})

async function load() {
  demands.value = await api.get('/demand/for-factory')
}

async function ensureProfile() {
  const data = await api.get('/enterprise/capability')
  cap.value = data.capability || {}
  if (!data.complete) {
    if (!(data.deviceCount > 0)) {
      ElMessage.warning('请先在「我的设备」中至少添加一台设备')
      router.push('/factory/devices')
      return false
    }
    ElMessage.warning('请先完善能力档案')
    router.push('/factory/profile')
    return false
  }
  return true
}

async function loadProcesses(row) {
  const ps = await api.get(`/demand/${row.id}/processes`)
  processes.value = ps.length ? ps : [{ processNo: 1, processName: '整单', quantity: row.quantity }]
}

async function openIntention(row) {
  if (!(await ensureProfile())) return
  current.value = row
  form.demandId = row.id
  form.processNo = null
  form.minQty = null
  form.maxQty = null
  form.deviceIds = []
  form.confirm = false
  idleDevices.value = await api.get('/device/idle')
  await loadProcesses(row)
  const cov = await getCoverage(row.id)
  coverage.value = cov.processes || []
  dialog.value = true
}

async function openLock(row) {
  current.value = row
  lockForm.demandId = row.id
  lockForm.processNo = null
  lockForm.price = null
  lockForm.minQty = null
  lockForm.maxQty = null
  lockForm.stageCount = 1
  lockForm.confirm = false
  await loadProcesses(row)
  lockDialog.value = true
}

async function submitIntention() {
  if (!form.processNo) return ElMessage.warning('请选择工序')
  if (!form.minQty || !form.maxQty) return ElMessage.warning('请填写承接量')
  if (form.minQty > form.maxQty) return ElMessage.warning('最小量不能大于最大量')
  if (!form.deviceIds?.length) return ElMessage.warning('请勾选投入的设备')
  const proc = processes.value.find(p => p.processNo === form.processNo)
  const capQty = proc?.quantity || current.value.quantity
  if (capQty && form.maxQty > capQty) {
    return ElMessage.warning('最大承接量不能超过该工序需求数量')
  }
  const data = await intention({
    demandId: form.demandId,
    processNo: form.processNo,
    minQty: form.minQty,
    maxQty: form.maxQty,
    deviceIds: form.deviceIds,
  })
  if (data?.payUrl) {
    window.open(data.payUrl, '_blank')
    ElMessage.success('已打开支付宝沙箱，付完 1000 元意向金后刷新「我的报名」')
  } else {
    ElMessage.success('报名成功，已冻结意向金 1000 元')
  }
  dialog.value = false
  load()
}

async function submitLock() {
  if (!lockForm.price) return ElMessage.warning('请填写锁定报价')
  const n = lockForm.stageCount || 1
  const days = Math.max(1, Math.floor((lockForm.promisedDays || n) / n))
  const stageCurveJson = n > 1
    ? JSON.stringify(Array.from({ length: n }, (_, i) => ({ name: '段' + (i + 1), days })))
    : null
  await lock({
    demandId: lockForm.demandId,
    processNo: lockForm.processNo,
    price: lockForm.price,
    yieldRate: lockForm.yieldRate,
    promisedDays: lockForm.promisedDays,
    minQty: lockForm.minQty,
    maxQty: lockForm.maxQty,
    stageCurveJson,
  })
  ElMessage.success('锁定报价成功，已冻结保证金')
  lockDialog.value = false
  load()
}

onMounted(load)
</script>

<style scoped>
.tip { color: #606266; font-size: 13px; margin: 0 0 12px; }
</style>
