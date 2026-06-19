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

    <el-alert
      v-if="!hasVisibleColumns && !loading"
      title="暂无权限查看此页面数据"
      type="warning"
      :closable="false"
      show-icon
      class="column-permission-alert"
    />

    <el-table v-if="hasVisibleColumns" :data="records" v-loading="loading" height="640">
      <el-table-column v-for="column in visibleColumns" :key="column.prop" :prop="column.prop" :label="column.label" :min-width="column.minWidth || 120">
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
      v-if="hasVisibleColumns"
      :page="page"
      :page-size="limit"
      :total="total"
      @page-change="handlePageChange"
      @page-size-change="handlePageSizeChange"
    />

    <CrudDialog
      v-model:visible="crudDialogVisible"
      :title="crudMode === 'create' ? `新增${meta.title}` : `编辑${meta.title}`"
      :form="crudForm"
      :fields="activeEditFields"
      :saving="saving"
      :can-submit="canSubmitCrud"
      @submit="submitCrud"
    />

    <CustomerAccountDialog
      v-model:visible="customerAccountDialogVisible"
      :form="customerAccountForm"
      :customer-options="relationOptions.orderCustomers"
      :saving="saving"
      @submit="submitCustomerAccount"
    />

    <ExceptionDialog
      v-model:visible="exceptionDialogVisible"
      :form="exceptionForm"
      :order-options="exceptionOrderOptions"
      :exception-type-options="fieldOptionGroups.exceptionType"
      :saving="saving"
      :can-submit="canReportException"
      @submit="submitException"
    />

    <FeeDialog
      v-model:visible="feeDialogVisible"
      :form="feeForm"
      :order-options="exceptionOrderOptions"
      :saving="saving"
      :can-submit="canGenerateFee"
      @submit="submitFee"
    />

    <OperationLogDetail
      v-model:visible="operationLogDetailVisible"
      :log="selectedOperationLog"
      :sections="operationLogDetailSections"
      :format-cell="formatCell"
    />
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
import { hasPermission, canModuleAction, canShowColumn } from '../stores/auth-store'
import CrudDialog from '../components/CrudDialog.vue'
import CustomerAccountDialog from '../components/CustomerAccountDialog.vue'
import ExceptionDialog from '../components/ExceptionDialog.vue'
import FeeDialog from '../components/FeeDialog.vue'
import OperationLogDetail from '../components/OperationLogDetail.vue'
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
const fullTableColumns = computed(() => isOperationLogs.value ? operationLogTableColumns : meta.value.columns)
/** 按列权限过滤后的可见列 */
const visibleColumns = computed(() => {
  const mod = route.meta.module
  if (!mod) return fullTableColumns.value
  return fullTableColumns.value.filter((col) => canShowColumn(mod, col.prop))
})
const hasVisibleColumns = computed(() => visibleColumns.value.length > 0)
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
      { prop: 'operation_source', label: '操作来源' },
      { prop: 'executor_type', label: '执行者' },
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
  },
  {
    title: 'AI 审计',
    items: [
      { prop: 'ai_conversation_id', label: 'AI会话ID' },
      { prop: 'ai_message_id', label: 'AI消息ID' },
      { prop: 'ai_tool_name', label: 'AI工具' },
      { prop: 'ai_tool_target', label: 'AI目标' },
      { prop: 'ai_readonly', label: '是否只读' },
      { prop: 'ai_prompt_summary', label: 'AI问题摘要' },
      { prop: 'ai_result_summary', label: 'AI结果摘要' }
    ]
  }
])
const visibleOperationLogDetailSections = computed(() => operationLogDetailSections.value
  .map((section) => ({
    ...section,
    items: section.items.filter((item) => canShowColumn(route.meta.module, item.prop))
  }))
  .filter((section) => section.items.length > 0))
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
const moduleCode = computed(() => route.meta.module || '')
const canCreate = computed(() => meta.value.editable && canModuleAction(moduleCode.value, 'create'))
const canCreateCustomerAccount = computed(() => route.meta.module === 'users' && canModuleAction(moduleCode.value, 'create'))
const canUpdate = computed(() => meta.value.editable && canModuleAction(moduleCode.value, 'update'))
const canDelete = computed(() => meta.value.editable && canModuleAction(moduleCode.value, 'delete'))
const canQuery = computed(() => canModuleAction(moduleCode.value, 'query'))
const canExport = computed(() => canModuleAction(moduleCode.value, 'export'))
const canImportCustomer = computed(() => route.meta.module === 'customers' && canModuleAction(moduleCode.value, 'import'))
const canReportException = computed(() => route.meta.module === 'exceptions' && canModuleAction(moduleCode.value, 'create'))
const canHandleException = computed(() => route.meta.module === 'exceptions' && canModuleAction(moduleCode.value, 'update'))
const canGenerateFee = computed(() => route.meta.module === 'fees' && canModuleAction(moduleCode.value, 'create'))
const canPayFee = computed(() => route.meta.module === 'fees' && canModuleAction(moduleCode.value, 'update'))
const showCrudColumn = computed(() => canUpdate.value || canDelete.value)
const canSubmitCrud = computed(() => crudMode.value === 'create' ? canCreate.value : canUpdate.value)

function dynamicFieldOptions(prop, module) {
  const source = relationFieldSources[module]?.[prop]
  return source ? relationOptions[source] : undefined
}

function formatCell(prop, value) {
  if (prop === 'operation_source') {
    const map = { USER: '用户操作', USER_TO_AI: '用户询问AI', AI_TOOL: 'AI调用工具', AI_RESPONSE: 'AI生成回答', SYSTEM: '系统操作' }
    return map[value] || value
  }
  if (prop === 'executor_type') {
    const map = { USER: '用户', AI: 'AI助手', SYSTEM: '系统' }
    return map[value] || value
  }
  if (prop === 'ai_readonly') {
    return value === 1 || value === true ? '只读' : value === 0 || value === false ? '非只读' : value
  }
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
    ElMessage.error('加载数据失败：' + ((error.response?.data?.message || error.message) || '未知错误'))
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
  } catch (error) {
    ElMessage.error('导入失败：' + ((error.response?.data?.message || error.message) || '未知错误'))
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
  } catch (error) {
    ElMessage.error('提交异常失败：' + ((error.response?.data?.message || error.message) || '未知错误'))
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
  } catch (error) {
    ElMessage.error('生成费用失败：' + ((error.response?.data?.message || error.message) || '未知错误'))
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
  } catch (error) {
    ElMessage.error(((crudMode.value === 'create' ? '新增' : '修改')) + '失败：' + ((error.response?.data?.message || error.message) || '未知错误'))
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
  } catch (error) {
    ElMessage.error('创建客户账号失败：' + ((error.response?.data?.message || error.message) || '未知错误'))
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
.column-permission-alert {
  margin: 16px 0;
}

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
