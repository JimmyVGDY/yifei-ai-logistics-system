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

    <section class="content-panel">
      <div class="panel-header">
        <div>
          <h3>业务文件上传</h3>
          <p>用于签收凭证、异常附件、结算资料等 v2.0 文件能力联调</p>
        </div>
      </div>
      <el-upload
        drag
        :auto-upload="false"
        :show-file-list="true"
        @change="handleUpload"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到这里，或点击选择文件</div>
      </el-upload>
      <el-alert v-if="uploadedFile" type="success" show-icon class="result-alert">
        <template #title>上传完成：{{ uploadedFile.relativePath }}</template>
      </el-alert>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchInfrastructureStatus, uploadBusinessFile } from '../api/logistics'

const loading = ref(false)
const status = ref(null)
const uploading = ref(false)
const uploadedFile = ref(null)

async function loadStatus() {
  // 资源中心只展示当前后端可见配置，不在前端主动探测中间件端口。
  loading.value = true
  try {
    status.value = await fetchInfrastructureStatus()
  } finally {
    loading.value = false
  }
}

async function handleUpload(uploadFile) {
  if (uploading.value) {
    // Element Plus 可能连续触发 change，上传中直接忽略重复事件。
    return
  }
  uploading.value = true
  try {
    // 文件校验和落盘路径以后端为准，前端只展示返回的相对路径。
    uploadedFile.value = await uploadBusinessFile(uploadFile.raw)
    ElMessage.success('文件上传成功')
  } finally {
    uploading.value = false
  }
}

onMounted(loadStatus)
</script>
