import { getSession } from './session'

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

// Called when the server says the login is no longer valid
let onUnauthorized: () => void = () => {}
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

/** Calls the backend and returns the JSON body. Throws ApiError with the server's message. */
export async function api<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const headers: Record<string, string> = {}
  const session = getSession()
  // Only protected endpoints get the token, so an old token can't break login or /api/info
  if (session && (path.startsWith('/api/admin') || path.startsWith('/api/user'))) {
    headers.Authorization = `Bearer ${session.token}`
  }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'

  const res = await fetch(path, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  })

  if (res.status === 401 && session && !path.startsWith('/api/auth')) {
    onUnauthorized()
  }
  if (!res.ok) {
    let message = `Request failed (${res.status})`
    try {
      const data = await res.json()
      if (data?.error) message = typeof data.error === 'string' ? data.error : data.error.message
    } catch {
      // body was not JSON
    }
    throw new ApiError(res.status, message)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

/** Downloads a file from a protected endpoint (the token can't go in a plain link). */
export async function downloadFile(path: string, filename: string) {
  const session = getSession()
  const res = await fetch(path, { headers: session ? { Authorization: `Bearer ${session.token}` } : {} })
  if (res.status === 401 && session) onUnauthorized()
  if (!res.ok) {
    let message = `Download failed (${res.status})`
    try {
      message = (await res.json()).error ?? message
    } catch {
      // not JSON
    }
    throw new ApiError(res.status, message)
  }
  const url = URL.createObjectURL(await res.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

export function formatMoney(value: number | null | undefined) {
  if (value === null || value === undefined) return '—'
  if (value === 0 || Math.abs(value) >= 0.01) return `$${value.toFixed(2)}`
  if (Math.abs(value) < 0.000001) return '<$0.000001'
  // Small amounts: up to 6 decimals without trailing zeros, e.g. $0.000027
  return `$${value.toFixed(6).replace(/0+$/, '')}`
}

/** Percent of a budget used. A $0 budget counts as used up, like on the server. */
export function budgetPercent(spent: number, budget: number) {
  return budget > 0 ? (spent / budget) * 100 : 100
}

/**
 * Copies text. `navigator.clipboard` only exists on HTTPS and localhost, so fall back to
 * the older copy command. Resolves false if both fail.
 */
export async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    // fall through
  }
  const area = document.createElement('textarea')
  area.value = text
  area.setAttribute('readonly', '')
  area.style.position = 'fixed'
  area.style.opacity = '0'
  document.body.appendChild(area)
  area.select()
  let ok = false
  try {
    ok = document.execCommand('copy')
  } catch {
    ok = false
  }
  document.body.removeChild(area)
  return ok
}

export function formatDate(value: string | null | undefined) {
  if (!value) return '—'
  return new Date(value).toLocaleString()
}
