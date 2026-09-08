/** 运营 / 质检同域，登录态放 sessionStorage，标签页互不影响。 */

const KEYS = ['token', 'role', 'tenantId', 'name', 'avatarId']

function takeLegacy() {
  if (sessionStorage.getItem('token')) {
    KEYS.forEach((k) => localStorage.removeItem(k))
    return
  }
  const token = localStorage.getItem('token')
  if (!token) {
    return
  }
  KEYS.forEach((k) => {
    const v = localStorage.getItem(k)
    if (v != null) {
      sessionStorage.setItem(k, v)
    }
    localStorage.removeItem(k)
  })
}

takeLegacy()

export function getToken() {
  return sessionStorage.getItem('token') || ''
}

export function getRole() {
  return sessionStorage.getItem('role') || ''
}

export function setAuth({ token, role, tenantId, name, avatarId }) {
  sessionStorage.setItem('token', token || '')
  sessionStorage.setItem('role', role || '')
  sessionStorage.setItem('tenantId', tenantId == null ? '' : String(tenantId))
  sessionStorage.setItem('name', name || '')
  sessionStorage.setItem('avatarId', avatarId == null ? '' : String(avatarId))
  KEYS.forEach((k) => localStorage.removeItem(k))
}

export function clearAuth() {
  KEYS.forEach((k) => {
    sessionStorage.removeItem(k)
    localStorage.removeItem(k)
  })
}
