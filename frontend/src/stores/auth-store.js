import { reactive } from 'vue'

const authState = reactive(readAuthFromStorage())

export function saveAuthToken(payload) {
  const session = normalizeSession(payload)
  const permissions = normalizePermissions(session.permissions)
  const nextState = {
    tokenName: session.tokenName || '',
    tokenValue: session.tokenValue || '',
    username: session.usernameMasked || session.username || '',
    userId: String(session.userId || session.loginId || ''),
    userCode: session.userCode || '',
    roleCode: session.roleCode || '',
    roleName: session.roleName || '',
    permissions,
    menus: session.menus || []
  }
  Object.assign(authState, nextState)
  writeAuthToStorage(nextState)
}

export function clearAuthToken() {
  Object.assign(authState, emptyAuth())
  ;[
    'auth.tokenName',
    'auth.tokenValue',
    'auth.username',
    'auth.userId',
    'auth.userCode',
    'auth.roleCode',
    'auth.roleName',
    'auth.permissions',
    'auth.menus'
  ].forEach((key) => sessionStorage.removeItem(key))
}

export function getAuthToken() {
  return authState
}

export function isAuthenticated() {
  return Boolean(authState.tokenName && authState.tokenValue)
}

export function canVisit(path) {
  if (!authState.menus.length) {
    return false
  }
  return flattenMenus(authState.menus).some((menu) => {
    if (path === menu.path) {
      return true
    }
    return !menu.children?.length && path.startsWith(`${menu.path}/`)
  })
}

/**
 * 检查是否拥有某个权限码。
 * 支持结构化权限对象：{ module: { actions:[], columns:[] }, _standalone: { actions:[], columns:[] } }
 * 同时兼容旧的扁平数组格式（自动升级）。
 *
 * @param {string} permission 权限码，如 'order:query'、'ai:chat'
 * @returns {boolean}
 */
export function hasPermission(permission) {
  if (!permission) {
    return true
  }
  const perms = authState.permissions

  // 旧版扁平数组格式兼容
  if (Array.isArray(perms)) {
    return perms.includes(permission)
  }

  // 结构化对象格式
  // 1. 检查 _standalone
  if (perms._standalone?.actions?.includes(permission)) {
    return true
  }

  // 2. 解析 module:action
  const idx = permission.lastIndexOf(':')
  if (idx > 0) {
    const module = permission.substring(0, idx)
    const action = permission.substring(idx + 1)
    return perms[module]?.actions?.includes(action) ?? false
  }

  return false
}

/**
 * 检查用户在指定模块是否有某个操作权限。
 *
 * @param {string} module 模块标识，如 'order'
 * @param {string} action 操作名，如 'create'
 * @returns {boolean}
 */
export function canModuleAction(module, action) {
  if (!module || !action) {
    return false
  }
  const perms = authState.permissions
  const backendModule = resolveBackendModule(module)
  if (Array.isArray(perms)) {
    return hasPermission(`${backendModule}:${action}`)
  }
  return perms[backendModule]?.actions?.includes(action) ?? false
}

/**
 * 检查用户在指定模块是否有权看到某个列。
 *
 * @param {string} module 模块标识，如 'order'
 * @param {string} fieldName 列字段名（snake_case），如 'total_amount'
 * @returns {boolean}
 */
export function canShowColumn(module, fieldName) {
  if (!module || !fieldName) {
    return false
  }
  const perms = authState.permissions
  const backendModule = resolveBackendModule(module)
  if (Array.isArray(perms)) {
    return hasPermission(`${backendModule}:column:${fieldName}`)
  }
  const scope = perms[backendModule]
  if (!scope) {
    return false
  }
  if (Array.isArray(scope.columns) && scope.columns.length > 0) {
    return scope.columns.includes(fieldName)
  }
  // 列权限按最小授权原则处理：空 columns 表示当前会话没有可展示列，不能用 view/manage 兜底放开。
  return false
}

/**
 * 前端路由 module 名 → 后端权限模块键的映射。
 * 前端使用复数形式（如 orders、customers），后端 StandardColumnRegistry 使用单数或 system:xxx 形式。
 */
const FRONTEND_TO_BACKEND_MODULE = {
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
  users: 'system:user',
  roles: 'system:role',
  operationLogs: 'system:log',
  files: 'file',
  dashboard: 'dashboard',
  resource: 'resource',
  ai: 'ai',
  system: 'system:permission'
}

function resolveBackendModule(frontendModule) {
  return FRONTEND_TO_BACKEND_MODULE[frontendModule] || frontendModule
}

export function firstMenuPath() {
  return flattenMenus(authState.menus).find((menu) => menu.path && menu.path !== '/system')?.path || '/dashboard'
}

export function hasMenus() {
  return flattenMenus(authState.menus).some((menu) => Boolean(menu.path))
}

export function isSessionChecked() { return sessionStorage.getItem('auth.sessionChecked') === '1' }
export function markSessionChecked() { sessionStorage.setItem('auth.sessionChecked', '1') }
export function resetSessionChecked() { sessionStorage.removeItem('auth.sessionChecked') }

// ==================== 内部工具 ====================

function normalizeSession(payload) {
  if (!payload) {
    return {}
  }
  if (payload.data && payload.tokenName === undefined) {
    return payload.data
  }
  return payload
}

/**
 * 将后端返回的结构化权限标准化。
 * 兼容旧版扁平数组格式（作为 fallback）。
 */
function normalizePermissions(permissions) {
  if (!permissions) {
    return {}
  }
  // 已是结构化的对象格式
  if (!Array.isArray(permissions) && typeof permissions === 'object') {
    return permissions
  }
  // 旧版扁平数组格式 → 不做转换（hasPermission 兼容 Array）
  return permissions
}

function readAuthFromStorage() {
  return {
    tokenName: sessionStorage.getItem('auth.tokenName') || '',
    tokenValue: sessionStorage.getItem('auth.tokenValue') || '',
    username: sessionStorage.getItem('auth.username') || '',
    userId: sessionStorage.getItem('auth.userId') || '',
    userCode: sessionStorage.getItem('auth.userCode') || '',
    roleCode: sessionStorage.getItem('auth.roleCode') || '',
    roleName: sessionStorage.getItem('auth.roleName') || '',
    permissions: readJson('auth.permissions', {}),
    menus: readJson('auth.menus', [])
  }
}

function writeAuthToStorage(state) {
  sessionStorage.setItem('auth.tokenName', state.tokenName)
  sessionStorage.setItem('auth.tokenValue', state.tokenValue)
  sessionStorage.setItem('auth.username', state.username)
  sessionStorage.setItem('auth.userId', state.userId)
  sessionStorage.setItem('auth.userCode', state.userCode)
  sessionStorage.setItem('auth.roleCode', state.roleCode)
  sessionStorage.setItem('auth.roleName', state.roleName)
  sessionStorage.setItem('auth.permissions', JSON.stringify(state.permissions))
  sessionStorage.setItem('auth.menus', JSON.stringify(state.menus))
}

function emptyAuth() {
  return {
    tokenName: '',
    tokenValue: '',
    username: '',
    userId: '',
    userCode: '',
    roleCode: '',
    roleName: '',
    permissions: {},
    menus: []
  }
}

function flattenMenus(menus) {
  return menus.flatMap((menu) => [menu, ...flattenMenus(menu.children || [])])
}

function readJson(key, fallback) {
  try {
    return JSON.parse(sessionStorage.getItem(key) || JSON.stringify(fallback))
  } catch (error) {
    return fallback
  }
}
