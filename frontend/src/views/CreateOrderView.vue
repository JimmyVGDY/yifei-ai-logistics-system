<template>
  <section class="content-panel form-panel">
    <div class="panel-header">
      <div>
        <h3>新建运单</h3>
        <p>提交后会调用后端创建接口，并触发 Redis、RabbitMQ、Sentinel、ES 链路</p>
      </div>
    </div>

    <el-form ref="formRef" label-position="top" :model="form" :rules="rules">
      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <el-form-item label="客户名称" prop="customerName">
            <el-input v-model="form.customerName" placeholder="例如 上海鲜达商贸有限公司" clearable />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="货物名称" prop="cargoName">
            <el-input v-model="form.cargoName" placeholder="例如 服装样品" clearable />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="发货地址" prop="senderAddress">
            <el-input v-model="form.senderAddress" placeholder="例如 上海市浦东新区张江镇" clearable />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="收货地址" prop="receiverAddress">
            <el-input v-model="form.receiverAddress" placeholder="例如 北京市朝阳区望京街道" clearable />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="重量 kg" prop="cargoWeight">
            <el-input-number v-model="form.cargoWeight" :min="0.001" :precision="3" placeholder="请输入重量" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-button type="primary" size="large" :loading="submitting" @click="submit">
        创建运单
      </el-button>
    </el-form>

    <el-alert v-if="createdOrder" type="success" show-icon class="result-alert">
      <template #title>创建成功：{{ createdOrder.orderNo }}</template>
    </el-alert>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createOrder } from '../api/logistics'

const formRef = ref(null)

const form = reactive({
  customerName: '',
  senderAddress: '',
  receiverAddress: '',
  cargoName: '',
  cargoWeight: null
})

const rules = {
  customerName: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
  cargoName: [{ required: true, message: '请输入货物名称', trigger: 'blur' }],
  senderAddress: [{ required: true, message: '请输入发货地址', trigger: 'blur' }],
  receiverAddress: [{ required: true, message: '请输入收货地址', trigger: 'blur' }],
  cargoWeight: [{ required: true, message: '请输入重量', trigger: 'change' }]
}

const submitting = ref(false)
const createdOrder = ref(null)

async function submit() {
  await formRef.value.validate()
  submitting.value = true
  try {
    createdOrder.value = await createOrder({ ...form })
    ElMessage.success('运单创建成功')
  } finally {
    submitting.value = false
  }
}
</script>
