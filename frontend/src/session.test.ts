import { describe, it, expect, beforeEach, vi } from 'vitest'
import { getSession, storeSession } from './session'

describe('Session management', () => {
  const storage = new Map<string, string>()

  beforeEach(() => {
    storage.clear()

    vi.stubGlobal('localStorage', {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => storage.set(key, value),
      removeItem: (key: string) => storage.delete(key),
    })

    storeSession(null)
  })

  it('stores a user session', () => {
    const session = {
      token: 'test-token',
      role: 'USER' as const,
      name: 'Test User',
    }

    storeSession(session)
    expect(getSession()).toEqual(session)
  })

  it('clears a user session', () => {
    storeSession({
      token: 'test-token',
      role: 'USER',
      name: 'Test User',
    })

    storeSession(null)
    expect(getSession()).toBeNull()
  })

  it('returns null when no session is stored', () => {
    expect(getSession()).toBeNull()
  })
})
