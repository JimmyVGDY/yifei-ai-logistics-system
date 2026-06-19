<template>
  <el-dialog :model-value="visible" @update:model-value="$emit('update:visible', $event)" title="上报运输异常" width="520px">
    <el-form label-position="top" :model="form">
      <el-form-item label="订单号">
        <el-select v-model="form.orderNo" clearable filterable style="width: 100%">
          <el-option v-for="option in orderOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="异常类型">
        <el-select v-model="form.exceptionType" clearable filterable style="width: 100%">
          <el-option v-for="option in exceptionTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="异常描述"><el-input v-model="form.exceptionDesc" type="textarea" :rows="3" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button v-if="canSubmit" type="primary" :loading="saving" @click="$emit('submit')">提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
defineProps({
  visible: Boolean,
  form: Object,
  orderOptions: { type: Array, default: () => [] },
  exceptionTypeOptions: { type: Array, default: () => [] },
  saving: Boolean,
  canSubmit: Boolean
})

defineEmits(['update:visible', 'submit'])
</script>
