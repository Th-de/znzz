<template>
  <div>
      <el-table :data="orders" border>
        <el-table-column prop="title" label="需求" min-width="180" />
        <el-table-column prop="productName" label="产品" width="140" />
        <el-table-column prop="factoryNames" label="工厂" min-width="160" />
        <el-table-column prop="totalAmount" label="总金额" width="110" />
        <el-table-column prop="commissionAmount" label="佣金" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">{{ label(ORDER_STATUS, row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="$router.push('/buyer/order/' + row.id)">合同与履约</el-button>
          </template>
        </el-table-column>
      </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listOrders } from '../../api/order'
import { ORDER_STATUS, label } from '../../utils/labels'

const orders = ref([])

async function load() {
  orders.value = await listOrders()
}

onMounted(load)
</script>

