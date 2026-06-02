<template>
  <section class="content-panel permission-page">
    <div class="panel-header">
      <div>
        <h3>权限配置</h3>
        <p>以角色权限为基础，支持给单个用户额外授权或单独禁用页面、按钮和接口权限</p>
      </div>
      <div class="permission-actions">
        <el-radio-group v-model="mode" @change="handleModeChange">
          <el-radio-button label="role">角色权限</el-radio-button>
          <el-radio-button label="user">用户特殊权限</el-radio-button>
        </el-radio-group>
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
        <el-button v-if="canUpdatePermission" type="primary" :loading="saving" :disabled="!canSave" @click="savePermissions">保存权限</el-button>
      </div>
    </div>

    <div class="permission-layout">
      <aside class="subject-list">
        <el-input v-model="subjectKeyword" clearable placeholder="搜索角色或用户" />
        <button
          v-for="subject in filteredSubjects"
          :key="subject.id"
          :class="{ active: String(subject.id) === selectedSubjectId }"
          @click="selectSubject(subject)"
        >
          <strong>{{ subject.name }}</strong>
          <span>{{ subject.code }}</span>
        </button>
      </aside>

      <main class="permission-editor">
        <el-alert
          v-if="mode === 'user'"
          type="info"
          :closable="false"
          show-icon
          title="用户特殊权限会覆盖角色基础权限：额外授权会增加能力，单独禁用会收回能力。"
        />

        <div v-if="mode === 'role'" class="tree-card">
          <div class="tree-title">
            <strong>角色基础权限</strong>
            <span>决定该角色默认可见页面和可执行操作</span>
          </div>
          <el-tree
            ref="roleTreeRef"
            :data="permissionTree"
            node-key="id"
            show-checkbox
            default-expand-all
            :props="treeProps"
            :default-checked-keys="rolePermissionIds"
          >
            <template #default="{ data }">
              <div class="permission-node">
                <span>{{ data.label }}</span>
                <small>{{ data.permissionCode }}</small>
              </div>
            </template>
          </el-tree>
        </div>

        <div v-else class="user-permission-grid">
          <div class="tree-card">
            <div class="tree-title">
              <strong>额外授权</strong>
              <span>在用户所属角色之外追加权限</span>
            </div>
            <el-tree
              ref="grantTreeRef"
              :data="permissionTree"
              node-key="id"
              show-checkbox
              default-expand-all
              :props="treeProps"
              :default-checked-keys="userPermission.grantPermissionIds"
              @check="syncUserTrees('grant')"
            >
              <template #default="{ data }">
                <div class="permission-node">
                  <span>{{ data.label }}</span>
                  <small>{{ data.permissionCode }}</small>
                </div>
              </template>
            </el-tree>
          </div>

          <div class="tree-card deny-card">
            <div class="tree-title">
              <strong>单独禁用</strong>
              <span>即使角色拥有，也禁止该用户使用</span>
            </div>
            <el-tree
              ref="denyTreeRef"
              :data="permissionTree"
              node-key="id"
              show-checkbox
              default-expand-all
              :props="treeProps"
              :default-checked-keys="userPermission.denyPermissionIds"
              @check="syncUserTrees('deny')"
            >
              <template #default="{ data }">
                <div class="permission-node">
                  <span>{{ data.label }}</span>
                  <small>{{ data.permissionCode }}</small>
                </div>
              </template>
            </el-tree>
          </div>
        </div>
      </main>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchModuleRecords } from '../api/logistics'
import { fetchSession } from '../api/auth'
import {
  fetchPermissionTree,
  fetchRolePermissionIds,
  fetchUserPermissionIds,
  updateRolePermissionIds,
  updateUserPermissionIds
} from '../api/system-permission'
import { getAuthToken, hasPermission, saveAuthToken } from '../stores/auth-store'

const mode = ref('role')
const roles = ref([])
const users = ref([])
const permissionTree = ref([])
const selectedSubjectId = ref('')
const rolePermissionIds = ref([])
const userPermission = ref({ grantPermissionIds: [], denyPermissionIds: [] })
const subjectKeyword = ref('')
const loading = ref(false)
const saving = ref(false)
const roleTreeRef = ref(null)
const grantTreeRef = ref(null)
const denyTreeRef = ref(null)
const treeProps = { label: 'label', children: 'children' }

const subjects = computed(() => mode.value === 'role' ? roles.value : users.value)
const filteredSubjects = computed(() => {
  const keyword = subjectKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return subjects.value
  }
  return subjects.value.filter((subject) => `${subject.name}${subject.code}`.toLowerCase().includes(keyword))
})
const canSave = computed(() => Boolean(selectedSubjectId.value))
const canUpdatePermission = computed(() => hasPermission('system:permission:update') || hasPermission('system:permission:manage'))

