<template>
  <section class="content-panel">
    <div class="panel-header">
      <div>
        <h3>{{ meta.title }}</h3>
        <p>{{ meta.description }}</p>
      </div>
      <ModuleToolbar
        v-model:keyword="keyword"
        v-model:time-range="timeRange"
        v-model:page-size="limit"
        :loading="loading"
        :can-create="canCreate"
        :can-report-exception="canReportException"
        :can-generate-fee="canGenerateFee"
        :can-import-customer="canImportCustomer"
        :can-export="canExport"
        :can-query="canQuery"
        @create="openCreateDialog"
        @report-exception="exceptionDialogVisible = true"
        @generate-fee="feeDialogVisible = true"
        @import-customer="handleCustomerImport"
        @export="downloadExcel"
        @search="loadData"
      />
    </div>

    <el-table :data="records" v-loading="loading" height="640">
      <el-table-column v-for="column in meta.columns" :key="column.prop" :prop="column.prop" :label="column.label" :min-width="column.minWidth || 120">
        <template #default="{ row }">{{ formatCell(column.prop, row[column.prop]) }}</template>
      </el-table-column>
      <el-table-column v-if="showCrudColumn" label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canUpdate" link type="primary" @click="openEditDialog(row)">编辑</el-button>
          <el-button v-if="canDelete" link type="danger" @click="deleteRow(row)">删除</el-button>
        </template>
      </el-table-column>
      <el-table-column v-if="canHandleException" label="异常操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button v-if="exceptionStatus(row) === 'WAIT_HANDLE'" link type="warning" @click="handleExceptionAction(row, 'PROCESSING')">开始处理</el-button>
          <el-button v-else-if="exceptionStatus(row) === 'PROCESSING'" link type="success" @click="handleExceptionAction(row, 'CLOSED')">处理完成</el-button>
          <el-tag v-else type="success" size="small">已处理</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canPayFee" label="收款" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handlePayRow(row)">收款</el-button>
        </template>
      </el-table-column>
    </el-table>

    <ModulePagination
      :page="page"
      :page-size="limit"
      :total="total"
      @page-change="handlePageChange"
      @page-size-change="handlePageSizeChange"
    />

    <el-dialog v-model="crudDialogVisible" :title="crudMode === 'create' ? `新增${meta.title}` : `编辑${meta.title}`" width="720px">
      <el-form label-position="top" :model="crudForm">
        <el-row :gutter="16">
          <el-col v-for="field in activeEditFields" :key="field.prop" :xs="24" :md="12">
            <el-form-item :label="field.label">
              <el-select v-if="field.options" v-model="crudForm[field.prop]" clearable filterable :allow-create="field.allowCreate" default-first-option style="width: 100%">
                <el-option v-for="option in field.options" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
              <el-input-number v-else-if="field.type === 'number'" v-model="crudForm[field.prop]" :precision="field.precision || 0" style="width: 100%" />
              <el-date-picker v-else-if="field.type === 'datetime'" v-model="crudForm[field.prop]" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择时间" style="width: 100%" />
              <el-input v-else v-model="crudForm[field.prop]" clearable />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="crudDialogVisible = false">取消</el-button>
        <el-button v-if="canSubmitCrud" type="primary" :loading="saving" @click="submitCrud">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="exceptionDialogVisible" title="上报运输异常" width="520px">
      <el-form label-position="top" :model="exceptionForm">
        <el-form-item label="订单号">
          <el-select v-model="exceptionForm.orderNo" clearable filterable style="width: 100%">
            <el-option v-for="option in exceptionOrderOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="异常类型">
          <el-select v-model="exceptionForm.exceptionType" clearable filterable style="width: 100%">
            <el-option v-for="option in fieldOptionGroups.exceptionType" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="异常描述"><el-input v-model="exceptionForm.exceptionDesc" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exceptionDialogVisible = false">取消</el-button>
        <el-button v-if="canReportException" type="primary" :loading="saving" @click="submitException">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="feeDialogVisible" title="生成订单费用" width="420px">
      <el-form label-position="top" :model="feeForm">
        <el-form-item label="订单号">
          <el-select v-model="feeForm.orderNo" clearable filterable style="width: 100%">
            <el-option v-for="option in exceptionOrderOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feeDialogVisible = false">取消</el-button>
        <el-button v-if="canGenerateFee" type="primary" :loading="saving" @click="submitFee">生成</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createModuleRecord,
  createOrder,
  deleteModuleRecord,
  exportModuleExcel,
  fetchModuleRecords,
  generateFee,
  handleException,
  importCustomers,
  markFeePaid,
  reportException,
  updateModuleRecord
} from '../api/logistics'
import { formatDateTime, statusLabel } from '../utils/status-labels'
import { hasPermission } from '../stores/auth-store'
import ModulePagination from '../components/ModulePagination.vue'
import ModuleToolbar from '../components/ModuleToolbar.vue'

