<template>
  <div>
    <div v-if="!current">
      <p class="tip">只显示您参与过的需求（买家为自己发布的，工厂为自己报过名的），按报名/发布时间从新到旧。点击一行查看该需求的通知。</p>
      <el-empty v-if="!demands.length" description="暂无您参与过的需求" :image-size="72" />
      <PagedBox v-else :data="demands" v-slot="{ rows }">
        <el-table :data="rows" border highlight-current-row class="demand-table" @row-click="openDemand">
          <el-table-column prop="demandId" label="需求ID" width="90" />
          <el-table-column prop="title" label="标题" min-width="180" />
          <el-table-column label="报名时间" width="170">
            <template #default="{ row }">{{ fmtTime(row.bidAt) }}</template>
          </el-table-column>
          <el-table-column label="阶段" width="140">
            <template #default="{ row }">
              <el-tag v-if="row.status === 'LOSE'" type="danger" size="small">已落选</el-tag>
              <span v-else>{{ label(DEMAND_STATUS, row.status) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="最新通知" width="170">
            <template #default="{ row }">{{ fmtTime(row.latestNotifyAt) }}</template>
          </el-table-column>
        </el-table>
      </PagedBox>
    </div>
    <div v-else>
      <div class="head">
        <el-button @click="current = null">返回需求列表</el-button>
        <span class="meta">需求 #{{ current.demandId }}　{{ current.title }}　{{ label(DEMAND_STATUS, current.status) }}</span>
      </div>
      <p class="tip">本需求通知按时间从新到旧。</p>
      <el-empty v-if="!(current.notifies || []).length" description="该需求暂无通知" :image-size="72" />
      <PagedBox v-else :data="current.notifies" v-slot="{ rows }">
        <el-table :data="rows" border>
          <el-table-column prop="title" label="标题" width="240" />
          <el-table-column prop="content" label="内容" min-width="200" />
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button v-if="row.link" size="small" type="primary" @click.stop="$router.push(row.link)">{{ row.action || '去办理' }}</el-button>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
      </PagedBox>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '../api'
import PagedBox from '../components/PagedBox.vue'
import { DEMAND_STATUS, fmtTime, label } from '../utils/labels'

const demands = ref([])
const current = ref(null)

function openDemand(row) {
  current.value = row
}

async function load() {
  demands.value = (await api.get('/common/notify-demands')) || []
}

onMounted(load)
</script>

<style scoped>
.tip { color: #606266; font-size: 13px; margin: 0 0 12px; }
.head { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.meta { color: #303133; font-size: 14px; }
.demand-table { cursor: pointer; }
</style>
