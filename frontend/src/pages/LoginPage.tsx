import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../api'
import { useAuth } from '../authContext'
import { Button, ErrorMessage, Field, Input } from '../components/ui'
import type { Session } from '../types'

export default function LoginPage() {
  const [mode, setMode] = useState<'admin' | 'member'>('admin')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [apiKey, setApiKey] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const session =
        mode === 'admin'
          ? await api<Session>('/api/auth/login', { method: 'POST', body: { username, password } })
          : await api<Session>('/api/auth/key-login', { method: 'POST', body: { apiKey } })
      login(session)
      navigate(session.role === 'ADMIN' ? '/admin' : '/me')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed')
    } finally {
      setBusy(false)
    }
  }

  const tab = (value: 'admin' | 'member', label: string) => (
    <button
      type="button"
      onClick={() => {
        setMode(value)
        setError(null)
      }}
      className={`flex-1 border-b-2 pb-2 text-sm font-medium ${
        mode === value ? 'border-indigo-600 text-indigo-700' : 'border-transparent text-gray-500'
      }`}
    >
      {label}
    </button>
  )

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <form onSubmit={submit} className="w-full max-w-sm space-y-5 rounded-lg bg-white p-8 shadow">
        <div className="text-center">
          <h1 className="text-2xl font-bold">TokenFest</h1>
          <p className="mt-1 text-sm text-gray-500">LLM API gateway</p>
        </div>
        <div className="flex gap-4">
          {tab('admin', 'Admin')}
          {tab('member', 'Team member')}
        </div>
        {mode === 'admin' ? (
          <>
            <Field label="Username">
              <Input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" required />
            </Field>
            <Field label="Password">
              <Input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                required
              />
            </Field>
          </>
        ) : (
          <Field label="API key" hint="The tf-... key your admin gave you">
            <Input type="password" value={apiKey} onChange={(e) => setApiKey(e.target.value)} required />
          </Field>
        )}
        <ErrorMessage message={error} />
        <Button type="submit" disabled={busy} className="w-full">
          {busy ? 'Logging in...' : 'Log in'}
        </Button>
      </form>
    </div>
  )
}