const route = useRoute()
const page = ref(1)
const limit = ref(20)
const total = ref(0)
const keyword = ref('')
const timeRange = ref([])
const records = ref([])
const loading = ref(false)
const saving = ref(false)
const exceptionDialogVisible = ref(false)
const feeDialogVisible = ref(false)
const crudDialogVisible = ref(false)
const crudMode = ref('create')
const editingId = ref(null)
const crudForm = reactive({})
const exceptionForm = reactive({ orderNo: '', exceptionType: '', exceptionDesc: '' })
const feeForm = reactive({ orderNo: '' })
const relationOptions = reactive({
  roles: [],
  orders: [],
  waybills: [],
  dispatches: [],
  drivers: [],
  vehicles: [],
  tasks: [],
  orderCustomers: []
})
const relationRows = reactive({
  orders: []
})

const statusOptions = {
  numeric: options('1:启用,0:停用'),
  common: options('ACTIVE:启用,DISABLED:停用,PAUSED:暂停'),
  order: options('CREATED:已创建,WAIT_DISPATCH:待调度,DISPATCHED:已调度,PICKED_UP:已揽收,IN_TRANSIT:运输中,DELIVERING:派送中,DELIVERED:已送达,SIGNED:已签收,COMPLETED:已完成,CANCELLED:已取消,EXCEPTION:异常'),
  transport: options('WAIT_DISPATCH:待调度,DISPATCHED:已调度,IN_TRANSIT:运输中,ARRIVED:已到达,DELIVERED:已送达,SIGNED:已签收,EXCEPTION:异常'),
  dispatch: options('ASSIGNED:已分配,WAIT_DISPATCH:待调度,DISPATCHED:已调度,PROCESSING:处理中,FINISHED:已完成,CANCELLED:已取消'),
  task: options('ASSIGNED:已分配,PICKED_UP:已揽收,TRANSPORTING:运输中,DELIVERING:派送中,SIGNED:已签收,FINISHED:已完成,EXCEPTION:异常'),
  driver: options('AVAILABLE:空闲,ON_ROUTE:运输中,RESTING:休息中,DISABLED:停用'),
  vehicle: options('AVAILABLE:空闲,ON_ROUTE:运输中,MAINTENANCE:维修中,DISABLED:停用'),
  exception: options('WAIT_HANDLE:待处理,PROCESSING:处理中,CLOSED:已关闭'),
  fee: options('UNPAID:未付款,PART_PAID:部分付款,PAID:已付款,REFUNDED:已退款')
}

const fieldOptionGroups = {
  licenseType: options('A1:A1,A2:A2,B1:B1,B2:B2,C1:C1,C2:C2'),
  vehicleType: options('冷链厢式货车:冷链厢式货车,重型半挂货车:重型半挂货车,城市配送面包车:城市配送面包车,9.6米厢式货车:9.6米厢式货车'),
  exceptionType: options('地址错误:地址错误,货损:货损,延误:延误,客户拒收:客户拒收,车辆故障:车辆故障,其他:其他'),
  province: options('北京市:北京市,上海市:上海市,广东省:广东省,浙江省:浙江省,江苏省:江苏省,四川省:四川省,湖北省:湖北省'),
  city: options('北京:北京,上海:上海,广州:广州,深圳:深圳,杭州:杭州,南京:南京,成都:成都,武汉:武汉')
}

