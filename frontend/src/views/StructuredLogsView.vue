<template>
  <section class="content-panel">
    <div class="panel-header">
      <div>
        <h3>结构化日志</h3>
        <p>按级别、关键词、链路、用户、模块和时间范围检索 JSON 文件日志</p>
      </div>
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="关键词" clearable style="width: 180px" />
        <el-select v-model="query.level" placeholder="级别" clearable style="width: 120px">
          <el-option label="ERROR" value="ERROR" />
          <el-option label="WARN" value="WARN" />
          <el-option label="INFO" value="INFO" />
          <el-option label="DEBUG" value="DEBUG" />
        </el-select>
        <el-input v-model="query.traceId" placeholder="Trace ID" clearable style="width: 160px" />
        <el-input v-model="query.userCode" placeholder="用户编号" clearable style="width: 130px" />
        <el-input v-model="query.roleCode" placeholder="角色编号" clearable style="width: 130px" />
        <el-input v-model="query.module" placeholder="模块" clearable style="width: 120px" />
        <el-input v-model="query.operation" placeholder="操作" clearable style="width: 140px" />
        <el-date-picker
          v-model="timeRange"
          type="datetimerange"
          value-format="YYYY-MM-DD HH:mm:ss"
          range-separator="-"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          style="width: 330px"
        />
        <el-button type="primary" :loading="loading" @click="search">查询</el-button>
      </div>
    </div>

    <el-table :data="records" v-loading="loading" height="640">
      <el-table-column prop="timestamp" label="时间" width="180" />
      <el-table-column prop="level" label="级别" width="90">
        <template #default="{ row }">
          <el-tag :type="levelType(row.level)" size="small">{{ row.level }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="module" label="模块" width="120" />
      <el-table-column prop="operation" label="操作" width="150" />
      <el-table-column prop="result" label="结果" width="100" />
      <el-table-column prop="userCode" label="用户编号" width="130" />
      <el-table-column prop="roleCode" label="角色编号" width="120" />
      <el-table-column prop="usernameMasked" label="用户" width="120" />
      <el-table-column prop="traceId" label="Trace ID" width="170" show-overflow-tooltip />
      <el-table-column prop="logger" label="Logger" min-width="220" show-overflow-tooltip />
      <el-table-column prop="message" label="消息" min-width="280" show-overflow-tooltip />
      <el-table-column prop="costMs" label="耗时ms" width="100" />
      <el-table-column prop="sourceFile" label="文件" width="210" show-overflow-tooltip />
      <el-table-column label="详情" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>

    <ModulePagination
      :page="page"
      :page-size="pageSize"
      :total="total"
      @page-change="handlePageChange"
      @page-size-change="handlePageSizeChange"
    />

    <el-dialog v-model="detailVisible" title="日志详情" width="760px">
      <pre class="log-detail">{{ detailText }}</pre>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { fetchStructuredLogs } from '../api/logistics'
import ModulePagination from '../components/ModulePagination.vue'

const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const records = ref([])
const loading = ref(false)
const timeRange = ref([])
const detailVisible = ref(false)
const detailRow = ref({})
const query = reactive({
  keyword: '',
  level: '',
  traceId: '',
  userCode: '',
  roleCode: '',
  module: '',
  operation: ''
})

const detailText = computed(() => JSON.stringify(detailRow.value, null, 2))

async function loadData() {
  loading.value = true
  try {
    const result = await fetchStructuredLogs({
      page: page.value,
      pageSize: pageSize.value,
      keyword: query.keyword || undefined,
      level: query.level || undefined,
      traceId: query.traceId || undefined,
      userCode: query.userCode || undefined,
      roleCode: query.roleCode || undefined,
      module: query.module || undefined,
      operation: query.operation || undefined,
      startTime: timeRange.value?.[0],
      endTime: timeRange.value?.[1]
    })
    records.value = result.records || []
    total.value = result.total || 0
    page.value = result.page || page.value
    pageSize.value = result.pageSize || pageSize.value
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  loadData()
}

function handlePageChange(nextPage) {
  page.value = nextPage
  loadData()
}

function handlePageSizeChange(nextPageSize) {
  pageSize.value = nextPageSize
  page.value = 1
  loadData()
}

function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

function levelType(level) {
  if (level === 'ERROR') {
    return 'danger'
  }
  if (level === 'WARN') {
    return 'warning'
  }
  if (level === 'INFO') {
    return 'success'
  }
  return 'info'
}

onMounted(loadData)
</script>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

.log-detail {
  max-height: 520px;
  overflow: auto;
  padding: 12px;
  border: 1px solid #d8dee9;
  border-radius: 6px;
  background: #0f172a;
  color: #e5e7eb;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
}
</style>
