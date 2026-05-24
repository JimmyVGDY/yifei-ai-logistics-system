<template>
  <div class="page-stack">
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :lg="6" v-for="item in metrics" :key="item.label">
        <section class="metric-panel">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.hint }}</small>
        </section>
      </el-col>
    </el-row>

    <section class="content-panel">
      <div class="panel-header">
        <div>
          <h3>近期运单</h3>
          <p>来自 Spring Boot 物流订单接口</p>
        </div>
        <el-button :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
      <el-table :data="orders" v-loading="loading" height="360">
        <el-table-column prop="orderNo" label="运单号" min-width="180" />
        <el-table-column prop="customerName" label="客户" min-width="180" />
        <el-table-column prop="cargoName" label="货物" min-width="140" />
        <el-table-column prop="cargoWeight" label="重量 kg" width="110" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { fetchOrders } from '../api/logistics'

const orders = ref([])
const loading = ref(false)

const metrics = computed(() => {
  const total = orders.value.length
  const created = orders.value.filter((item) => item.status === 'CREATED').length
  const transit = orders.value.filter((item) => item.status === 'IN_TRANSIT').length
  const delivered = orders.value.filter((item) => item.status === 'DELIVERED').length
  return [
    { label: '运单总数', value: total, hint: '当前列表统计' },
    { label: '待揽收', value: created, hint: 'CREATED' },
    { label: '运输中', value: transit, hint: 'IN_TRANSIT' },
    { label: '已签收', value: delivered, hint: 'DELIVERED' }
  ]
})

function statusType(status) {
  if (status === 'DELIVERED') return 'success'
  if (status === 'IN_TRANSIT') return 'warning'
  return 'info'
}

async function loadData() {
  loading.value = true
  try {
    orders.value = await fetchOrders(20)
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>