const relationFieldSources = {
  users: { role_id: 'roles', customer_id: 'orderCustomers' },
  waybills: { order_id: 'orders' },
  dispatches: { order_id: 'orders', waybill_id: 'waybills', driver_id: 'drivers', vehicle_id: 'vehicles' },
  tasks: { order_id: 'orders', waybill_id: 'waybills', dispatch_id: 'dispatches', driver_id: 'drivers', vehicle_id: 'vehicles' },
  tracks: { order_id: 'orders', waybill_id: 'waybills' },
  exceptions: { order_id: 'orders', task_id: 'tasks' },
  fees: { order_id: 'orders' }
}

const moduleMetas = {
  orders: moduleMeta('orders', '运单管理', '统一维护订单、调度前状态和业务下单入口', 'order_no:订单号,customer_name:客户名称,sender_address:发货地址,receiver_address:收货地址,cargo_name:货物名称,cargo_weight:重量,status:状态,created_at:创建时间,updated_at:更新时间', 'customerName:客户名称,senderAddress:发货地址,receiverAddress:收货地址,cargoName:货物名称,cargoWeight:重量:number:3'),
  customers: moduleMeta('customers', '客户管理', '维护寄件客户和联系人资料', 'customer_code:客户编号,customer_name:客户名称,contact_name:联系人,contact_phone:联系电话,province:省份,city:城市,address:地址,status:状态,created_at:创建时间,updated_at:更新时间', 'customer_name:客户名称,contact_name:联系人,contact_phone:联系电话,province:省份,city:城市,address:地址,status:状态'),
  waybills: moduleMeta('waybills', '运单中心', '订单创建后生成的运单和运输状态', 'waybill_no:运单号,order_id:订单ID,order_no:订单号,start_site:始发网点,target_site:目的网点,current_location:当前位置,transport_status:运输状态,create_time:创建时间,update_time:更新时间', 'order_id:订单ID,start_site:始发网点,target_site:目的网点,current_location:当前位置,transport_status:运输状态'),
  dispatches: moduleMeta('dispatches', '调度管理', '分配司机、车辆并跟踪调度状态', 'order_id:订单ID,order_no:订单号,waybill_id:运单ID,driver_id:司机ID,driver_name:司机,vehicle_id:车辆ID,vehicle_no:车辆,start_site:始发网点,target_site:目的网点,dispatch_status:调度状态,create_time:创建时间,update_time:更新时间', 'order_id:订单ID,waybill_id:运单ID,driver_id:司机ID,vehicle_id:车辆ID,start_site:始发网点,target_site:目的网点,dispatch_status:调度状态'),
  tasks: moduleMeta('tasks', '运输任务', '司机接单、运输、签收和异常上报', 'task_no:任务号,order_id:订单ID,order_no:订单号,driver_id:司机ID,driver_name:司机,vehicle_id:车辆ID,vehicle_no:车辆,task_status:任务状态,proof_url:签收凭证,create_time:创建时间,update_time:更新时间', 'order_id:订单ID,waybill_id:运单ID,dispatch_id:调度ID,driver_id:司机ID,vehicle_id:车辆ID,task_status:任务状态,proof_url:签收凭证'),
  tracks: moduleMeta('tracks', '物流轨迹', '按时间线记录订单运输轨迹', 'order_id:订单ID,order_no:订单号,waybill_id:运单ID,current_status:当前状态,current_location:当前位置,operator_name:操作人,operation_desc:操作说明,operation_time:操作时间', 'order_id:订单ID,waybill_id:运单ID,current_status:当前状态,current_location:当前位置,operator_name:操作人,operation_desc:操作说明,operation_time:操作时间:datetime'),
  drivers: moduleMeta('drivers', '司机管理', '维护司机证件和可用状态', 'driver_code:司机编号,driver_name:司机姓名,phone:手机号,license_no:驾驶证号,license_type:准驾车型,status:状态,created_at:创建时间,updated_at:更新时间', 'driver_name:司机姓名,phone:手机号,license_no:驾驶证号,license_type:准驾车型,status:状态'),
  vehicles: moduleMeta('vehicles', '车辆管理', '维护车辆、容量和当前位置', 'vehicle_no:车牌号,vehicle_type:车辆类型,load_capacity_kg:载重,volume_capacity_cubic:容积,current_city:当前城市,status:状态,created_at:创建时间,updated_at:更新时间', 'vehicle_no:车牌号,vehicle_type:车辆类型,load_capacity_kg:载重:number:2,volume_capacity_cubic:容积:number:2,current_city:当前城市,status:状态'),
  exceptions: moduleMeta('exceptions', '异常管理', '运输异常上报、处理和查询', 'order_id:订单ID,order_no:订单号,task_id:任务ID,exception_type:异常类型,exception_desc:异常描述,exception_status:异常状态,report_user:上报人,report_time:上报时间,handle_user:处理人,handle_time:处理时间', 'order_id:订单ID,task_id:任务ID,exception_type:异常类型,exception_desc:异常描述,exception_status:异常状态'),
  fees: moduleMeta('fees', '费用结算', '订单费用计算、账单和付款状态', 'order_id:订单ID,order_no:订单号,base_fee:基础运费,weight_fee:重量费用,distance_fee:距离费用,additional_fee:附加费,discount_fee:优惠金额,payable_fee:应收金额,actual_fee:实收金额,payment_status:付款状态,create_time:创建时间,update_time:更新时间', 'order_id:订单ID,base_fee:基础运费:number:2,weight_fee:重量费用:number:2,distance_fee:距离费用:number:2,additional_fee:附加费:number:2,discount_fee:优惠金额:number:2,payable_fee:应收金额:number:2,actual_fee:实收金额:number:2,payment_status:付款状态'),
  users: moduleMeta('users', '用户管理', '后台用户、状态和角色分配', 'user_code:用户编号,username:登录账号,real_name:姓名,mobile:手机号,email:邮箱,role_id:角色ID,role_name:角色,customer_id:关联客户ID,customer_name:关联客户,customer_account_type:客户账号类型,status:状态,create_time:创建时间,update_time:更新时间', 'username:登录账号,real_name:姓名,mobile:手机号,email:邮箱,password:密码,role_id:角色ID,customer_id:客户名称,status:状态'),
  roles: moduleMeta('roles', '角色管理', '系统管理员、客服、调度、司机、财务和客户角色', 'role_code:角色编码,role_name:角色名称,status:状态,create_time:创建时间,update_time:更新时间', 'role_name:角色名称,status:状态'),
  operationLogs: { title: '操作日志', description: '记录关键接口和业务写操作', editable: false, columns: columns('operation_id:操作ID,trace_id:Trace ID,user_code:用户编号,user_id:用户主键,username:操作人,role_code:角色编号,operation:操作内容,request_uri:请求地址,request_method:方法,operation_status:状态,cost_ms:耗时ms,operation_time:操作时间') },
  files: { title: '上传文件', description: '查看上传到本地的业务附件记录', editable: false, columns: columns('original_name:原文件名,relative_path:保存路径,file_size:大小,content_type:类型,upload_user:上传人,upload_time:上传时间') }
}

