<template>
  <el-dialog :model-value="visible" @update:model-value="$emit('update:visible', $event)" :title="title" width="720px">
    <el-form label-position="top" :model="form">
      <el-row :gutter="16">
        <el-col v-for="field in activeEditFields" :key="field.prop" :xs="24" :md="12">
          <el-form-item :label="field.label">
            <el-select v-if="field.options" v-model="form[field.prop]" clearable filterable :allow-create="field.allowCreate" default-first-option style="width: 100%">
              <el-option v-for="option in field.options" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
            <el-input-number v-else-if="field.type === 'number'" v-model="form[field.prop]" :precision="field.precision || 0" style="width: 100%" />
            <el-date-picker v-else-if="field.type === 'datetime'" v-model="form[field.prop]" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择时间" style="width: 100%" />
            <el-input v-else v-model="form[field.prop]" clearable />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button v-if="canSubmit" type="primary" :loading="saving" @click="$emit('submit')">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  visible: Boolean,
  mode: { type: String, default: 'create' },
  form: Object,
  fields: Array,
  saving: Boolean,
  canSubmit: Boolean
})

defineEmits(['update:visible', 'submit'])

const title = computed(() => props.mode === 'create' ? '新增' : '编辑')
const activeEditFields = computed(() => props.fields || [])
</script>
