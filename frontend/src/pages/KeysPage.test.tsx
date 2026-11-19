import { fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import KeysPage from './KeysPage'

afterEach(() => {
  vi.unstubAllGlobals()
})

function json(status: number, body: unknown) {
  return Promise.resolve({ ok: status < 400, status, json: () => Promise.resolve(body) })
}

const devA = {
  id: 1,
  name: 'devA',
  keyPrefix: 'tf-abc1234',
  monthlyBudget: 100,
  rateLimitPerMinute: 60,
  enabled: true,
  allowedProviderIds: [],
  spentThisMonth: 85,
  createdAt: '2026-10-01T00:00:00Z',
  lastUsedAt: null,
}

test('lists keys with spending against budget', async () => {
  vi.stubGlobal('fetch', vi.fn((url: string) => json(200, url === '/api/admin/keys' ? [devA] : [])))
  render(<KeysPage />)
  expect(await screen.findByText('devA')).toBeInTheDocument()
  expect(screen.getByText('$85.00 / $100.00')).toBeInTheDocument()
  expect(screen.getByText('60/min')).toBeInTheDocument()
})

test('a new key is shown once after creation', async () => {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    if (url === '/api/admin/keys' && init?.method === 'POST') {
      return json(201, { key: { ...devA, id: 2, name: 'devB' }, apiKey: 'tf-full-secret-key' })
    }
    return json(200, [])
  })
  vi.stubGlobal('fetch', fetchMock)
  render(<KeysPage />)

  fireEvent.click(await screen.findByRole('button', { name: '+ New key' }))
  const dialog = screen.getByRole('dialog')
  fireEvent.change(within(dialog).getByLabelText(/^Name/), { target: { value: 'devB' } })
  fireEvent.change(within(dialog).getByLabelText(/^Monthly budget/), { target: { value: '25' } })
  fireEvent.click(within(dialog).getByRole('button', { name: 'Create key' }))

  expect(await screen.findByText('tf-full-secret-key')).toBeInTheDocument()
  expect(screen.getByText(/won't be shown again/)).toBeInTheDocument()
  const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')!
  expect(JSON.parse(post[1]!.body as string)).toMatchObject({ name: 'devB', monthlyBudget: 25, rateLimitPerMinute: null })
})
