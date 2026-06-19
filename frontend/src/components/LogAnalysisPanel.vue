<template>
  <aside v-if="visible" class="log-panel">
    <div class="panel-header">
      <div>
        <h3>日志排障</h3>
        <p>按链路标识定位异常</p>
      </div>
      <el-button type="primary" size="small" :icon="Search" :loading="loading" @click="$emit('analyze')">分析</el-button>
    </div>

    <el-scrollbar class="log-body">
    <el-form :model="form" label-width="82px" class="log-form">
      <el-form-item label="Trace ID">
        <el-input v-model="form.traceId" clearable />
      </el-form-item>
      <el-form-item label="操作ID">
        <el-input v-model="form.operationId" clearable />
      </el-form-item>
      <el-form-item label="会话ID">
        <el-input v-model="form.loginSessionId" clearable />
      </el-form-item>
      <el-form-item label="用户编号">
        <el-input v-model="form.userId" clearable />
      </el-form-item>
      <el-form-item label="请求地址">
        <el-input v-model="form.uri" clearable />
      </el-form-item>
    </el-form>

    <div v-if="loading" class="log-loading">
      <span>正在分析日志链路</span><i></i><i></i><i></i>
    </div>

    <div v-if="result" class="analysis-result">
      <h4>{{ result.summary }}</h4>
      <el-alert v-for="risk in result.riskPoints" :key="risk" :title="risk" type="warning" show-icon :closable="false" />
      <el-timeline>
        <el-timeline-item v-for="(item, index) in result.timeline" :key="index" :timestamp="item.time">
          <strong>{{ item.operation || item.uri }}</strong>
          <p>{{ item.method }} {{ item.uri }}</p>
          <el-tag size="small" :type="item.status === 'SUCCESS' ? 'success' : 'danger'">{{ item.status || '-' }}</el-tag>
          <span class="cost">{{ item.costMs }}ms</span>
          <p v-if="item.errorMessage" class="error-text">{{ item.errorMessage }}</p>
        </el-timeline-item>
      </el-timeline>
    </div>
    </el-scrollbar>
  </aside>
</template>

<script setup>
import { Search } from '@element-plus/icons-vue'

defineProps({
  visible: Boolean,
  form: Object,
  loading: Boolean,
  result: Object
})

defineEmits(['analyze'])
</script>