async function loadAll() {
  loading.value = true
  try {
    const [rolePage, userPage, tree] = await Promise.all([
      fetchModuleRecords('roles', { page: 1, pageSize: 200, usage: 'permissionConfig' }),
      fetchModuleRecords('users', { page: 1, pageSize: 200, usage: 'permissionConfig' }),
      fetchPermissionTree()
    ])
    roles.value = normalizeRows(rolePage).map((row) => ({
      ...row,
      id: String(row.id),
      name: row.role_name || '未命名角色',
      code: row.role_code || row.id
    }))
    users.value = normalizeRows(userPage).map((row) => ({
      ...row,
      id: String(row.id),
      name: row.real_name || row.username || '未命名用户',
      code: `${row.user_code || row.id} / ${row.role_name || '未分配角色'}`
    }))
    permissionTree.value = tree || []
    ensureSelectedSubject()
    await loadSelectedPermissions()
  } finally {
    loading.value = false
  }
}

function normalizeRows(page) {
  return Array.isArray(page) ? page : (page.records || [])
}

function ensureSelectedSubject() {
  if (subjects.value.some((subject) => subject.id === selectedSubjectId.value)) {
    return
  }
  selectedSubjectId.value = subjects.value[0]?.id || ''
}

async function loadSelectedPermissions() {
  if (!selectedSubjectId.value) {
    rolePermissionIds.value = []
    userPermission.value = { grantPermissionIds: [], denyPermissionIds: [] }
    return
  }
  if (mode.value === 'role') {
    rolePermissionIds.value = await fetchRolePermissionIds(selectedSubjectId.value)
    await nextTick()
    roleTreeRef.value?.setCheckedKeys(rolePermissionIds.value)
    return
  }
  userPermission.value = await fetchUserPermissionIds(selectedSubjectId.value)
  await nextTick()
  grantTreeRef.value?.setCheckedKeys(userPermission.value.grantPermissionIds || [])
  denyTreeRef.value?.setCheckedKeys(userPermission.value.denyPermissionIds || [])
}

function handleModeChange() {
  selectedSubjectId.value = ''
  ensureSelectedSubject()
  loadSelectedPermissions()
}

function selectSubject(subject) {
  selectedSubjectId.value = subject.id
  loadSelectedPermissions()
}

function checkedPermissionIds(treeRef) {
  const checked = treeRef?.getCheckedKeys(false) || []
  return checked.map((id) => Number(id)).filter(Boolean)
}

function syncUserTrees(source) {
  const grantIds = checkedPermissionIds(grantTreeRef.value)
  const denyIds = checkedPermissionIds(denyTreeRef.value)
  if (source === 'grant') {
    denyTreeRef.value?.setCheckedKeys(denyIds.filter((id) => !grantIds.includes(id)))
  } else {
    grantTreeRef.value?.setCheckedKeys(grantIds.filter((id) => !denyIds.includes(id)))
  }
}

async function savePermissions() {
  saving.value = true
  try {
    if (mode.value === 'role') {
      const permissionIds = checkedPermissionIds(roleTreeRef.value)
      rolePermissionIds.value = await updateRolePermissionIds(selectedSubjectId.value, permissionIds)
      await refreshCurrentSessionIfNeeded('role')
      ElMessage.success('角色权限已保存')
      return
    }
    const grantIds = checkedPermissionIds(grantTreeRef.value)
    const denyIds = checkedPermissionIds(denyTreeRef.value)
    userPermission.value = await updateUserPermissionIds(selectedSubjectId.value, grantIds, denyIds)
    await refreshCurrentSessionIfNeeded('user')
    ElMessage.success('用户特殊权限已保存')
  } finally {
    saving.value = false
  }
}

async function refreshCurrentSessionIfNeeded(scope) {
  const auth = getAuthToken()
  if (scope === 'user' && auth.userId !== selectedSubjectId.value) {
    return
  }
  if (scope === 'role') {
    const role = roles.value.find((item) => item.id === selectedSubjectId.value)
    if (!role || role.role_code !== auth.roleCode) {
      return
    }
  }
  saveAuthToken(await fetchSession())
}

onMounted(loadAll)
</script>

<style scoped>
.permission-page {
  min-height: 720px;
}

.permission-actions {
  align-items: center;
  display: flex;
  gap: 10px;
}

.permission-layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 18px;
}

.subject-list {
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 18px;
}

.subject-list button {
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

.subject-list button.active {
  background: #eff6ff;
  border-color: #93c5fd;
  color: #1d4ed8;
}

.subject-list span {
  color: #64748b;
  font-size: 12px;
}

.permission-editor {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.user-permission-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.tree-card {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  min-height: 560px;
  padding: 14px;
}

.deny-card {
  border-color: #fecaca;
}

.tree-title {
  align-items: baseline;
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

.tree-title span {
  color: #64748b;
  font-size: 12px;
}

.permission-node {
  align-items: center;
  display: flex;
  gap: 12px;
}

.permission-node small {
  color: #64748b;
}

@media (max-width: 1100px) {
  .permission-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .permission-layout,
  .user-permission-grid {
    grid-template-columns: 1fr;
  }

  .subject-list {
    border-bottom: 1px solid #e5e7eb;
    border-right: 0;
    padding-bottom: 14px;
    padding-right: 0;
  }
}
</style>
