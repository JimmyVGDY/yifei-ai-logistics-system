/**
 * @deprecated 请使用 auth-store 的 canModuleAction(module, action)。
 *             保留此函数仅用于向后兼容，新代码不应再依赖它。
 */
export function actionPermissionFromRoutePermission(routePermission, action) {
  if (!routePermission) {
    return ''
  }
  const index = routePermission.lastIndexOf(':')
  if (index > 0) {
    return `${routePermission.slice(0, index)}:${action}`
  }
  return routePermission
}

/**
 * @deprecated 请使用 auth-store 的 canModuleAction(module, action)。
 */
export function canActionWithPermissions(permissions, routePermission, action) {
  const permission = actionPermissionFromRoutePermission(routePermission, action)
  return !permission || permissions.includes(permission)
}
