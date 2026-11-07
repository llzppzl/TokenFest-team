import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api } from '../api'
import { Badge, Button, ErrorMessage, Field, Input, Modal, PageHeader, Select, Table } from '../components/ui'
import type { ConnectionTest, Model, Provider, ProviderType } from '../types'

type FormState = {
  name: string
  type: ProviderType
  baseUrl: string
  apiKey: string
  priority: string
  enabled: boolean
  models: { modelName: string; inputPrice: string; outputPrice: string }[]
}

const DEFAULT_URLS: Record<ProviderType, string> = {
  OPENAI: 'https://api.openai.com/v1',
  ANTHROPIC: 'https://api.anthropic.com',
}

function toForm(p?: Provider): FormState {
  return {
    name: p?.name ?? '',
    type: p?.type ?? 'OPENAI',
    baseUrl: p?.baseUrl ?? DEFAULT_URLS.OPENAI,
    apiKey: '',
    priority: String(p?.priority ?? 100),
    enabled: p?.enabled ?? true,
    models: (p?.models ?? []).map((m) => ({
      modelName: m.modelName,
      inputPrice: String(m.inputPrice),
      outputPrice: String(m.outputPrice),
    })),
  }
}

/** Scheme + host + port, or null if the URL is not valid yet. */
function origin(url: string) {
  try {
    return new URL(url).origin
  } catch {
    return null
  }
}

/** A price must be filled in: an empty field would silently become $0. */
function parsePrice(value: string) {
  const trimmed = value.trim()
  if (trimmed === '') return NaN
  const n = Number(trimmed)
  return n >= 0 ? n : NaN
}

