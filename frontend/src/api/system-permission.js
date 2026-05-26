import http from './http'

export function fetchPermissionMenus() {
  return http.get('/system/permissions/menus')
}

export function fetchRoleMenuIds(roleId) {
  return http.get(`/system/permissions/roles/${roleId}/menus`)
}

export function updateRoleMenuIds(roleId, menuIds) {
  return http.post(`/system/permissions/roles/${roleId}/menus`, { menuIds })
}
