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

export function canActionWithPermissions(permissions, routePermission, action) {
  const permission = actionPermissionFromRoutePermission(routePermission, action)
  return !permission || permissions.includes(permission)
}
