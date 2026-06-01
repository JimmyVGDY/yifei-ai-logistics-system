<template>
  <div class="toolbar">
    <el-input :model-value="keyword" placeholder="关键词模糊查询" clearable style="width: 180px" @update:model-value="$emit('update:keyword', $event)" />
    <el-date-picker
      :model-value="timeRange"
      type="datetimerange"
      start-placeholder="开始时间"
      end-placeholder="结束时间"
      value-format="YYYY-MM-DD HH:mm:ss"
      style="width: 360px"
      @update:model-value="$emit('update:timeRange', $event || [])"
    />
    <el-input-number :model-value="pageSize" :min="1" :max="100" @update:model-value="$emit('update:pageSize', $event)" />
    <el-button v-if="canCreate" type="primary" @click="$emit('create')">新增</el-button>
    <el-button v-if="canReportException" type="warning" @click="$emit('report-exception')">上报异常</el-button>
    <el-button v-if="canGenerateFee" type="success" @click="$emit('generate-fee')">生成费用</el-button>
    <el-upload v-if="canImportCustomer" :show-file-list="false" :auto-upload="false" accept=".xlsx" @change="$emit('import-customer', $event)">
      <el-button>导入客户</el-button>
    </el-upload>
    <el-button v-if="canExport" @click="$emit('export')">导出 Excel</el-button>
    <el-button v-if="canQuery" type="primary" :loading="loading" @click="$emit('search')">
      <el-icon><Search /></el-icon>
      查询
    </el-button>
  </div>
</template>

<script setup>
import { Search } from '@element-plus/icons-vue'

defineProps({
  keyword: { type: String, default: '' },
  timeRange: { type: Array, default: () => [] },
  pageSize: { type: Number, default: 20 },
  loading: { type: Boolean, default: false },
  canCreate: { type: Boolean, default: false },
  canReportException: { type: Boolean, default: false },
  canGenerateFee: { type: Boolean, default: false },
  canImportCustomer: { type: Boolean, default: false },
  canExport: { type: Boolean, default: false },
  canQuery: { type: Boolean, default: false }
})

defineEmits([
  'update:keyword',
  'update:timeRange',
  'update:pageSize',
  'create',
  'report-exception',
  'generate-fee',
  'import-customer',
  'export',
  'search'
])
</script>
