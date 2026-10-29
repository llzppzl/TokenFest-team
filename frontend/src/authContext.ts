import { createContext, useContext } from 'react'
import type { Session } from './types'

export type AuthContextValue = {
  session: Session | null
  login: (session: Session) => void
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
