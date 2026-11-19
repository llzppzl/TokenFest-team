import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import App from './App'
import { reloadSession } from './session'

beforeEach(() => {
  localStorage.clear()
  reloadSession()
  window.history.pushState({}, '', '/')
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function jsonResponse(status: number, body: unknown) {
  return Promise.resolve({ ok: status < 400, status, json: () => Promise.resolve(body) })
}

test('redirects to the login page when not logged in', () => {
  render(<App />)
  expect(screen.getByRole('button', { name: 'Log in' })).toBeInTheDocument()
})

test('admin login opens the overview page', async () => {
  const fetchMock = vi.fn((url: string) => {
    if (url === '/api/auth/login') return jsonResponse(200, { token: 't', role: 'ADMIN', name: 'admin' })
    if (url.startsWith('/api/admin/stats/summary'))
      return jsonResponse(200, { from: '2026-10-01', to: '2026-10-07', totalCost: 1.5, requests: 3, errors: 0, inputTokens: 10, outputTokens: 5, activeKeys: 1 })
    if (url.startsWith('/api/admin/stats/daily')) return jsonResponse(200, [])
    if (url === '/api/admin/keys') return jsonResponse(200, [])
    return jsonResponse(404, {})
  })
  vi.stubGlobal('fetch', fetchMock)
  render(<App />)

  fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'admin' } })
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'secret' } })
  fireEvent.click(screen.getByRole('button', { name: 'Log in' }))

  expect(await screen.findByText('$1.50')).toBeInTheDocument()
  expect(screen.getByText('No usage in this period.')).toBeInTheDocument()
  const providersCall = fetchMock.mock.calls.find(([url]) => url === '/api/admin/keys') as unknown as [
    string,
    RequestInit,
  ]
  expect((providersCall[1].headers as Record<string, string>).Authorization).toBe('Bearer t')
})

test('shows the server error on failed login', async () => {
  vi.stubGlobal('fetch', vi.fn(() => jsonResponse(401, { error: 'Wrong username or password' })))
  render(<App />)
  fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'admin' } })
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'bad' } })
  fireEvent.click(screen.getByRole('button', { name: 'Log in' }))
  expect(await screen.findByText('Wrong username or password')).toBeInTheDocument()
})

test('team member login opens their own dashboard', async () => {
  const fetchMock = vi.fn((url: string) => {
    if (url === '/api/auth/key-login') return jsonResponse(200, { token: 'u', role: 'USER', name: 'devA' })
    if (url === '/api/user/me')
      return jsonResponse(200, {
        keyId: 1,
        name: 'devA',
        keyPrefix: 'tf-abc1234',
        monthlyBudget: 50,
        spentThisMonth: 40,
        rateLimitPerMinute: 30,
        month: '2026-10',
      })
    if (url.startsWith('/api/user/stats/summary'))
      return jsonResponse(200, { from: '2026-10-01', to: '2026-10-07', totalCost: 40, requests: 9, errors: 0, inputTokens: 1, outputTokens: 1, activeKeys: 1 })
    if (url.startsWith('/api/user/stats/daily')) return jsonResponse(200, [])
    if (url.startsWith('/api/user/requests')) return jsonResponse(200, { items: [], page: 0, size: 50, total: 0 })
    return jsonResponse(404, {})
  })
  vi.stubGlobal('fetch', fetchMock)
  render(<App />)

  fireEvent.click(screen.getByRole('button', { name: 'Team member' }))
  fireEvent.change(screen.getByLabelText(/^API key/), { target: { value: 'tf-abc' } })
  fireEvent.click(screen.getByRole('button', { name: 'Log in' }))

  expect(await screen.findByText('This month (2026-10)')).toBeInTheDocument()
  expect(screen.getByText('Rate limit: 30 requests/minute')).toBeInTheDocument()
  expect(screen.getByLabelText('80% of budget used')).toBeInTheDocument()
  expect(fetchMock.mock.calls.some(([url]) => String(url).startsWith('/api/admin'))).toBe(false)
})
