import assert from 'node:assert/strict'

const storage = new Map()
globalThis.sessionStorage = {
  getItem: (key) => storage.has(key) ? storage.get(key) : null,
  setItem: (key, value) => storage.set(key, String(value)),
  removeItem: (key) => storage.delete(key)
}

const authStore = await import('../src/stores/auth-store.js')
const permissions = await import('../src/utils/permission-utils.js')

authStore.clearAuthToken()
authStore.saveAuthToken({
  tokenName: 'satoken',
  tokenValue: 'test-token',
  permissions: ['order:query', 'order:update', 'track:view'],
  menus: [
    {
      path: '/system',
      children: [
        { path: '/system/users', children: [] }
      ]
    },
    { path: '/orders', children: [] },
    { path: '/tracks', children: [] }
  ]
})

assert.equal(authStore.hasPermission('order:query'), true)
assert.equal(authStore.hasPermission('order:create'), false)
assert.equal(authStore.canVisit('/orders'), true)
assert.equal(authStore.canVisit('/orders/detail'), true)
assert.equal(authStore.canVisit('/fees'), false)
assert.equal(authStore.firstMenuPath(), '/system/users')

assert.equal(permissions.actionPermissionFromRoutePermission('order:query', 'update'), 'order:update')
assert.equal(permissions.canActionWithPermissions(['order:update'], 'order:query', 'update'), true)
assert.equal(permissions.canActionWithPermissions(['order:update'], 'order:query', 'delete'), false)

authStore.clearAuthToken()
assert.equal(authStore.hasPermission('order:query'), false)
assert.equal(authStore.canVisit('/orders'), false)

console.log('auth permission tests passed')
