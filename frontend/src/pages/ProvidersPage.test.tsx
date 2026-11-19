import { fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import ProvidersPage from './ProvidersPage'

afterEach(() => {
  vi.unstubAllGlobals()
})

function json(status: number, body: unknown) {
  return Promise.resolve({ ok: status < 400, status, json: () => Promise.resolve(body) })
}

test('switching to Anthropic changes the default base URL and server errors are shown', async () => {
  const fetchMock = vi.fn((_url: string, init?: RequestInit) =>
    init?.method === 'POST' ? json(409, { error: "A provider named 'x' already exists" }) : json(200, []),
  )
  vi.stubGlobal('fetch', fetchMock)
  render(<ProvidersPage />)

  fireEvent.click(await screen.findByRole('button', { name: '+ Add provider' }))
  const dialog = screen.getByRole('dialog')
  fireEvent.change(within(dialog).getByLabelText('API format'), { target: { value: 'ANTHROPIC' } })
  expect(within(dialog).getByDisplayValue('https://api.anthropic.com')).toBeInTheDocument()

  fireEvent.change(within(dialog).getByLabelText('Name'), { target: { value: 'x' } })
  fireEvent.change(within(dialog).getByLabelText(/^API key/), { target: { value: 'sk-1' } })
  fireEvent.click(within(dialog).getByRole('button', { name: 'Save' }))

  expect(await within(dialog).findByText("A provider named 'x' already exists")).toBeInTheDocument()
  const body = JSON.parse(fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')![1]!.body as string)
  expect(body).toMatchObject({ name: 'x', type: 'ANTHROPIC', baseUrl: 'https://api.anthropic.com', apiKey: 'sk-1' })
})
