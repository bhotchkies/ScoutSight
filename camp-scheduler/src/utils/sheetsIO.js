/**
 * Google Sheets sync utilities.
 *
 * All operations use GET requests (not POST) to avoid the Apps Script redirect issue:
 * Google redirects the initial /exec URL to the real execution URL, and browsers do
 * not forward POST bodies through redirects. GET with query params works reliably
 * from both file:// and http:// origins.
 *
 * Endpoints (handled by the troop's Apps Script web app):
 *   getAll       — returns { selections, locks }
 *   acquireLock  — atomically lock a scout; returns { ok, lockedBy? }
 *   releaseLock  — write selections and release lock
 *   dropLock     — release lock without writing (cancel/back)
 *   ping         — refresh lock timestamp (keepalive while editing)
 *
 * Lock expiry: locks older than 60 s auto-expire server-side (dead-tab protection).
 * Ping interval in App.jsx: every 5 s while editing.
 */

const TIMEOUT_MS = 15000

async function get(url, params) {
  const qs = new URLSearchParams({ ...params, t: Date.now() }).toString()
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)
  try {
    const res = await fetch(`${url}?${qs}`, { signal: controller.signal })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const data = await res.json()
    if (data.error) throw new Error(data.error)
    return data
  } finally {
    clearTimeout(timer)
  }
}

/**
 * Test connection and fetch initial state.
 * Returns { selections: {[memberId]: {morning,freeTime}}, locks: {[memberId]: deviceId} }
 */
export async function sheetsConnect(url) {
  return get(url, { action: 'getAll' })
}

/**
 * Poll for current state (selections + active locks).
 */
export async function sheetsGetAll(url) {
  return get(url, { action: 'getAll' })
}

/**
 * Attempt to lock a scout for editing.
 * Returns { ok: boolean, lockedBy?: string }
 */
export async function sheetsAcquireLock(url, scoutId, deviceId) {
  return get(url, { action: 'acquireLock', scoutId, deviceId })
}

/**
 * Write a scout's selections to the sheet and release the lock.
 * Pass selections=null to release without writing.
 */
export async function sheetsReleaseLock(url, scoutId, deviceId, selections) {
  return get(url, {
    action:         'releaseLock',
    scoutId,
    deviceId,
    selectionsJson: JSON.stringify(selections ?? null)
  })
}

/**
 * Drop a lock without writing selections (Back / cancel).
 */
export async function sheetsDropLock(url, scoutId, deviceId) {
  return get(url, { action: 'dropLock', scoutId, deviceId })
}

/**
 * Refresh a lock's timestamp to prevent expiry while the user is still editing.
 */
export async function sheetsPing(url, scoutId, deviceId) {
  return get(url, { action: 'ping', scoutId, deviceId })
}
