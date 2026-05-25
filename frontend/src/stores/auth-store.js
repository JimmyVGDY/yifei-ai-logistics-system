export function saveAuthToken(session) {
  localStorage.setItem('auth.tokenName', session.tokenName)
  localStorage.setItem('auth.tokenValue', session.tokenValue)
  localStorage.setItem('auth.username', session.usernameMasked || session.username)
  localStorage.setItem('auth.userId', session.userId || session.loginId)
  localStorage.setItem('auth.roleCode', session.roleCode || '')
  localStorage.setItem('auth.roleName', session.roleName || '')
  localStorage.setItem('auth.permissions', JSON.stringify(session.permissions || []))
  localStorage.setItem('auth.menus', JSON.stringify(session.menus || []))
}

export function clearAuthToken() {
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
  return {
    tokenName: localStorage.getItem('auth.tokenName'),
    tokenValue: localStorage.getItem('auth.tokenValue'),
    username: localStorage.getItem('auth.username'),
    userId: localStorage.getItem('auth.userId'),
    roleCode: localStorage.getItem('auth.roleCode'),
    roleName: localStorage.getItem('auth.roleName'),
    permissions: readJson('auth.permissions', []),
    menus: readJson('auth.menus', [])
  }
}

export function isAuthenticated() {
  const { tokenName, tokenValue } = getAuthToken()
  return Boolean(tokenName && tokenValue)
}

export function canVisit(path) {
  const { menus } = getAuthToken()
  if (!menus.length) {
    return true
  }
  return flattenMenus(menus).some((menu) => path === menu.path || path.startsWith(`${menu.path}/`))
}

export function firstMenuPath() {
  return flattenMenus(getAuthToken().menus).find((menu) => menu.path && menu.path !== '/system')?.path || '/dashboard'
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
