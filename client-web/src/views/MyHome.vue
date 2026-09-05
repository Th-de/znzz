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
    <el-descriptions v-if="info.type === 'FACTORY'" title="平台履约数据（买家可见，不可手改）" :column="1" border style="margin-top:16px">
      <el-descriptions-item label="质检合格率">
        {{ profile.inspectionPassRate != null ? profile.inspectionPassRate + '%（已结算且已出结论 ' + profile.inspectionCount + ' 单）' : '暂无已结算质检' }}
      </el-descriptions-item>
      <el-descriptions-item label="已结算工单">{{ profile.settledStages ?? 0 }}</el-descriptions-item>
      <el-descriptions-item label="买家评分">{{ profile.surveyAvg != null ? profile.surveyAvg + ' 星' : '暂无' }}</el-descriptions-item>
    </el-descriptions>
    <el-descriptions title="账户" :column="1" border style="margin-top:16px">
      <el-descriptions-item label="可用余额">¥ {{ money(info.balance) }}</el-descriptions-item>
      <el-descriptions-item label="冻结中">¥ {{ money(info.frozen) }}</el-descriptions-item>
    </el-descriptions>
    <el-alert style="margin-top:12px" type="info" :closable="false"
      title="演示环境企业账户默认 100 万。意向金/保证金从可用余额冻结；余额不足将无法报名或锁价。" />

    <el-card shadow="never" style="margin-top:16px">
      <template #header>修改登录密码</template>
      <el-form label-width="90px" style="max-width:420px">
        <el-form-item label="原密码"><el-input v-model="pwd.oldPassword" type="password" show-password /></el-form-item>
        <el-form-item label="新密码"><el-input v-model="pwd.newPassword" type="password" show-password placeholder="至少 6 位" /></el-form-item>
        <el-form-item label="确认新密码"><el-input v-model="pwd.confirm" type="password" show-password /></el-form-item>
        <el-button type="primary" @click="changePwd">修改密码</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import api from '../api'
import { ElMessage } from 'element-plus'

const info = ref({})
const profile = ref({})
const pwd = reactive({ oldPassword: '', newPassword: '', confirm: '' })
function money(v) {
  if (v == null) return '0.00'
  return Number(v).toFixed(2)
}
async function changePwd() {
  if (!pwd.oldPassword) return ElMessage.warning('请填写原密码')
  if (!pwd.newPassword || pwd.newPassword.length < 6) return ElMessage.warning('新密码至少 6 位')
  if (pwd.newPassword !== pwd.confirm) return ElMessage.warning('两次输入的新密码不一致')
  await api.post('/auth/change-password', { oldPassword: pwd.oldPassword, newPassword: pwd.newPassword })
  ElMessage.success('密码已修改，下次登录请用新密码')
  pwd.oldPassword = pwd.newPassword = pwd.confirm = ''
}
onMounted(async () => {
  info.value = await api.get('/enterprise/mine')
  if (info.value?.type === 'FACTORY' && info.value.id) {
    profile.value = await api.get(`/enterprise/${info.value.id}/public-profile`)
  }
})
</script>
