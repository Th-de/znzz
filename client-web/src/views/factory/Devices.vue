<template>
  <div>
    <div class="toolbar">
      <span>我的设备</span>
      <el-button type="primary" @click="openEdit()">新增设备</el-button>
    </div>
    <el-table :data="list" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="设备名" />
      <el-table-column prop="model" label="型号" />
      <el-table-column prop="precisionText" label="精度" />
      <el-table-column prop="parts" label="可加工零件" />
      <el-table-column prop="materials" label="可加工材料" />
      <el-table-column prop="dailyCapacity" label="日产能(件/天)" width="130" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-dropdown @command="(c) => setStatus(row, c)">
            <el-button size="small">改状态</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="IDLE">空闲</el-dropdown-item>
                <el-dropdown-item command="IN_USE">使用中</el-dropdown-item>
                <el-dropdown-item command="MAINTENANCE">维修中</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑设备' : '新增设备'" width="520px">
      <el-form label-width="110px">
        <el-form-item label="设备名" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="型号"><el-input v-model="form.model" /></el-form-item>
        <el-form-item label="精度"><el-input v-model="form.precisionText" /></el-form-item>
        <el-form-item label="可加工零件"><el-input v-model="form.parts" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="可加工材料"><el-input v-model="form.materials" placeholder="逗号分隔" /></el-form-item>
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

const list = ref([])
const dialog = ref(false)
const form = reactive({ id: null, name: '', model: '', precisionText: '', parts: '', materials: '', dailyCapacity: 120 })

function statusText(s) {
  return ({ IDLE: '空闲', IN_USE: '使用中', MAINTENANCE: '维修中' })[s] || s
}
function statusType(s) {
  return ({ IDLE: 'success', IN_USE: 'warning', MAINTENANCE: 'danger' })[s] || 'info'
}

async function load() {
  list.value = await api.get('/device/mine')
}
function openEdit(row) {
  Object.assign(form, row || { id: null, name: '', model: '', precisionText: '', parts: '', materials: '', dailyCapacity: 120 })
  dialog.value = true
}
async function save() {
  if (!form.name?.trim()) return ElMessage.warning('请填写设备名')
  if (!form.dailyCapacity || form.dailyCapacity < 1) return ElMessage.warning('请填写日产能')
  await api.post('/device', { ...form })
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
</style>
