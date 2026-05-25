<template>
  <div class="page-stack">
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :lg="4" v-for="item in metrics" :key="item.label">
        <section class="metric-panel">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.hint }}</small>
        </section>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <section class="content-panel">
          <div class="panel-header">
            <div>
              <h3>订单状态占比</h3>
              <p>按照需求书中的订单状态维度汇总</p>
            </div>
            <el-button :loading="loading" @click="loadData">
              <el-icon><Refresh /></el-icon>
            </el-button>
          </div>
          <el-table :data="summary?.statusDistribution || []" v-loading="loading" height="320">
            <el-table-column prop="status" label="状态" min-width="160">
              <template #default="{ row }">{{ statusLabel(row.status) }}</template>
            </el-table-column>
            <el-table-column prop="total" label="数量" width="120" />
          </el-table>
        </section>
      </el-col>

      <el-col :xs="24" :lg="12">
        <section class="content-panel">
          <div class="panel-header">
            <div>
              <h3>异常订单提醒</h3>
              <p>最近上报且未关闭的运输异常</p>
            </div>
          </div>
          <el-table :data="summary?.recentExceptions || []" v-loading="loading" height="320">
            <el-table-column prop="order_no" label="订单号" min-width="160" />
            <el-table-column prop="exception_type" label="异常类型" width="120" />
            <el-table-column prop="exception_status" label="状态" width="120">
              <template #default="{ row }">{{ statusLabel(row.exception_status) }}</template>
            </el-table-column>
            <el-table-column prop="report_user" label="上报人" width="120" />
          </el-table>
        </section>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <section class="content-panel">
          <div class="panel-header">
            <div>
              <h3>订单趋势</h3>
              <p>最近 7 天订单创建量</p>
            </div>
          </div>
          <el-table :data="orderTrend" v-loading="loading" height="260">
            <el-table-column prop="stat_date" label="日期" min-width="160" />
            <el-table-column prop="total" label="订单数" width="120" />
          </el-table>
        </section>
      </el-col>

      <el-col :xs="24" :lg="12">
        <section class="content-panel">
          <div class="panel-header">
            <div>
              <h3>收入趋势</h3>
              <p>最近 6 个月已收款金额</p>
            </div>
          </div>
          <el-table :data="incomeTrend" v-loading="loading" height="260">
            <el-table-column prop="stat_month" label="月份" min-width="160" />
            <el-table-column prop="total" label="收入" width="140" />
          </el-table>
        </section>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { fetchDashboardSummary, fetchIncomeTrend, fetchOrderTrend } from '../api/logistics'
import { statusLabel } from '../utils/status-labels'

const summary = ref(null)
const orderTrend = ref([])
const incomeTrend = ref([])
const loading = ref(false)

const metrics = computed(() => [
  { label: '今日订单', value: summary.value?.todayOrders || 0, hint: '当天创建' },
  { label: '已完成', value: summary.value?.completedOrders || 0, hint: 'COMPLETED / SIGNED' },
  { label: '待调度', value: summary.value?.waitDispatchOrders || 0, hint: 'WAIT_DISPATCH' },
  { label: '运输中', value: summary.value?.inTransitOrders || 0, hint: 'IN_TRANSIT' },
  { label: '异常订单', value: summary.value?.exceptionOrders || 0, hint: '未关闭异常' },
  { label: '月度收入', value: summary.value?.monthIncome || 0, hint: '已付款费用' }
])

async function loadData() {
  loading.value = true
  try {
    const [summaryData, orderTrendData, incomeTrendData] = await Promise.all([
      fetchDashboardSummary(),
      fetchOrderTrend(7),
      fetchIncomeTrend(6)
    ])
    summary.value = summaryData
    orderTrend.value = orderTrendData
    incomeTrend.value = incomeTrendData
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>
