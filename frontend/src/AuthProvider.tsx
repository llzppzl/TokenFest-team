import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { AuthContext } from './authContext'
import { getSession, storeSession } from './session'
import type { Session } from './types'

export default function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(getSession)

  const login = useCallback((s: Session) => {
    storeSession(s)
    setSession(s)
  }, [])

  const logout = useCallback(() => {
    storeSession(null)
    setSession(null)
  }, [])

  const value = useMemo(() => ({ session, login, logout }), [session, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
