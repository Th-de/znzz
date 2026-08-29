<template>
  <div style="max-width:860px">
      <el-alert type="info" :closable="false"
        title="不填完不能参与意向报名。设备请在「我的设备」维护（至少一台）；此处填写材料、工艺、合格率和企业介绍，工序产能由设备自动推导。"
        style="margin-bottom:16px" />

      <el-form label-width="120px">
        <h4>可加工材料 / 工艺 / 认证</h4>
        <el-form-item label="材料">
          <el-select v-model="form.materials" multiple placeholder="多选" style="width:100%">
            <el-option v-for="m in MATERIAL_OPTS" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="工艺">
          <el-select v-model="form.processes" multiple placeholder="多选" style="width:100%">
            <el-option v-for="m in PROCESS_OPTS" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="体系认证">
          <el-select v-model="form.certs" multiple placeholder="选填" style="width:100%">
            <el-option v-for="m in CERT_OPTS" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="检测手段">
          <el-input v-model="form.inspectDevices" placeholder="如 二次元 / 三坐标" />
        </el-form-item>
        <el-form-item label="历史合格率">
          <el-input-number v-model="form.yieldRate" :min="0" :max="1" :step="0.01" />
          <span class="hint">0~1，如 0.98 表示 98%</span>
        </el-form-item>

        <h4>企业介绍（买家可见）</h4>
        <el-form-item label="介绍">
          <el-input v-model="introduction" type="textarea" :rows="4"
                    placeholder="主营方向、核心设备、质量体系、代表客户等" />
        </el-form-item>

        <h4>工序产能（件/天，由设备自动推导）</h4>
        <el-alert type="info" :closable="false"
          title="产能不可手填：按「我的设备」里每台设备的适用工序与日产能自动合计。要调整请去改设备。"
          style="margin-bottom:10px" />
        <el-table :data="deviceCapacity" border size="small" style="max-width:520px">
          <el-table-column prop="processName" label="工序" />
          <el-table-column prop="dailyCapacity" label="日产能(件/天)" width="140" />
          <el-table-column prop="deviceCount" label="设备数" width="90" />
        </el-table>
        <el-empty v-if="!deviceCapacity.length" description="暂无设备，请先到「我的设备」添加" :image-size="60" />

        <div style="margin-top:20px">
          <el-button type="primary" @click="save">保存档案</el-button>
        </div>
      </el-form>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import api from '../../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const MATERIAL_OPTS = ['碳钢', '合金钢', '不锈钢', '铝合金', '铜合金', '工程塑料']
const PROCESS_OPTS = ['车削', '铣削', '磨削', '齿轮加工', '热处理', '表面处理', '钣金']
const CERT_OPTS = ['ISO9001', 'ISO14001', 'IATF16949', 'CE']

const form = reactive({
  materials: [],
  processes: [],
  certs: [],
  inspectDevices: '',
  yieldRate: 0.97,
})
const deviceCapacity = ref([])
const introduction = ref('')
const snapshot = ref('')
function takeSnap() { snapshot.value = JSON.stringify(form) }
function dirty() { return snapshot.value && snapshot.value !== JSON.stringify(form) }

async function load() {
  const data = await api.get('/enterprise/capability')
  const c = data.capability || {}
  if (c.materials) form.materials = c.materials
  if (c.processes) form.processes = c.processes
  if (c.certs) form.certs = c.certs
  if (c.inspectDevices) form.inspectDevices = c.inspectDevices
  if (c.yieldRate != null) form.yieldRate = c.yieldRate
  deviceCapacity.value = data.deviceCapacity || []
  try {
    const mine = await api.get('/enterprise/mine')
    introduction.value = mine.introduction || ''
  } catch { /* 忽略 */ }
  takeSnap()
}

async function save() {
  await api.put('/enterprise/capability', { ...form })
  await api.put('/enterprise/introduction', { introduction: introduction.value })
  takeSnap()
  ElMessage.success('档案已保存，可以去报名')
}

onBeforeRouteLeave(async () => {
  if (!dirty()) return true
  try {
    await ElMessageBox.confirm('有未保存内容，确定离开？', '提示', { type: 'warning' })
    return true
  } catch {
    return false
  }
})

onMounted(load)
</script>

<style scoped>
.row { display: flex; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; align-items: center; }
.hint { margin-left: 8px; color: #909399; font-size: 12px; }
h4 { margin: 20px 0 10px; }
</style>