const meta = computed(() => moduleMetas[route.meta.module] || moduleMetas.customers)
const exceptionOrderOptions = computed(() => relationRows.orders.map((row) => ({
  value: row.order_no,
  label: relationLabel('orders', row)
})))
const activeEditFields = computed(() => (meta.value.editFields || []).map((field) => {
  const dynamicOptions = dynamicFieldOptions(field.prop, route.meta.module)
  if (dynamicOptions) {
    return { ...field, options: dynamicOptions, allowCreate: field.prop === 'customer_id' }
  }
  return field
}))
const modulePermission = computed(() => route.meta.permission || meta.value.permission)
const canCreate = computed(() => meta.value.editable && canAction('create'))
const canUpdate = computed(() => meta.value.editable && canAction('update'))
const canDelete = computed(() => meta.value.editable && canAction('delete'))
const canQuery = computed(() => canAction('query'))
const canExport = computed(() => canAction('export'))
const canImportCustomer = computed(() => route.meta.module === 'customers' && canAction('import'))
const canReportException = computed(() => route.meta.module === 'exceptions' && canAction('create'))
const canHandleException = computed(() => route.meta.module === 'exceptions' && canAction('update'))
const canGenerateFee = computed(() => route.meta.module === 'fees' && canAction('create'))
const canPayFee = computed(() => route.meta.module === 'fees' && canAction('update'))
const showCrudColumn = computed(() => canUpdate.value || canDelete.value)
const canSubmitCrud = computed(() => crudMode.value === 'create' ? canCreate.value : canUpdate.value)

