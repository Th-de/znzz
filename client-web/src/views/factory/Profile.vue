<template>
  <div style="max-width:860px">
      <el-alert type="info" :closable="false" title="不填完不能参与意向报名。设备至少一台，并填写材料、工艺、合格率和工序产能。" style="margin-bottom:16px" />

      <el-form label-width="120px">
        <h4>设备清单</h4>
        <div v-for="(d, i) in form.devices" :key="'d'+i" class="row">
          <el-input v-model="d.name" placeholder="设备名" style="width:160px" />
          <el-input v-model="d.model" placeholder="型号" style="width:140px" />
          <el-input v-model="d.precision" placeholder="精度" style="width:120px" />
          <el-input-number v-model="d.qty" :min="1" placeholder="数量" />
          <el-button @click="form.devices.splice(i,1)">删</el-button>
        </div>
        <el-button size="small" @click="form.devices.push({ name:'', model:'', precision:'', qty:1 })">加设备</el-button>

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

        <h4>工序产能（件/天）</h4>
        <div v-for="(c, i) in form.capacityByProcess" :key="'c'+i" class="row">
          <el-input v-model="c.processName" placeholder="工序名" style="width:160px" />
          <el-input-number v-model="c.dailyCapacity" :min="1" placeholder="件/天" />
          <el-input-number v-model="c.loadPct" :min="0" :max="100" placeholder="负荷%" />
          <el-button @click="form.capacityByProcess.splice(i,1)">删</el-button>
        </div>
        <el-button size="small" @click="form.capacityByProcess.push({ processName:'', dailyCapacity:1000, loadPct:50 })">加产能行</el-button>

        <div style="margin-top:20px">
          <el-button type="primary" @click="save">保存档案</el-button>
        </div>
      </el-form>
  </div>
</template>

<script setup>
import { reactive, onMounted } from 'vue'
import api from '../../api'
import { ElMessage } from 'element-plus'

const MATERIAL_OPTS = ['碳钢', '合金钢', '不锈钢', '铝合金', '铜合金', '工程塑料']
const PROCESS_OPTS = ['车削', '铣削', '磨削', '齿轮加工', '热处理', '表面处理', '钣金']
const CERT_OPTS = ['ISO9001', 'ISO14001', 'IATF16949', 'CE']

const form = reactive({
  devices: [{ name: '', model: '', precision: '', qty: 1 }],
  materials: [],
  processes: [],
  certs: [],
  inspectDevices: '',
  yieldRate: 0.97,
  capacityByProcess: [{ processName: '整单', dailyCapacity: 1000, loadPct: 50 }],
})

async function load() {
  const data = await api.get('/enterprise/capability')
  const c = data.capability || {}
  if (c.devices?.length) form.devices = c.devices
  if (c.materials) form.materials = c.materials
  if (c.processes) form.processes = c.processes
  if (c.certs) form.certs = c.certs
  if (c.inspectDevices) form.inspectDevices = c.inspectDevices
  if (c.yieldRate != null) form.yieldRate = c.yieldRate
  if (c.capacityByProcess?.length) form.capacityByProcess = c.capacityByProcess
}

async function save() {
  await api.put('/enterprise/capability', { ...form })
  ElMessage.success('档案已保存，可以去报名')
}

onMounted(load)
</script>

<style scoped>
.row { display: flex; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; align-items: center; }
.hint { margin-left: 8px; color: #909399; font-size: 12px; }
h4 { margin: 20px 0 10px; }
</style>
