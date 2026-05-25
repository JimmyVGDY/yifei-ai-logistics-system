export function saveAuthToken(session) {
  localStorage.setItem('auth.tokenName', session.tokenName)
  localStorage.setItem('auth.tokenValue', session.tokenValue)
  localStorage.setItem('auth.username', session.username)
}

export function clearAuthToken() {
  localStorage.removeItem('auth.tokenName')
  localStorage.removeItem('auth.tokenValue')
  localStorage.removeItem('auth.username')
}

export function getAuthToken() {
  return {
    tokenName: localStorage.getItem('auth.tokenName'),
    tokenValue: localStorage.getItem('auth.tokenValue'),
    username: localStorage.getItem('auth.username')
  }
}

export function isAuthenticated() {
  const { tokenName, tokenValue } = getAuthToken()
  return Boolean(tokenName && tokenValue)
}
