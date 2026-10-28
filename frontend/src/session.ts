import type { Session } from './types'

const STORAGE_KEY = 'tokenfest.session'

function load(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as Session) : null
  } catch {
    return null
  }
}

let current: Session | null = load()

/** The logged-in user, readable outside React (used by the API client). */
export function getSession() {
  return current
}

export function storeSession(session: Session | null) {
  current = session
  try {
    if (session) localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
    else localStorage.removeItem(STORAGE_KEY)
  } catch {
    // Storage can be blocked (private mode); the session then lasts until reload
  }
}

/** Re-reads storage. Used by tests. */
export function reloadSession() {
  current = load()
}
