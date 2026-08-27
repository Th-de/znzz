<template>
  <div>
      <el-table :data="quotations" border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="demandId" label="需求ID" width="80" />
        <el-table-column prop="demandTitle" label="需求标题" min-width="160" />
        <el-table-column prop="processNo" label="工序" width="70" />
        <el-table-column label="承接区间" width="140">
          <template #default="{ row }">{{ row.minQty }} ~ {{ row.maxQty }}</template>
        </el-table-column>
        <el-table-column prop="price" label="锁定报价" width="110" />
        <el-table-column label="阶段" width="120">
          <template #default="{ row }">{{ label(QUOTE_STATUS, row.status) }}</template>
        </el-table-column>
        <el-table-column label="意向金" width="130">
          <template #default="{ row }">{{ label(INTENTION_STATUS, row.intentionStatus) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button v-if="row.status==='INTENTION' && row.intentionStatus==='PENDING_PAY'" size="small" type="primary" @click="pay(row)">继续支付</el-button>
            <el-button v-if="row.status==='INTENTION'" size="small" type="danger" @click="cancel(row)">取消意向</el-button>
            <el-button v-if="row.status==='LOCKED'" size="small" type="danger" @click="cancelLock(row)">取消锁定(扣保证金)</el-button>
          </template>
        </el-table-column>
      </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '../../api'
import { ElMessage } from 'element-plus'
import { payIntention } from '../../api/bidding'
import { QUOTE_STATUS, INTENTION_STATUS, label } from '../../utils/labels'

const quotations = ref([])

async function load() {
  quotations.value = await api.get('/bidding/mine')
}

async function pay(row) {
  const data = await payIntention(row.id)
  if (data?.payUrl) {
    window.open(data.payUrl, '_blank')
    ElMessage.success('已打开支付宝沙箱，付完后刷新本页')
  }
}

async function cancel(row) {
  await api.post(`/bidding/${row.id}/cancel-intention`)
  ElMessage.success('已取消，意向金退还')
  load()
}

async function cancelLock(row) {
  await api.post(`/bidding/${row.id}/cancel-lock`)
  ElMessage.success('已取消锁定，保证金已扣除')
  load()
}

onMounted(load)
</script>