function columns(definition) {
  return definition.split(',').map((item) => {
    const [prop, label] = item.split(':')
    return { prop, label, minWidth: prop.includes('desc') || prop.includes('address') ? 220 : 130 }
  })
}

function moduleMeta(module, title, description, columnDefinition, editDefinition) {
  return { title, description, columns: columns(columnDefinition), editable: true, editFields: editFields(editDefinition, module) }
}

function editFields(definition, module) {
  return definition.split(',').map((item) => {
    const [prop, label, type, precision] = item.split(':')
    return { prop, label, type: type || 'text', precision: precision ? Number(precision) : undefined, options: fieldOptions(prop, module) }
  })
}

function options(definition) {
  return definition.split(',').map((item) => {
    const [value, label] = item.split(':')
    return { value, label }
  })
}

function fieldOptions(prop, module) {
  if (prop === 'license_type') {
    return fieldOptionGroups.licenseType
  }
  if (prop === 'vehicle_type') {
    return fieldOptionGroups.vehicleType
  }
  if (prop === 'exception_type') {
    return fieldOptionGroups.exceptionType
  }
  if (prop === 'province') {
    return fieldOptionGroups.province
  }
  if (prop === 'city' || prop === 'current_city') {
    return fieldOptionGroups.city
  }
  if (prop === 'status') {
    if (['orders'].includes(module)) {
      return statusOptions.order
    }
    if (module === 'drivers') {
      return statusOptions.driver
    }
    if (module === 'vehicles') {
      return statusOptions.vehicle
    }
    if (['users', 'roles'].includes(module)) {
      return statusOptions.numeric
    }
    return statusOptions.common
  }
  if (prop === 'transport_status' || prop === 'current_status') {
    return statusOptions.transport
  }
  if (prop === 'dispatch_status') {
    return statusOptions.dispatch
  }
  if (prop === 'task_status') {
    return statusOptions.task
  }
  if (prop === 'exception_status') {
    return statusOptions.exception
  }
  if (prop === 'payment_status') {
    return statusOptions.fee
  }
  return undefined
}

function dynamicFieldOptions(prop, module) {
  const source = relationFieldSources[module]?.[prop]
  return source ? relationOptions[source] : undefined
}

function canAction(action) {
  const permission = actionPermission(action)
  return hasPermission(permission)
}

function actionPermission(action) {
  if (!modulePermission.value) {
    return ''
  }
  const index = modulePermission.value.lastIndexOf(':')
  if (index > 0) {
    return `${modulePermission.value.slice(0, index)}:${action}`
  }
  return modulePermission.value
}

