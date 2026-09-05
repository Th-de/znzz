<script setup>
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getOrder } from '../../api/order'

const route = useRoute()
const router = useRouter()
onMounted(async () => {
  try {
    const o = await getOrder(route.params.id)
    if (o?.demandId) {
      router.replace('/buyer/demand/' + o.demandId)
      return
    }
  } catch { /* 回需求列表 */ }
  router.replace('/buyer/home')
})
</script>

<template>
  <div />
</template>
