<template>
  <el-drawer :model-value="visible" @update:model-value="$emit('update:visible', $event)" title="操作日志详情" size="620px">
    <div v-if="log" class="log-detail">
      <section v-for="section in sections" :key="section.title" class="log-detail-section">
        <h4>{{ section.title }}</h4>
        <div v-for="item in section.items" :key="item.prop" class="log-detail-row">
          <span class="log-detail-label">{{ item.label }}</span>
          <span class="log-detail-value">{{ fullText(item.prop, log[item.prop]) || '-' }}</span>
          <el-button
            v-if="log[item.prop] !== undefined && log[item.prop] !== null && log[item.prop] !== ''"
            link
            type="primary"
            @click="copyValue(fullText(item.prop, log[item.prop]))"
          >
            复制
          </el-button>
        </div>
      </section>
    </div>
  </el-drawer>
</template>

<script setup>
import { ElMessage } from 'element-plus'

const props = defineProps({
  visible: Boolean,
  log: Object,
  sections: { type: Array, default: () => [] },
  formatCell: { type: Function, default: (prop, value) => value }
})

defineEmits(['update:visible'])

function fullText(prop, value) {
  const text = props.formatCell(prop, value)
  return text === null || text === undefined ? '' : String(text)
}

async function copyValue(value) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(value)
  } else {
    const input = document.createElement('textarea')
    input.value = value
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    document.body.removeChild(input)
  }
  ElMessage.success('已复制')
}
</script>

<style scoped>
.log-detail { display: grid; gap: 18px; }
.log-detail-section { border-bottom: 1px solid #edf0f5; padding-bottom: 14px; }
.log-detail-section h4 { color: #303133; font-size: 15px; margin: 0 0 12px; }
.log-detail-row { align-items: start; display: grid; gap: 10px; grid-template-columns: 110px minmax(0, 1fr) 44px; line-height: 1.7; padding: 4px 0; }
.log-detail-label { color: #909399; }
.log-detail-value { color: #303133; white-space: pre-wrap; word-break: break-all; }
</style>
