import { reactive } from 'vue'

const authState = reactive(readAuthFromStorage())

export function saveAuthToken(payload) {
  const session = normalizeSession(payload)
  const nextState = {
    tokenName: session.tokenName || '',
    tokenValue: session.tokenValue || '',
    username: session.usernameMasked || session.username || '',
    userId: String(session.userId || session.loginId || ''),
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
    'auth.roleCode',
    'auth.roleName',
    'auth.permissions',
    'auth.menus'
  ].forEach((key) => localStorage.removeItem(key))
}

export function getAuthToken() {
  return authState
}

export function isAuthenticated() {
  return Boolean(authState.tokenName && authState.tokenValue)
}

export function canVisit(path) {
  if (!authState.menus.length) {
    return true
  }
  return flattenMenus(authState.menus).some((menu) => path === menu.path || path.startsWith(`${menu.path}/`))
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
    tokenName: localStorage.getItem('auth.tokenName') || '',
    tokenValue: localStorage.getItem('auth.tokenValue') || '',
    username: localStorage.getItem('auth.username') || '',
    userId: localStorage.getItem('auth.userId') || '',
    roleCode: localStorage.getItem('auth.roleCode') || '',
    roleName: localStorage.getItem('auth.roleName') || '',
    permissions: readJson('auth.permissions', []),
    menus: readJson('auth.menus', [])
  }
}

function writeAuthToStorage(state) {
  localStorage.setItem('auth.tokenName', state.tokenName)
  localStorage.setItem('auth.tokenValue', state.tokenValue)
  localStorage.setItem('auth.username', state.username)
  localStorage.setItem('auth.userId', state.userId)
  localStorage.setItem('auth.roleCode', state.roleCode)
  localStorage.setItem('auth.roleName', state.roleName)
  localStorage.setItem('auth.permissions', JSON.stringify(state.permissions))
  localStorage.setItem('auth.menus', JSON.stringify(state.menus))
}

function emptyAuth() {
  return {
    tokenName: '',
    tokenValue: '',
    username: '',
    userId: '',
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
    return JSON.parse(localStorage.getItem(key) || JSON.stringify(fallback))
  } catch (error) {
    return fallback
  }
}
