<template>
  <section class="content-panel">
    <div class="panel-header">
      <div>
        <h3>{{ meta.title }}</h3>
        <p>{{ meta.description }}</p>
      </div>
      <div class="toolbar">
        <el-input-number v-model="limit" :min="1" :max="100" />
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
    </el-table>
  </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { fetchModuleRecords } from '../api/logistics'

const route = useRoute()
const limit = ref(20)
const records = ref([])
const loading = ref(false)

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
  roles: { title: '角色管理', description: '系统管理员、客服、调度、司机、财务和客户等角色', columns: columns('role_code:角色编码,role_name:角色名称,status:状态,create_time:创建时间') }
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

watch(() => route.meta.module, loadData)
onMounted(loadData)
</script>
