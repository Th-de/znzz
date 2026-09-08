<template>
  <header class="app-header">
    <div class="brand">
      <img src="/logo.png" alt="智能制造云平台" />
      <div class="brand-text">
        <span class="brand-name">智能制造云平台</span>
        <span class="brand-role">{{ roleLabel }}</span>
      </div>
    </div>
    <div class="nav-wrap">
      <el-menu
        :key="active"
        mode="horizontal"
        :ellipsis="false"
        :default-active="active"
        :router="routerMode"
        @select="onSelect"
      >
        <template v-for="it in items" :key="it.index">
          <el-sub-menu v-if="it.children?.length" :index="it.index">
            <template #title>{{ it.label }}</template>
            <el-menu-item v-for="c in it.children" :key="c.index" :index="c.index">{{ c.label }}</el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="it.index">{{ it.label }}</el-menu-item>
        </template>
      </el-menu>
    </div>
    <div class="app-user">
      <span class="company-name" :title="company">{{ company }}</span>
      <el-dropdown trigger="click" @command="onUserCmd">
        <span class="user-avatar">
          <img v-if="avatarUrl" :src="avatarUrl" alt="" />
          <span v-else>{{ initial }}</span>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-if="uploadAvatar" command="avatar">上传头像</el-dropdown-item>
            <el-dropdown-item v-if="showPassword" command="password">修改密码</el-dropdown-item>
            <el-dropdown-item command="logout">退出</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <input ref="fileRef" type="file" accept="image/png,image/jpeg" hidden @change="onFile" />
    </div>
  </header>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'
import { fetchAttachment } from '../api/file'
import { ElMessage } from 'element-plus'

const props = defineProps({
  items: { type: Array, default: () => [] },
  active: { type: String, default: '' },
  routerMode: { type: Boolean, default: true },
  showPassword: { type: Boolean, default: false },
  uploadAvatar: { type: Boolean, default: false },
  roleLabel: { type: String, default: '' },
})
const emit = defineEmits(['select', 'logout', 'password'])
const router = useRouter()

const company = ref(sessionStorage.getItem('name') || '')
const avatarId = ref(sessionStorage.getItem('avatarId') || '')
const avatarUrl = ref('')
const fileRef = ref()

const initial = computed(() => (company.value || '企').slice(0, 1))

async function loadAvatar(id) {
  if (avatarUrl.value) {
    URL.revokeObjectURL(avatarUrl.value)
    avatarUrl.value = ''
  }
  if (!id) return
  try {
    const { blob } = await fetchAttachment(id, true)
    avatarUrl.value = URL.createObjectURL(blob)
  } catch {
    avatarUrl.value = ''
  }
}

async function refreshMe() {
  try {
    const me = await api.get('/me')
    company.value = me.name || ''
    avatarId.value = me.avatarId ? String(me.avatarId) : ''
    sessionStorage.setItem('name', company.value)
    sessionStorage.setItem('avatarId', avatarId.value)
    await loadAvatar(me.avatarId)
  } catch {
    company.value = sessionStorage.getItem('name') || ''
    await loadAvatar(avatarId.value)
  }
}

function onSelect(index) {
  emit('select', index)
  if (props.routerMode && typeof index === 'string' && index.startsWith('/')) {
    router.push(index)
  }
}

function onUserCmd(cmd) {
  if (cmd === 'logout') emit('logout')
  else if (cmd === 'password') emit('password')
  else if (cmd === 'avatar') fileRef.value?.click()
}

async function onFile(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  const fd = new FormData()
  fd.append('file', file)
  const me = await api.post('/me/avatar', fd)
  sessionStorage.setItem('name', me.name || '')
  sessionStorage.setItem('avatarId', me.avatarId ? String(me.avatarId) : '')
  window.dispatchEvent(new Event('profile-changed'))
  ElMessage.success('头像已更新')
  await refreshMe()
}

function onProfileChanged() {
  refreshMe()
}

onMounted(() => {
  refreshMe()
  window.addEventListener('profile-changed', onProfileChanged)
})
onUnmounted(() => {
  window.removeEventListener('profile-changed', onProfileChanged)
  if (avatarUrl.value) URL.revokeObjectURL(avatarUrl.value)
})
</script>

<style scoped>
.user-avatar img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}
</style>
