<template>
  <el-descriptions v-if="buyer && buyer.name" :column="2" border>
    <el-descriptions-item label="企业名称" :span="staff ? 1 : 2">{{ buyer.name }}</el-descriptions-item>
    <el-descriptions-item v-if="staff && buyer.creditCode" label="统一社会信用代码">{{ buyer.creditCode }}</el-descriptions-item>
    <el-descriptions-item label="信用分">{{ buyer.creditScore ?? '-' }}</el-descriptions-item>
    <el-descriptions-item label="认证状态">{{ authText }}</el-descriptions-item>
    <el-descriptions-item label="联系人">{{ buyer.contactName || '-' }}</el-descriptions-item>
    <el-descriptions-item v-if="staff" label="联系电话">{{ buyer.contactPhone || '-' }}</el-descriptions-item>
    <el-descriptions-item label="地址" :span="2">{{ buyer.address || '-' }}</el-descriptions-item>
    <el-descriptions-item label="企业介绍" :span="2">{{ buyer.introduction || '-' }}</el-descriptions-item>
  </el-descriptions>
  <p v-else class="hint">暂无买家公开信息</p>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  buyer: { type: Object, default: () => ({}) },
  staff: { type: Boolean, default: false },
})

const AUTH = {
  APPROVED: '已认证',
  PASSED: '已认证',
  PENDING: '认证中',
  REJECTED: '未通过',
  NONE: '未认证',
}

const authText = computed(() => AUTH[props.buyer?.authStatus] || props.buyer?.authStatus || '-')
</script>

<style scoped>
.hint { color: #909399; font-size: 13px; margin: 0; }
</style>
