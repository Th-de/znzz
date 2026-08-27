<template>
  <div style="max-width:720px">
    <el-descriptions title="企业信息" :column="1" border>
      <el-descriptions-item label="企业名称">{{ info.name }}</el-descriptions-item>
      <el-descriptions-item label="企业类型">{{ info.type === 'FACTORY' ? '工厂' : '买家' }}</el-descriptions-item>
      <el-descriptions-item label="统一社会信用代码">{{ info.creditCode || '-' }}</el-descriptions-item>
      <el-descriptions-item label="联系人">{{ info.contactName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="地址">{{ info.address || '-' }}</el-descriptions-item>
      <el-descriptions-item label="信用分">{{ info.creditScore ?? '-' }}</el-descriptions-item>
    </el-descriptions>
    <el-descriptions title="账户" :column="1" border style="margin-top:16px">
      <el-descriptions-item label="可用余额">¥ {{ money(info.balance) }}</el-descriptions-item>
      <el-descriptions-item label="冻结中">¥ {{ money(info.frozen) }}</el-descriptions-item>
    </el-descriptions>
    <el-alert style="margin-top:12px" type="info" :closable="false"
      title="演示环境企业账户默认 100 万。意向金/保证金从可用余额冻结；余额不足将无法报名或锁价。" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '../api'

const info = ref({})
function money(v) {
  if (v == null) return '0.00'
  return Number(v).toFixed(2)
}
onMounted(async () => {
  info.value = await api.get('/enterprise/mine')
})
</script>
