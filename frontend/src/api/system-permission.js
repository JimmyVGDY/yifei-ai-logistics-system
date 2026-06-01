import http from './http'

export function fetchPermissionMenus() {
  return http.get('/system/permissions/menus')
}

export function fetchPermissionTree() {
  return http.get('/system/permissions/tree')
}

export function fetchRoleMenuIds(roleId) {
  return http.get(`/system/permissions/roles/${roleId}/menus`)
}

export function updateRoleMenuIds(roleId, menuIds) {
  return http.post(`/system/permissions/roles/${roleId}/menus`, { menuIds })
}

export function fetchRolePermissionIds(roleId) {
  return http.get(`/system/permissions/roles/${roleId}/permissions`)
}

export function updateRolePermissionIds(roleId, permissionIds) {
  return http.post(`/system/permissions/roles/${roleId}/permissions`, { permissionIds })
}

export function fetchUserPermissionIds(userId) {
  return http.get(`/system/permissions/users/${userId}/permissions`)
}

export function updateUserPermissionIds(userId, grantPermissionIds, denyPermissionIds) {
  return http.post(`/system/permissions/users/${userId}/permissions`, { grantPermissionIds, denyPermissionIds })
}
