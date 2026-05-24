<template>
  <section class="content-panel">
    <div class="panel-header">
      <div>
        <h3>运单列表</h3>
        <p>查询最近创建的物流订单</p>
      </div>
      <div class="toolbar">
        <el-input-number v-model="limit" :min="1" :max="100" />
        <el-button type="primary" :loading="loading" @click="loadData">
          <el-icon><Search /></el-icon>
          查询
        </el-button>
      </div>
    </div>

    <el-table :data="orders" v-loading="loading" height="620">
      <el-table-column prop="orderNo" label="运单号" min-width="190" fixed />
      <el-table-column prop="customerName" label="客户" min-width="190" />
      <el-table-column prop="senderAddress" label="发货地址" min-width="220" />
      <el-table-column prop="receiverAddress" label="收货地址" min-width="220" />
      <el-table-column prop="cargoName" label="货物" min-width="150" />
      <el-table-column prop="cargoWeight" label="重量 kg" width="110" />
      <el-table-column prop="status" label="状态" width="130">
        <template #default="{ row }">
          <el-tag>{{ row.status }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { fetchOrders } from '../api/logistics'

const limit = ref(20)
const orders = ref([])
const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    orders.value = await fetchOrders(limit.value)
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>