function formatCell(prop, value) {
  if (prop === 'customer_account_type') {
    return value === 'MAIN' ? '主账号' : value === 'SUB' ? '子账号' : value
  }
  if (prop.includes('status') || prop === 'status') {
    return statusLabel(value)
  }
  if (prop.includes('time') || prop.endsWith('_at')) {
    return formatDateTime(value)
  }
  return value
}

async function loadData() {
  loading.value = true
  try {
    const result = await fetchModuleRecords(route.meta.module, {
      page: page.value,
      pageSize: limit.value,
      keyword: keyword.value || undefined,
      startTime: timeRange.value?.[0],
      endTime: timeRange.value?.[1]
    })
    if (Array.isArray(result)) {
      records.value = result
      total.value = result.length
    } else {
      records.value = result.records || []
      total.value = result.total || 0
      page.value = result.page || page.value
      limit.value = result.pageSize || limit.value
    }
  } catch (error) {
    records.value = []
    total.value = 0
    throw error
  } finally {
    loading.value = false
  }
}

async function loadCurrentRelationOptions() {
  const sources = [...new Set(Object.values(relationFieldSources[route.meta.module] || {}))]
  await Promise.allSettled(sources.map((source) => loadRelationOptions(source)))
}

async function loadRelationOptions(source) {
  if (source === 'orderCustomers') {
    const customerMap = new Map()
    if (hasPermission('order:query')) {
      const orderResult = await fetchModuleRecords('orders', { page: 1, pageSize: 200 })
      const orderRows = Array.isArray(orderResult) ? orderResult : (orderResult.records || [])
      orderRows.forEach((row) => {
        const customerName = row.customer_name || row.customerName
        if (!customerName || customerMap.has(customerName)) {
          return
        }
        const customerId = row.customer_id || row.customerId || customerName
        customerMap.set(customerName, {
          value: String(customerId),
          label: `${customerName}${row.order_no ? `（来自运单 ${row.order_no}）` : ''}`
        })
      })
    }
    if (hasPermission('customer:query')) {
      const customerResult = await fetchModuleRecords('customers', { page: 1, pageSize: 200 })
      const customerRows = Array.isArray(customerResult) ? customerResult : (customerResult.records || [])
      customerRows.forEach((row) => {
        const customerName = row.customer_name || row.customerName
        if (!customerName || customerMap.has(customerName)) {
          return
        }
        customerMap.set(customerName, {
          value: String(row.id || customerName),
          label: `${customerName}${row.customer_code ? `（${row.customer_code}）` : ''}`
        })
      })
    }
    relationOptions[source] = Array.from(customerMap.values())
    return
  }
  if (!hasPermission(sourceQueryPermission(source))) {
    relationOptions[source] = []
    if (source === 'orders') {
      relationRows.orders = []
    }
    return
  }
  const result = await fetchModuleRecords(source, { page: 1, pageSize: 100 })
  const rows = Array.isArray(result) ? result : (result.records || [])
  if (source === 'orders') {
    relationRows.orders = rows
  }
  relationOptions[source] = rows.map((row) => ({ value: String(row.id), label: relationLabel(source, row) }))
}

function sourceQueryPermission(source) {
  const prefixes = {
    orders: 'order',
    customers: 'customer',
    waybills: 'waybill',
    dispatches: 'dispatch',
    tasks: 'task',
    tracks: 'track',
    drivers: 'driver',
    vehicles: 'vehicle',
    exceptions: 'exception',
    fees: 'fee',
    roles: 'system:role',
    users: 'system:user',
    orderCustomers: 'order'
  }
  return `${prefixes[source] || source}:query`
}

