<template>
  <section class="content-panel">
    <div class="panel-header">
      <div>
        <h3>{{ meta.title }}</h3>
        <p>{{ meta.description }}</p>
      </div>
      <div class="toolbar">
        <el-input-number v-model="limit" :min="1" :max="100" />
        <el-button v-if="route.meta.module === 'exceptions'" type="warning" @click="exceptionDialogVisible = true">
          上报异常
        </el-button>
        <el-button v-if="route.meta.module === 'fees'" type="success" @click="feeDialogVisible = true">
          生成费用
        </el-button>
        <el-upload
          v-if="route.meta.module === 'customers'"
          :show-file-list="false"
          :auto-upload="false"
          accept=".xlsx"
          @change="handleCustomerImport"
        >
          <el-button>导入客户</el-button>
        </el-upload>
        <el-button @click="downloadExcel">
          导出 Excel
        </el-button>
        <el-button type="primary" :loading="loading" @click="loadData">
          <el-icon><Search /></el-icon>
          查询
        </el-button>
      </div>
    </div>

    <el-table :data="records" v-loading="loading" height="640">
      <el-table-column
        v-for="column in meta.columns"
        :key="column.prop"
        :prop="column.prop"
        :label="column.label"
        :min-width="column.minWidth || 120"
      />
      <el-table-column v-if="route.meta.module === 'exceptions'" label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleExceptionRow(row)">关闭</el-button>
        </template>
      </el-table-column>
      <el-table-column v-if="route.meta.module === 'fees'" label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handlePayRow(row)">收款</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="exceptionDialogVisible" title="上报运输异常" width="520px">
      <el-form label-position="top" :model="exceptionForm">
        <el-form-item label="订单号">
          <el-input v-model="exceptionForm.orderNo" placeholder="请输入订单号" />
        </el-form-item>
        <el-form-item label="异常类型">
          <el-select v-model="exceptionForm.exceptionType" placeholder="请选择异常类型">
            <el-option label="车辆故障" value="车辆故障" />
            <el-option label="交通事故" value="交通事故" />
            <el-option label="货物破损" value="货物破损" />
            <el-option label="客户拒收" value="客户拒收" />
            <el-option label="地址错误" value="地址错误" />
            <el-option label="天气原因" value="天气原因" />
            <el-option label="其他异常" value="其他异常" />
          </el-select>
        </el-form-item>
        <el-form-item label="异常描述">
          <el-input v-model="exceptionForm.exceptionDesc" type="textarea" :rows="3" placeholder="请输入异常描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exceptionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitException">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="feeDialogVisible" title="生成订单费用" width="420px">
      <el-form label-position="top" :model="feeForm">
        <el-form-item label="订单号">
          <el-input v-model="feeForm.orderNo" placeholder="请输入订单号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitFee">生成</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  exportModuleExcel,
  fetchModuleRecords,
  generateFee,
  handleException,
  importCustomers,
  markFeePaid,
  reportException
} from '../api/logistics'

const route = useRoute()
const limit = ref(20)
const records = ref([])
const loading = ref(false)
const saving = ref(false)
const exceptionDialogVisible = ref(false)
const feeDialogVisible = ref(false)
const exceptionForm = reactive({ orderNo: '', exceptionType: '', exceptionDesc: '' })
const feeForm = reactive({ orderNo: '' })

