import { reactive } from 'vue'

const authState = reactive(readAuthFromStorage())

export function saveAuthToken(payload) {
  const session = normalizeSession(payload)
  const nextState = {
    tokenName: session.tokenName || '',
    tokenValue: session.tokenValue || '',
    username: session.usernameMasked || session.username || '',
    userId: String(session.userId || session.loginId || ''),
    userCode: session.userCode || '',
    roleCode: session.roleCode || '',
    roleName: session.roleName || '',
    permissions: session.permissions || [],
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

export function hasPermission(permission) {
  if (!permission) {
    return true
  }
  return authState.permissions.includes(permission)
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

function normalizeSession(payload) {
  if (!payload) {
    return {}
  }
  if (payload.data && payload.tokenName === undefined) {
    return payload.data
  }
  return payload
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
    permissions: readJson('auth.permissions', []),
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
    permissions: [],
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
