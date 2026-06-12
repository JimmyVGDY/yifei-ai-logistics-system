<template>
  <div class="ai-data-table" v-if="columns.length && rows.length">
    <div class="table-header">
      <span class="table-title">查询结果（共 {{ rows.length }} 条）</span>
      <el-pagination
        v-if="rows.length > pageSize"
        small
        background
        layout="prev, pager, next"
        :page-size="pageSize"
        :total="rows.length"
        :current-page="currentPage"
        @current-change="handlePageChange"
      />
    </div>
    <el-table
      :data="pagedRows"
      border
      stripe
      size="small"
      max-height="320"
      class="data-table"
      :show-overflow-tooltip="true"
    >
      <el-table-column
        v-for="col in columns"
        :key="col"
        :prop="col"
        :label="col"
        :min-width="colWidth(col)"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <span class="cell-value">{{ formatValue(row[col]) }}</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  columns: { type: Array, default: () => [] },
  rows: { type: Array, default: () => [] }
})

const pageSize = 15
const currentPage = ref(1)

const pagedRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return props.rows.slice(start, start + pageSize)
})

function handlePageChange(page) {
  currentPage.value = page
}

function colWidth(col) {
  // 根据列名长度估算最小列宽
  const len = (col || '').length
  if (len <= 4) return 100
  if (len <= 6) return 130
  return 160
}

function formatValue(value) {
  if (value === null || value === undefined) return '-'
  if (typeof value === 'number') return String(value)
  return String(value)
}
</script>

<style scoped>
.ai-data-table {
  margin: 10px 0;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
  background: #fafbfc;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f0f2f5;
  border-bottom: 1px solid #e4e7ed;
}

.table-title {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
}

.data-table {
  width: 100%;
  font-size: 12px;
}

/* 窄聊天框适配 */
.data-table :deep(.el-table__header th) {
  font-size: 12px;
  padding: 6px 0;
  white-space: nowrap;
}

.data-table :deep(.el-table__body td) {
  font-size: 12px;
  padding: 5px 0;
}

.cell-value {
  font-size: 12px;
  color: #303133;
}

/* 分页器适配窄空间 */
.table-header :deep(.el-pagination) {
  padding: 0;
}

.table-header :deep(.el-pagination .btn-prev),
.table-header :deep(.el-pagination .btn-next) {
  min-width: 24px;
  height: 24px;
}

.table-header :deep(.el-pager li) {
  min-width: 24px;
  height: 24px;
  line-height: 24px;
  font-size: 12px;
}
</style>
