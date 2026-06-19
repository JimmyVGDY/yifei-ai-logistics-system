<template>
  <el-dialog :model-value="visible" @update:model-value="$emit('update:visible', $event)" title="新增客户账号" width="760px">
    <el-form label-position="top" :model="form">
      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <el-form-item label="账号类型">
            <el-radio-group v-model="form.customerSubjectType">
              <el-radio-button label="PERSONAL">个人账号</el-radio-button>
              <el-radio-button label="ENTERPRISE">企业账号</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item :label="form.customerSubjectType === 'ENTERPRISE' ? '公司名称' : '客户名称'">
            <el-select v-model="form.customerName" clearable filterable allow-create default-first-option style="width: 100%">
              <el-option v-for="option in customerOptions" :key="option.value" :label="option.label" :value="option.rawName || option.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="登录账号"><el-input v-model="form.username" clearable /></el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="姓名"><el-input v-model="form.realName" clearable /></el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="手机号"><el-input v-model="form.mobile" maxlength="11" clearable /></el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="邮箱"><el-input v-model="form.email" clearable /></el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password clearable /></el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="$emit('submit')">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
defineProps({
  visible: Boolean,
  form: Object,
  customerOptions: { type: Array, default: () => [] },
  saving: Boolean
})

defineEmits(['update:visible', 'submit'])
</script>
