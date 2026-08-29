<template>
  <div>
    <p class="tip">点「去办理」进入对应页完成上传合同、支付、问卷等。</p>
    <PagedBox :data="notifies" v-slot="{ rows }">
    <el-table :data="rows" border>
      <el-table-column prop="title" label="标题" width="240" />
      <el-table-column prop="content" label="内容" />
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="办理" width="140">
        <template #default="{ row }">
          <el-button v-if="row.link" size="small" type="primary" @click="$router.push(row.link)">{{ row.action || '去办理' }}</el-button>
          <span v-else>-</span>
        </template>
      </el-table-column>
    </el-table>
    </PagedBox>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '../api'
import PagedBox from '../components/PagedBox.vue'
import { fmtTime } from '../utils/labels'

const notifies = ref([])

async function load() {
  notifies.value = await api.get('/common/notifies')
}

onMounted(load)
</script>

<style scoped>
.tip { color: #606266; font-size: 13px; margin: 0 0 12px; }
</style>
