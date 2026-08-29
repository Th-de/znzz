<template>
  <div>
    <div class="toolbar">
      <span>我的设备</span>
      <el-button type="primary" @click="openEdit()">新增设备</el-button>
    </div>
    <PagedBox :data="list" v-slot="{ rows }">
    <el-table :data="rows" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="设备名" />
      <el-table-column prop="model" label="型号" />
      <el-table-column prop="precisionText" label="精度" />
      <el-table-column prop="parts" label="可加工零件" />
      <el-table-column prop="materials" label="可加工材料" />
      <el-table-column label="适用工序" min-width="140">
        <template #default="{ row }">
          <el-tag v-for="p in splitNames(row.processNames)" :key="p" size="small" style="margin-right:4px">{{ p }}</el-tag>
          <span v-if="!row.processNames" class="hint">未填</span>
        </template>
      </el-table-column>
      <el-table-column prop="dailyCapacity" label="日产能(件/天)" width="130" />
      <el-table-column label="添加时间" width="160">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <div class="ops">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-dropdown @command="(c) => setStatus(row, c)">
              <el-button size="small">修改状态</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="GOOD">良好</el-dropdown-item>
                  <el-dropdown-item command="FAULT">故障</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    </PagedBox>

    <el-dialog v-model="dialog" :title="form.id ? '编辑设备' : '新增设备'" width="520px" :close-on-click-modal="false">
      <el-form label-width="110px">
        <el-form-item label="设备名" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="型号"><el-input v-model="form.model" /></el-form-item>
        <el-form-item label="精度"><el-input v-model="form.precisionText" /></el-form-item>
        <el-form-item label="可加工零件"><el-input v-model="form.parts" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="可加工材料"><el-input v-model="form.materials" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="适用工序" required>
          <el-select v-model="form.processList" multiple filterable allow-create default-first-option
                     placeholder="选择或输入该设备能做的工序，如 粗车、精磨" style="width:100%">
            <el-option v-for="p in PROCESS_OPTIONS" :key="p" :label="p" :value="p" />
          </el-select>
          <div class="hint">用于能力档案展示，请如实填写该设备能做的工序</div>
        </el-form-item>
        <el-form-item label="日产能(件/天)" required>
          <el-input-number v-model="form.dailyCapacity" :min="1" :max="99999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog=false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import api from '../../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import PagedBox from '../../components/PagedBox.vue'
import { fmtTime, DEVICE_STATUS, label, deviceStatusType } from '../../utils/labels'

const list = ref([])
const dialog = ref(false)
const PROCESS_OPTIONS = ['粗车', '精车', '铣削', '精磨', '热处理', '钻孔', '注塑', '钣金', '焊接', '折弯', '冲压', '装配', '表面处理', '滚齿', '插齿', '线切割']
const form = reactive({ id: null, name: '', model: '', precisionText: '', parts: '', materials: '', dailyCapacity: 120, processList: [] })

function splitNames(s) {
  return String(s || '').split(/[,，、\s]+/).filter(Boolean)
}

function statusText(s) {
  return label(DEVICE_STATUS, s)
}
function statusType(s) {
  return deviceStatusType(s)
}

async function load() {
  list.value = await api.get('/device/mine')
}
function openEdit(row) {
  Object.assign(form, row || { id: null, name: '', model: '', precisionText: '', parts: '', materials: '', dailyCapacity: 120, processNames: '' })
  form.processList = splitNames(row?.processNames)
  dialog.value = true
}
async function save() {
  if (!form.name?.trim()) return ElMessage.warning('请填写设备名')
  if (!form.dailyCapacity || form.dailyCapacity < 1) return ElMessage.warning('请填写日产能')
  if (!form.processList.length) return ElMessage.warning('请填写设备适用工序')
  await api.post('/device', { ...form, processNames: form.processList.join(',') })
  ElMessage.success('已保存')
  dialog.value = false
  load()
}
async function setStatus(row, status) {
  await api.post(`/device/${row.id}/status`, { status })
  ElMessage.success('状态已更新')
  load()
}
async function remove(row) {
  await ElMessageBox.confirm(`删除设备「${row.name}」？`, '确认')
  await api.delete(`/device/${row.id}`)
  ElMessage.success('已删除')
  load()
}
onMounted(load)
</script>

<style scoped>
.toolbar { display:flex; justify-content:space-between; align-items:center; margin-bottom:12px; font-weight:600; }
.hint { color:#909399; font-size:12px; }
.ops { display:flex; align-items:center; gap:8px; }
</style>
