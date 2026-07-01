<template>
  <el-drawer :model-value="visible" @update:model-value="$emit('update:visible', $event)" size="72%" append-to-body>
    <template #header>
      <div class="data-drawer-header">
        <strong>{{ result?.toolName || '业务数据查询' }} · {{ result?.target || '查询结果' }}</strong>
        <span>{{ result?.rows?.length || 0 }} 条数据</span>
      </div>
    </template>
    <div class="data-drawer-summary">{{ result?.summary || '以下为本次 AI 只读查询返回的结构化数据。' }}</div>
    <el-table v-if="result?.rows?.length" :data="result.rows" border height="calc(100vh - 190px)" class="data-full-table">
      <el-table-column
        v-for="column in displayColumns"
        :key="column.prop"
        :prop="column.prop"
        :label="column.label"
        min-width="150"
        show-overflow-tooltip
      />
    </el-table>
    <el-empty v-else description="暂无结构化数据" />
    <template v-if="result?.cursorId && result?.hasMore" #footer>
      <el-button text type="primary" @click="$emit('load-more')">加载更多结果</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue'
import { displayColumns as safeDisplayColumns } from '../utils/ai-display-sanitizer.js'

const props = defineProps({
  visible: Boolean,
  result: Object,
  fieldLabel: { type: Function, default: (f) => f }
})

defineEmits(['update:visible', 'load-more'])

const displayColumns = computed(() => {
  const columns = safeDisplayColumns(props.result)
  return columns.length ? columns : []
})
</script>