const moduleMetas = {
  customers: { title: '客户管理', description: '维护寄件客户信息和客户状态', columns: columns('customer_code:客户编码,customer_name:客户名称,contact_name:联系人,contact_phone:联系电话,city:城市,status:状态') },
  waybills: { title: '运单中心', description: '订单创建后生成的运单及运输状态', columns: columns('waybill_no:运单号,order_no:订单号,start_site:起始网点,target_site:目的网点,current_location:当前位置,transport_status:运输状态') },
  dispatches: { title: '调度管理', description: '分配司机、车辆并创建运输任务', columns: columns('order_no:订单号,waybill_no:运单号,driver_name:司机,vehicle_no:车辆,start_site:起始网点,target_site:目的网点,dispatch_status:调度状态') },
  tasks: { title: '运输任务', description: '司机接单、揽收、运输、签收和异常上报', columns: columns('task_no:任务号,order_no:订单号,driver_name:司机,vehicle_no:车辆,task_status:任务状态,create_time:创建时间') },
  tracks: { title: '物流轨迹', description: '按时间线记录订单运输轨迹', columns: columns('order_no:订单号,waybill_no:运单号,current_status:当前状态,current_location:当前位置,operator_name:操作人,operation_desc:操作说明,operation_time:操作时间') },
  drivers: { title: '司机管理', description: '维护司机证件、准驾车型和状态', columns: columns('driver_code:司机编码,driver_name:司机姓名,phone:手机号,license_no:驾驶证号,license_type:准驾车型,status:状态') },
  vehicles: { title: '车辆管理', description: '维护车牌、车型、载重、容量和车辆状态', columns: columns('vehicle_no:车牌号,vehicle_type:车辆类型,load_capacity_kg:载重量,current_city:当前城市,status:状态') },
  exceptions: { title: '异常管理', description: '运输过程异常上报、处理和查询', columns: columns('order_no:订单号,exception_type:异常类型,exception_desc:异常描述,exception_status:异常状态,report_user:上报人,report_time:上报时间') },
  fees: { title: '费用结算', description: '订单费用计算、账单和付款状态', columns: columns('order_no:订单号,base_fee:基础运费,weight_fee:重量费用,distance_fee:距离费用,payable_fee:应收金额,actual_fee:实收金额,payment_status:付款状态') },
  users: { title: '用户管理', description: '后台用户、状态和角色分配', columns: columns('username:登录账号,real_name:真实姓名,mobile:手机号,email:邮箱,role_name:角色,status:状态') },
  roles: { title: '角色管理', description: '系统管理员、客服、调度、司机、财务和客户等角色', columns: columns('role_code:角色编码,role_name:角色名称,status:状态,create_time:创建时间') },
  operationLogs: { title: '操作日志', description: '记录敏感接口和物流业务写操作', columns: columns('username:操作人,operation:操作内容,request_uri:请求地址,request_method:方法,operation_status:状态,operation_time:操作时间') },
  files: { title: '上传文件', description: '查看上传到本地的业务附件记录', columns: columns('original_name:原文件名,relative_path:保存路径,file_size:大小,content_type:类型,upload_user:上传人,upload_time:上传时间') }
}

const meta = computed(() => moduleMetas[route.meta.module] || moduleMetas.customers)

function columns(definition) {
  return definition.split(',').map((item) => {
    const [prop, label] = item.split(':')
    return { prop, label, minWidth: prop.includes('desc') || prop.includes('address') ? 220 : 130 }
  })
}

async function loadData() {
  loading.value = true
  try {
    records.value = await fetchModuleRecords(route.meta.module, limit.value)
  } finally {
    loading.value = false
  }
}

async function downloadExcel() {
  const blob = await exportModuleExcel(route.meta.module, limit.value)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${route.meta.module}-export.xlsx`
  link.click()
  URL.revokeObjectURL(url)
}

async function handleCustomerImport(uploadFile) {
  saving.value = true
  try {
    const result = await importCustomers(uploadFile.raw)
    ElMessage.success(`导入完成：${result.imported || 0} 条`)
    await loadData()
  } finally {
    saving.value = false
  }
}

async function submitException() {
  saving.value = true
  try {
    await reportException({ ...exceptionForm })
    ElMessage.success('异常已上报')
    exceptionDialogVisible.value = false
    Object.assign(exceptionForm, { orderNo: '', exceptionType: '', exceptionDesc: '' })
    await loadData()
  } finally {
    saving.value = false
  }
}

async function handleExceptionRow(row) {
  await ElMessageBox.confirm('确认将该异常记录关闭吗？', '处理异常')
  await handleException(row.id, { exceptionStatus: 'CLOSED' })
  ElMessage.success('异常已关闭')
  await loadData()
}

async function submitFee() {
  saving.value = true
  try {
    await generateFee(feeForm.orderNo)
    ElMessage.success('费用已生成')
    feeDialogVisible.value = false
    feeForm.orderNo = ''
    await loadData()
  } finally {
    saving.value = false
  }
}

async function handlePayRow(row) {
  await ElMessageBox.confirm('确认将该费用标记为已付款吗？', '费用收款')
  await markFeePaid(row.id)
  ElMessage.success('已标记付款')
  await loadData()
}

watch(() => route.meta.module, loadData)
onMounted(loadData)
</script>