function ProviderForm({ provider, onSaved, onCancel }: { provider?: Provider; onSaved: () => void; onCancel: () => void }) {
  const [form, setForm] = useState<FormState>(() => toForm(provider))
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const set = (patch: Partial<FormState>) => setForm((f) => ({ ...f, ...patch }))
  // The saved key is only reused for the same server, so it can't be sent somewhere else
  const keyNeeded = !provider || origin(form.baseUrl) !== origin(provider.baseUrl)

  function setModel(i: number, patch: Partial<FormState['models'][number]>) {
    set({ models: form.models.map((m, j) => (j === i ? { ...m, ...patch } : m)) })
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const models: Model[] = form.models
      .filter((m) => m.modelName.trim())
      .map((m) => ({ modelName: m.modelName.trim(), inputPrice: parsePrice(m.inputPrice), outputPrice: parsePrice(m.outputPrice) }))
    if (models.some((m) => Number.isNaN(m.inputPrice) || Number.isNaN(m.outputPrice))) {
      setError('Every model needs an input and output price (0 or more)')
      return
    }
    const priority = form.priority.trim() === '' ? null : Number(form.priority)
    if (priority !== null && !Number.isInteger(priority)) {
      setError('Priority must be a whole number')
      return
    }
    const body = { ...form, priority, apiKey: form.apiKey || null, models }
    setBusy(true)
    try {
      await api(provider ? `/api/admin/providers/${provider.id}` : '/api/admin/providers', {
        method: provider ? 'PUT' : 'POST',
        body,
      })
      onSaved()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Name">
          <Input value={form.name} onChange={(e) => set({ name: e.target.value })} required />
        </Field>
        <Field label="API format">
          <Select
            value={form.type}
            onChange={(e) => {
              const type = e.target.value as ProviderType
              const urlUnchanged = Object.values(DEFAULT_URLS).includes(form.baseUrl)
              set({ type, baseUrl: urlUnchanged ? DEFAULT_URLS[type] : form.baseUrl })
            }}
          >
            <option value="OPENAI">OpenAI-compatible</option>
            <option value="ANTHROPIC">Anthropic</option>
          </Select>
        </Field>
        <Field
          label="Base URL"
          hint={form.type === 'OPENAI' ? 'Include /v1, e.g. https://api.deepseek.com/v1' : 'Without /v1'}
        >
          <Input value={form.baseUrl} onChange={(e) => set({ baseUrl: e.target.value })} required />
        </Field>
        <Field
          label="API key"
          hint={
            !provider ? undefined : keyNeeded ? 'Required because the server changed' : 'Leave empty to keep the current key'
          }
        >
          <Input
            type="password"
            value={form.apiKey}
            onChange={(e) => set({ apiKey: e.target.value })}
            required={keyNeeded}
            autoComplete="off"
          />
        </Field>
        <Field label="Priority" hint="Lower is tried first (empty = 100)">
          <Input type="number" step="1" value={form.priority} onChange={(e) => set({ priority: e.target.value })} />
        </Field>
        <label className="flex items-center gap-2 self-center text-sm">
          <input type="checkbox" checked={form.enabled} onChange={(e) => set({ enabled: e.target.checked })} />
          Enabled
        </label>
      </div>

      <div>
        <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
          <span className="text-sm font-medium text-gray-700">Models (price in USD per 1M tokens)</span>
          <Button
            type="button"
            variant="secondary"
            onClick={() => set({ models: [...form.models, { modelName: '', inputPrice: '', outputPrice: '' }] })}
          >
            + Add model
          </Button>
        </div>
        {form.models.length === 0 && <p className="text-sm text-gray-500">No models yet.</p>}
        {form.models.length > 0 && (
          <div className="mb-1 hidden grid-cols-[1fr_6rem_6rem_5.5rem] gap-2 text-xs font-medium text-gray-500 sm:grid">
            <span>Model name</span>
            <span>Input $ / 1M</span>
            <span>Output $ / 1M</span>
            <span />
          </div>
        )}
        <div className="space-y-3 sm:space-y-2">
          {form.models.map((m, i) => (
            <div key={i} className="grid grid-cols-2 gap-2 sm:grid-cols-[1fr_6rem_6rem_5.5rem]">
              <Input
                className="col-span-2 sm:col-span-1"
                placeholder="e.g. gpt-4o"
                aria-label={`Model ${i + 1} name`}
                value={m.modelName}
                onChange={(e) => setModel(i, { modelName: e.target.value })}
              />
              <Input
                placeholder="Input $"
                aria-label={`Model ${i + 1} input price per 1M tokens`}
                inputMode="decimal"
                value={m.inputPrice}
                onChange={(e) => setModel(i, { inputPrice: e.target.value })}
              />
              <Input
                placeholder="Output $"
                aria-label={`Model ${i + 1} output price per 1M tokens`}
                inputMode="decimal"
                value={m.outputPrice}
                onChange={(e) => setModel(i, { outputPrice: e.target.value })}
              />
              <Button
                type="button"
                variant="danger"
                className="col-span-2 sm:col-span-1"
                onClick={() => set({ models: form.models.filter((_, j) => j !== i) })}
              >
                Remove
              </Button>
            </div>
          ))}
        </div>
      </div>

      <ErrorMessage message={error} />
      <div className="flex justify-end gap-2">
        <Button type="button" variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" disabled={busy}>
          {busy ? 'Saving...' : 'Save'}
        </Button>
      </div>
    </form>
  )
}

function TestResult({ result }: { result: ConnectionTest | 'running' }) {
  if (result === 'running') return <span className="text-xs text-gray-500">Testing...</span>
  return (
    <span className={`text-xs ${result.ok ? 'text-green-700' : 'text-red-700'}`} title={result.models.join(', ')}>
      {result.ok ? `OK, ${result.latencyMs} ms, ${result.models.length} models` : result.message}
    </span>
  )
}

export default function ProvidersPage() {
  const [providers, setProviders] = useState<Provider[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<Provider | 'new' | null>(null)
  const [tests, setTests] = useState<Record<number, ConnectionTest | 'running'>>({})

  const load = useCallback(() => {
    api<Provider[]>('/api/admin/providers')
      .then((p) => {
        setProviders(p)
        setError(null)
      })
      .catch((e) => setError(e.message))
  }, [])

  useEffect(load, [load])

  async function remove(p: Provider) {
    if (!confirm(`Delete provider "${p.name}"? Its past usage is kept.`)) return
    try {
      await api(`/api/admin/providers/${p.id}`, { method: 'DELETE' })
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Delete failed')
    }
  }

  async function test(p: Provider) {
    setTests((t) => ({ ...t, [p.id]: 'running' }))
    try {
      const result = await api<ConnectionTest>(`/api/admin/providers/${p.id}/test`, { method: 'POST' })
      setTests((t) => ({ ...t, [p.id]: result }))
    } catch (e) {
      const message = e instanceof Error ? e.message : 'Test failed'
      setTests((t) => ({ ...t, [p.id]: { ok: false, status: 0, latencyMs: 0, message, models: [] } }))
    }
  }

  return (
    <div>
      <PageHeader title="Providers" action={<Button onClick={() => setEditing('new')}>+ Add provider</Button>} />
      <ErrorMessage message={error} />
      <Table headers={['Name', 'Format', 'Base URL', 'Priority', 'Models', 'Status', '']}>
        {providers === null && !error && (
          <tr>
            <td colSpan={7} className="px-4 py-6 text-center text-gray-500">
              Loading...
            </td>
          </tr>
        )}
        {providers?.length === 0 && (
          <tr>
            <td colSpan={7} className="px-4 py-6 text-center text-gray-500">
              No providers yet. Add one to start forwarding requests.
            </td>
          </tr>
        )}
        {providers?.map((p) => (
          <tr key={p.id}>
            <td className="whitespace-nowrap px-4 py-2 font-medium">{p.name}</td>
            <td className="px-4 py-2">
              <Badge color={p.type === 'OPENAI' ? 'blue' : 'purple'}>{p.type === 'OPENAI' ? 'OpenAI' : 'Anthropic'}</Badge>
            </td>
            <td className="max-w-xs truncate px-4 py-2 text-gray-600" title={p.baseUrl}>
              {p.baseUrl}
            </td>
            <td className="px-4 py-2">{p.priority}</td>
            <td className="min-w-40 px-4 py-2 text-gray-600">{p.models.map((m) => m.modelName).join(', ') || '—'}</td>
            <td className="space-y-1 px-4 py-2">
              <div>
                <Badge color={p.enabled ? 'green' : 'gray'}>{p.enabled ? 'Enabled' : 'Disabled'}</Badge>{' '}
                {p.coolingDown && (
                  <span title={`${p.consecutiveFailures} failures in a row; tried last for a short time`}>
                    <Badge color="amber">Cooling down</Badge>
                  </span>
                )}
              </div>
              {tests[p.id] && <TestResult result={tests[p.id]} />}
            </td>
            <td className="space-x-2 whitespace-nowrap px-4 py-2 text-right">
              <Button variant="secondary" onClick={() => test(p)} disabled={tests[p.id] === 'running'}>
                Test
              </Button>
              <Button variant="secondary" onClick={() => setEditing(p)}>
                Edit
              </Button>
              <Button variant="danger" onClick={() => remove(p)}>
                Delete
              </Button>
            </td>
          </tr>
        ))}
      </Table>
      {editing && (
        <Modal title={editing === 'new' ? 'Add provider' : `Edit ${editing.name}`} onClose={() => setEditing(null)}>
          <ProviderForm
            provider={editing === 'new' ? undefined : editing}
            onCancel={() => setEditing(null)}
            onSaved={() => {
              setEditing(null)
              load()
            }}
          />
        </Modal>
      )}
    </div>
  )
}
