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
        :can-create-customer-account="canCreateCustomerAccount"
        :can-report-exception="canReportException"
        :can-generate-fee="canGenerateFee"
        :can-import-customer="canImportCustomer"
        :can-export="canExport"
        :can-query="canQuery"
        @create="openCreateDialog"
        @create-customer-account="openCustomerAccountDialog"
        @report-exception="exceptionDialogVisible = true"
        @generate-fee="feeDialogVisible = true"
        @import-customer="handleCustomerImport"
        @export="downloadExcel"
        @search="loadData"
      />
    </div>

    <el-table :data="records" v-loading="loading" height="640">
      <el-table-column v-for="column in tableColumns" :key="column.prop" :prop="column.prop" :label="column.label" :min-width="column.minWidth || 120">
        <template #default="{ row }">
          <span
            class="table-cell-text"
            :class="{ 'table-cell-ellipsis': isOperationLogs && isCompactLogColumn(column.prop) }"
            :title="fullCellText(column.prop, row[column.prop])"
          >
            {{ displayCell(column.prop, row[column.prop]) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column v-if="isOperationLogs" label="详情" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openOperationLogDetail(row)">查看</el-button>
        </template>
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

    <el-dialog v-model="customerAccountDialogVisible" title="新增客户账号" width="760px">
      <el-form label-position="top" :model="customerAccountForm">
        <el-row :gutter="16">
          <el-col :xs="24" :md="12">
            <el-form-item label="账号类型">
              <el-radio-group v-model="customerAccountForm.customerSubjectType">
                <el-radio-button label="PERSONAL">个人账号</el-radio-button>
                <el-radio-button label="ENTERPRISE">企业账号</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item :label="customerAccountForm.customerSubjectType === 'ENTERPRISE' ? '公司名称' : '客户名称'">
              <el-select v-model="customerAccountForm.customerName" clearable filterable allow-create default-first-option style="width: 100%">
                <el-option v-for="option in relationOptions.orderCustomers" :key="option.value" :label="option.label" :value="option.rawName || option.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="登录账号"><el-input v-model="customerAccountForm.username" clearable /></el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="姓名"><el-input v-model="customerAccountForm.realName" clearable /></el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="手机号"><el-input v-model="customerAccountForm.mobile" maxlength="11" clearable /></el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="邮箱"><el-input v-model="customerAccountForm.email" clearable /></el-form-item>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-form-item label="密码"><el-input v-model="customerAccountForm.password" type="password" show-password clearable /></el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="customerAccountDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCustomerAccount">保存</el-button>
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

    <el-drawer v-model="operationLogDetailVisible" title="操作日志详情" size="620px">
      <div v-if="selectedOperationLog" class="log-detail">
        <section v-for="section in operationLogDetailSections" :key="section.title" class="log-detail-section">
          <h4>{{ section.title }}</h4>
          <div v-for="item in section.items" :key="item.prop" class="log-detail-row">
            <span class="log-detail-label">{{ item.label }}</span>
            <span class="log-detail-value">{{ fullCellText(item.prop, selectedOperationLog[item.prop]) || '-' }}</span>
            <el-button
              v-if="selectedOperationLog[item.prop] !== undefined && selectedOperationLog[item.prop] !== null && selectedOperationLog[item.prop] !== ''"
              link
              type="primary"
              @click="copyValue(fullCellText(item.prop, selectedOperationLog[item.prop]))"
            >
              复制
            </el-button>
          </div>
        </section>
      </div>
    </el-drawer>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createCustomerAccount,
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
import { actionPermissionFromRoutePermission } from '../utils/permission-utils'
import { hasPermission } from '../stores/auth-store'
import ModulePagination from '../components/ModulePagination.vue'
import ModuleToolbar from '../components/ModuleToolbar.vue'
import {
  fieldOptionGroups,
  moduleMetas,
  operationLogTableColumns,
  relationFieldSources
} from '../config/module-metadata'

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
const customerAccountDialogVisible = ref(false)
const operationLogDetailVisible = ref(false)
const selectedOperationLog = ref(null)
const crudMode = ref('create')
const editingId = ref(null)
const crudForm = reactive({})
const exceptionForm = reactive({ orderNo: '', exceptionType: '', exceptionDesc: '' })
const feeForm = reactive({ orderNo: '' })
const customerAccountForm = reactive({
  customerSubjectType: 'ENTERPRISE',
  customerName: '',
  username: '',
  realName: '',
  mobile: '',
  email: '',
  password: ''
})
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

const meta = computed(() => moduleMetas[route.meta.module] || moduleMetas.customers)
const isOperationLogs = computed(() => route.meta.module === 'operationLogs')
const tableColumns = computed(() => isOperationLogs.value ? operationLogTableColumns : meta.value.columns)
const operationLogDetailSections = computed(() => [
  {
    title: '链路标识',
    items: [
      { prop: 'operation_id', label: '操作ID' },
      { prop: 'trace_id', label: 'Trace ID' },
      { prop: 'login_session_id', label: '会话ID' }
    ]
  },
  {
    title: '操作人',
    items: [
      { prop: 'user_code', label: '用户编号' },
      { prop: 'user_id', label: '用户主键' },
      { prop: 'username', label: '操作人' },
      { prop: 'role_code', label: '角色编号' },
      { prop: 'client_ip', label: '客户端IP' }
    ]
  },
  {
    title: '请求信息',
    items: [
      { prop: 'operation', label: '操作内容' },
      { prop: 'target_id', label: '对象ID' },
      { prop: 'request_method', label: '方法' },
      { prop: 'request_uri', label: '请求地址' },
      { prop: 'operation_status', label: '状态' },
      { prop: 'cost_ms', label: '耗时ms' },
      { prop: 'operation_time', label: '操作时间' }
    ]
  },
  {
    title: '参数与变更',
    items: [
      { prop: 'request_params', label: '参数摘要' },
      { prop: 'change_summary', label: '变更摘要' },
      { prop: 'error_message', label: '异常信息' }
    ]
  }
])
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
const canCreateCustomerAccount = computed(() => route.meta.module === 'users' && canAction('create'))
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

function dynamicFieldOptions(prop, module) {
  const source = relationFieldSources[module]?.[prop]
  return source ? relationOptions[source] : undefined
}

function canAction(action) {
  const permission = actionPermission(action)
  return hasPermission(permission)
}

function actionPermission(action) {
  return actionPermissionFromRoutePermission(modulePermission.value, action)
}

function formatCell(prop, value) {
  if (prop === 'customer_subject_type') {
    return value === 'PERSONAL' ? '个人客户' : value === 'ENTERPRISE' ? '企业客户' : value
  }
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

function fullCellText(prop, value) {
  const text = formatCell(prop, value)
  return text === null || text === undefined ? '' : String(text)
}

function displayCell(prop, value) {
  const text = fullCellText(prop, value)
  if (!isOperationLogs.value) {
    return text
  }
  if (['operation_id', 'trace_id', 'login_session_id'].includes(prop)) {
    return shortenText(text, 18)
  }
  if (['request_uri', 'operation'].includes(prop)) {
    return shortenText(text, 34)
  }
  return text
}

function shortenText(value, maxLength) {
  if (!value || value.length <= maxLength) {
    return value
  }
  if (maxLength <= 10) {
    return `${value.slice(0, maxLength)}...`
  }
  const headLength = Math.ceil((maxLength - 3) * 0.6)
  const tailLength = Math.floor((maxLength - 3) * 0.4)
  return `${value.slice(0, headLength)}...${value.slice(-tailLength)}`
}

function isCompactLogColumn(prop) {
  return ['operation_id', 'trace_id', 'login_session_id', 'request_uri', 'operation'].includes(prop)
}

function openOperationLogDetail(row) {
  selectedOperationLog.value = row
  operationLogDetailVisible.value = true
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
      const orderResult = await fetchModuleRecords('orders', relationQueryParams(200))
      const orderRows = Array.isArray(orderResult) ? orderResult : (orderResult.records || [])
      orderRows.forEach((row) => {
        const customerName = row.customer_name || row.customerName
        if (!customerName || customerMap.has(customerName)) {
          return
        }
        const customerId = row.customer_id || row.customerId || customerName
        customerMap.set(customerName, {
          value: String(customerId),
          rawName: customerName,
          label: `${customerName}${row.order_no ? `（来自运单 ${row.order_no}）` : ''}`
        })
      })
    }
    if (hasPermission('customer:query')) {
      const customerResult = await fetchModuleRecords('customers', relationQueryParams(200))
      const customerRows = Array.isArray(customerResult) ? customerResult : (customerResult.records || [])
      customerRows.forEach((row) => {
        const customerName = row.customer_name || row.customerName
        if (!customerName || customerMap.has(customerName)) {
          return
        }
        customerMap.set(customerName, {
          value: String(row.id || customerName),
          rawName: customerName,
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
  const result = await fetchModuleRecords(source, relationQueryParams(100))
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

function relationQueryParams(pageSize) {
  return { page: 1, pageSize, usage: 'relationOptions' }
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

function openCustomerAccountDialog() {
  Object.assign(customerAccountForm, {
    customerSubjectType: 'ENTERPRISE',
    customerName: '',
    username: '',
    realName: '',
    mobile: '',
    email: '',
    password: ''
  })
  customerAccountDialogVisible.value = true
  loadRelationOptions('orderCustomers')
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

async function submitCustomerAccount() {
  if (!/^1[3-9]\d{9}$/.test(customerAccountForm.mobile || '')) {
    ElMessage.warning('手机号必须是11位中国大陆手机号')
    return
  }
  if (customerAccountForm.customerSubjectType === 'ENTERPRISE' && !customerAccountForm.customerName) {
    ElMessage.warning('企业账号必须选择或填写公司名称')
    return
  }
  saving.value = true
  try {
    await createCustomerAccount({ ...customerAccountForm })
    ElMessage.success('客户账号创建成功')
    customerAccountDialogVisible.value = false
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

<style scoped>
.table-cell-text {
  display: inline-block;
  max-width: 100%;
  vertical-align: middle;
}

.table-cell-ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.log-detail {
  display: grid;
  gap: 18px;
}

.log-detail-section {
  border-bottom: 1px solid #edf0f5;
  padding-bottom: 14px;
}

.log-detail-section h4 {
  color: #303133;
  font-size: 15px;
  margin: 0 0 12px;
}

.log-detail-row {
  align-items: start;
  display: grid;
  gap: 10px;
  grid-template-columns: 110px minmax(0, 1fr) 44px;
  line-height: 1.7;
  padding: 4px 0;
}

.log-detail-label {
  color: #909399;
}

.log-detail-value {
  color: #303133;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