function relationLabel(source, row) {
  if (source === 'roles') {
    return `${row.role_name || '未命名角色'}（${row.role_code || row.id}）`
  }
  if (source === 'customers') {
    return `${row.customer_name || row.id}${row.customer_code ? `（${row.customer_code}）` : ''}`
  }
  if (source === 'orders') {
    return `${row.order_no || row.id}${row.customer_name ? ` - ${row.customer_name}` : ''}`
  }
  if (source === 'waybills') {
    return `${row.waybill_no || row.id}${row.order_no ? ` - ${row.order_no}` : ''}`
  }
  if (source === 'dispatches') {
    return `${row.order_no || '调度'} / ${row.driver_name || row.id}`
  }
  if (source === 'drivers') {
    return `${row.driver_name || '未命名司机'}（${row.driver_code || row.id}）`
  }
  if (source === 'vehicles') {
    return `${row.vehicle_no || row.id}${row.vehicle_type ? ` - ${row.vehicle_type}` : ''}`
  }
  if (source === 'tasks') {
    return `${row.task_no || row.id}${row.order_no ? ` - ${row.order_no}` : ''}`
  }
  return String(row.id)
}

function handlePageChange(nextPage) {
  page.value = nextPage
  loadData()
}

function handlePageSizeChange(nextPageSize) {
  limit.value = nextPageSize
  page.value = 1
  loadData()
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
    ElMessage.success(`导入完成，${result.imported || 0} 条`)
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

function exceptionStatus(row) {
  return row.exception_status || row.exceptionStatus
}

async function handleExceptionAction(row, targetStatus) {
  const isProcessing = targetStatus === 'PROCESSING'
  const confirmText = isProcessing ? '确认开始处理这条异常记录吗？' : '确认将这条异常标记为已处理吗？'
  await ElMessageBox.confirm(confirmText, '异常处理')
  await handleException(row.id, { exceptionStatus: targetStatus })
  ElMessage.success(isProcessing ? '异常已进入处理中' : '异常已处理')
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

function openCreateDialog() {
  crudMode.value = 'create'
  editingId.value = null
  resetCrudForm()
  crudDialogVisible.value = true
}

function openEditDialog(row) {
  crudMode.value = 'edit'
  editingId.value = row.id
  resetCrudForm(row)
  crudDialogVisible.value = true
}

function resetCrudForm(row = {}) {
  Object.keys(crudForm).forEach((key) => delete crudForm[key])
  activeEditFields.value.forEach((field) => {
    crudForm[field.prop] = normalizeFormValue(field, pickRowValue(row, field.prop))
  })
}

function pickRowValue(row, prop) {
  return row[prop] ?? row[toSnakeCase(prop)] ?? row[toCamelCase(prop)] ?? ''
}

function normalizeFormValue(field, value) {
  if (field.options && value !== '' && value !== null && value !== undefined) {
    return String(value)
  }
  return value ?? ''
}

function toSnakeCase(value) {
  return value.replace(/[A-Z]/g, (letter) => `_${letter.toLowerCase()}`)
}

function toCamelCase(value) {
  return value.replace(/_([a-z])/g, (_, letter) => letter.toUpperCase())
}

async function submitCrud() {
  saving.value = true
  try {
    if (crudMode.value === 'create') {
      if (route.meta.businessCreate) {
        await createOrder({ ...crudForm })
      } else {
        await createModuleRecord(route.meta.module, buildCrudPayload())
      }
      ElMessage.success('新增成功')
    } else {
      await updateModuleRecord(route.meta.module, editingId.value, buildCrudPayload())
      ElMessage.success('修改成功')
    }
    crudDialogVisible.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

function buildCrudPayload() {
  return Object.fromEntries(Object.entries(crudForm).map(([key, value]) => [toSnakeCase(key), value]))
}

async function deleteRow(row) {
  await ElMessageBox.confirm('确认删除这条记录吗？', '删除确认')
  await deleteModuleRecord(route.meta.module, row.id)
  ElMessage.success('删除成功')
  await loadData()
}

watch(() => route.meta.module, () => {
  page.value = 1
  loadData()
  loadCurrentRelationOptions()
})
onMounted(() => {
  loadData()
  loadCurrentRelationOptions()
})
</script>
