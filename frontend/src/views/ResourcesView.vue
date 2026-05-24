<template>
  <div class="page-stack">
    <section class="content-panel">
      <div class="panel-header">
        <div>
          <h3>中间件状态</h3>
          <p>Nacos、Sentinel、ES、Redis、RabbitMQ、MySQL 基础配置</p>
        </div>
        <el-button :loading="loading" @click="loadStatus">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
      <el-descriptions :column="1" border v-if="status">
        <el-descriptions-item label="应用名">{{ status.details.applicationName }}</el-descriptions-item>
        <el-descriptions-item label="Nacos">{{ status.details.nacosServerAddr }}</el-descriptions-item>
        <el-descriptions-item label="Sentinel">{{ status.details.sentinelDashboard }}</el-descriptions-item>
        <el-descriptions-item label="Elasticsearch">{{ status.details.elasticsearchUris }}</el-descriptions-item>
        <el-descriptions-item label="Redis">{{ status.details.redisConnectionFactory }}</el-descriptions-item>
        <el-descriptions-item label="RabbitMQ">
          {{ status.details.rabbitmqHost }}:{{ status.details.rabbitmqPort }}
        </el-descriptions-item>
      </el-descriptions>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { fetchInfrastructureStatus } from '../api/logistics'

const loading = ref(false)
const status = ref(null)

async function loadStatus() {
  loading.value = true
  try {
    status.value = await fetchInfrastructureStatus()
  } finally {
    loading.value = false
  }
}

onMounted(loadStatus)
</script>
