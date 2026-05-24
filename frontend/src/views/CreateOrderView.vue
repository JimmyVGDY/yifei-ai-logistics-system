<template>
  <section class="content-panel form-panel">
    <div class="panel-header">
      <div>
        <h3>新建运单</h3>
        <p>提交后会调用后端创建接口，并触发 Redis、RabbitMQ、Sentinel、ES 链路</p>
      </div>
    </div>

    <el-form label-position="top" :model="form">
      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <el-form-item label="客户名称">
            <el-input v-model="form.customerName" placeholder="例如 Shanghai Fresh Retail Co." />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="货物名称">
            <el-input v-model="form.cargoName" placeholder="例如 Apparel Samples" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="发货地址">
            <el-input v-model="form.senderAddress" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="收货地址">
            <el-input v-model="form.receiverAddress" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="重量 kg">
            <el-input-number v-model="form.cargoWeight" :min="0.001" :precision="3" />
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

const form = reactive({
  customerName: 'Shanghai Fresh Retail Co.',
  senderAddress: 'Pudong New Area, Shanghai',
  receiverAddress: 'Chaoyang District, Beijing',
  cargoName: 'Apparel Samples',
  cargoWeight: 12.5
})

const submitting = ref(false)
const createdOrder = ref(null)

async function submit() {
  submitting.value = true
  try {
    createdOrder.value = await createOrder(form)
    ElMessage.success('运单创建成功')
  } finally {
    submitting.value = false
  }
}
</script>
