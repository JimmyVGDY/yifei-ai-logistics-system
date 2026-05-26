<template>
  <section class="content-panel permission-page">
    <div class="panel-header">
      <div>
        <h3>权限配置</h3>
        <p>按角色维护可见菜单和接口权限</p>
      </div>
      <div class="permission-actions">
        <el-select v-model="selectedRoleId" filterable placeholder="选择角色" style="width: 260px" @change="loadRoleMenus">
          <el-option v-for="role in roles" :key="role.id" :label="roleLabel(role)" :value="String(role.id)" />
        </el-select>
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
        <el-button type="primary" :loading="saving" :disabled="!selectedRoleId" @click="saveRoleMenus">保存权限</el-button>
      </div>
    </div>

    <div class="permission-layout">
      <aside class="role-list">
        <button
          v-for="role in roles"
          :key="role.id"
          :class="{ active: String(role.id) === selectedRoleId }"
          @click="selectRole(role)"
        >
          <strong>{{ role.role_name }}</strong>
          <span>{{ role.role_code }}</span>
        </button>
      </aside>

      <div class="permission-tree">
        <el-tree
          ref="menuTreeRef"
          :data="menus"
          node-key="id"
          show-checkbox
          default-expand-all
          :props="treeProps"
          :default-checked-keys="checkedMenuIds"
        >
          <template #default="{ data }">
            <div class="menu-node">
              <span>{{ data.name }}</span>
              <small>{{ data.permissionCode }}</small>
            </div>
          </template>
        </el-tree>
      </div>
    </div>
  </section>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchModuleRecords } from '../api/logistics'
import { fetchSession } from '../api/auth'
import { fetchPermissionMenus, fetchRoleMenuIds, updateRoleMenuIds } from '../api/system-permission'
import { getAuthToken, saveAuthToken } from '../stores/auth-store'

const roles = ref([])
const menus = ref([])
const checkedMenuIds = ref([])
const selectedRoleId = ref('')
const loading = ref(false)
const saving = ref(false)
const menuTreeRef = ref(null)
const treeProps = { label: 'name', children: 'children' }

async function loadAll() {
  loading.value = true
  try {
    const [rolePage, menuTree] = await Promise.all([
      fetchModuleRecords('roles', { page: 1, pageSize: 100 }),
      fetchPermissionMenus()
    ])
    roles.value = Array.isArray(rolePage) ? rolePage : (rolePage.records || [])
    menus.value = menuTree || []
    if (!selectedRoleId.value && roles.value.length) {
      selectedRoleId.value = String(roles.value[0].id)
    }
    if (selectedRoleId.value) {
      await loadRoleMenus()
    }
  } finally {
    loading.value = false
  }
}

async function loadRoleMenus() {
  if (!selectedRoleId.value) {
    checkedMenuIds.value = []
    return
  }
  checkedMenuIds.value = await fetchRoleMenuIds(selectedRoleId.value)
  await nextTick()
  menuTreeRef.value?.setCheckedKeys(checkedMenuIds.value)
}

function selectRole(role) {
  selectedRoleId.value = String(role.id)
  loadRoleMenus()
}

async function saveRoleMenus() {
  saving.value = true
  try {
    const checked = menuTreeRef.value?.getCheckedKeys(false) || []
    const halfChecked = menuTreeRef.value?.getHalfCheckedKeys() || []
    const menuIds = [...new Set([...checked, ...halfChecked])].map((id) => Number(id))
    checkedMenuIds.value = await updateRoleMenuIds(selectedRoleId.value, menuIds)
    await refreshCurrentSessionIfNeeded()
    ElMessage.success('权限配置已保存')
  } finally {
    saving.value = false
  }
}

async function refreshCurrentSessionIfNeeded() {
  const currentRole = roles.value.find((role) => String(role.id) === selectedRoleId.value)
  if (!currentRole || currentRole.role_code !== getAuthToken().roleCode) {
    return
  }
  saveAuthToken(await fetchSession())
}

function roleLabel(role) {
  return `${role.role_name || '未命名角色'}（${role.role_code || role.id}）`
}

onMounted(loadAll)
</script>

<style scoped>
.permission-page {
  min-height: 720px;
}

.permission-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.permission-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 18px;
}

.role-list {
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-right: 18px;
}

.role-list button {
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  color: #334155;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  text-align: left;
}

.role-list button.active {
  background: #eff6ff;
  border-color: #93c5fd;
  color: #1d4ed8;
}

.role-list span {
  color: #64748b;
  font-size: 12px;
}

.permission-tree {
  min-height: 560px;
}

.menu-node {
  align-items: center;
  display: flex;
  gap: 12px;
}

.menu-node small {
  color: #64748b;
}

@media (max-width: 900px) {
  .permission-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .permission-layout {
    grid-template-columns: 1fr;
  }

  .role-list {
    border-right: 0;
    border-bottom: 1px solid #e5e7eb;
    padding-bottom: 14px;
    padding-right: 0;
  }
}
</style>
