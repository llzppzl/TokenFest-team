import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, budgetPercent, copyText, formatDate, formatMoney } from '../api'
import { Badge, Button, ErrorMessage, Field, Input, Modal, PageHeader, Table } from '../components/ui'
import type { ApiKey, Provider } from '../types'

type FormState = {
  name: string
  monthlyBudget: string
  rateLimitPerMinute: string
  enabled: boolean
  allowedProviderIds: number[]
}

function KeyForm({
  apiKey,
  providers,
  onSaved,
  onCancel,
}: {
  apiKey?: ApiKey
  providers: Provider[]
  onSaved: (createdKey?: string) => void
  onCancel: () => void
}) {
  const [form, setForm] = useState<FormState>({
    name: apiKey?.name ?? '',
    monthlyBudget: apiKey?.monthlyBudget?.toString() ?? '',
    rateLimitPerMinute: apiKey?.rateLimitPerMinute?.toString() ?? '',
    enabled: apiKey?.enabled ?? true,
    allowedProviderIds: apiKey?.allowedProviderIds ?? [],
  })
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const set = (patch: Partial<FormState>) => setForm((f) => ({ ...f, ...patch }))

  function toggleProvider(id: number) {
    set({
      allowedProviderIds: form.allowedProviderIds.includes(id)
        ? form.allowedProviderIds.filter((p) => p !== id)
        : [...form.allowedProviderIds, id],
    })
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    const body = {
      name: form.name,
      monthlyBudget: form.monthlyBudget === '' ? null : Number(form.monthlyBudget),
      rateLimitPerMinute: form.rateLimitPerMinute === '' ? null : Number(form.rateLimitPerMinute),
      enabled: form.enabled,
      allowedProviderIds: form.allowedProviderIds,
    }
    try {
      if (apiKey) {
        await api(`/api/admin/keys/${apiKey.id}`, { method: 'PUT', body })
        onSaved()
      } else {
        const created = await api<{ apiKey: string }>('/api/admin/keys', { method: 'POST', body })
        onSaved(created.apiKey)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <Field label="Name" hint="Who this key belongs to">
        <Input value={form.name} onChange={(e) => set({ name: e.target.value })} required />
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Monthly budget (USD)" hint="Empty = no limit">
          <Input type="number" min="0" step="0.01" value={form.monthlyBudget} onChange={(e) => set({ monthlyBudget: e.target.value })} />
        </Field>
        <Field label="Rate limit (requests/minute)" hint="Empty = no limit">
          <Input type="number" min="1" value={form.rateLimitPerMinute} onChange={(e) => set({ rateLimitPerMinute: e.target.value })} />
        </Field>
      </div>
      <div>
        <span className="text-sm font-medium text-gray-700">Allowed providers</span>
        <p className="text-xs text-gray-500">None selected = all providers</p>
        <div className="mt-2 flex flex-wrap gap-3">
          {providers.map((p) => (
            <label key={p.id} className="flex items-center gap-1 text-sm">
              <input type="checkbox" checked={form.allowedProviderIds.includes(p.id)} onChange={() => toggleProvider(p.id)} />
              {p.name}
            </label>
          ))}
        </div>
      </div>
      {apiKey && (
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={form.enabled} onChange={(e) => set({ enabled: e.target.checked })} />
          Enabled
        </label>
      )}
      <ErrorMessage message={error} />
      <div className="flex justify-end gap-2">
        <Button type="button" variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" disabled={busy}>
          {busy ? 'Saving...' : apiKey ? 'Save' : 'Create key'}
        </Button>
      </div>
    </form>
  )
}

function NewKeyDialog({ apiKey, title, onClose }: { apiKey: string; title: string; onClose: () => void }) {
  const [copyState, setCopyState] = useState<'idle' | 'copied' | 'failed'>('idle')
  const origin = window.location.origin
  return (
    <Modal title={title} onClose={onClose}>
      <p className="text-sm text-amber-800">Copy this key now. It won't be shown again.</p>
      <div className="mt-3 flex gap-2">
        <code className="flex-1 select-all break-all rounded bg-gray-100 px-3 py-2 text-sm">{apiKey}</code>
        <Button onClick={() => copyText(apiKey).then((ok) => setCopyState(ok ? 'copied' : 'failed'))}>
          {copyState === 'copied' ? 'Copied' : 'Copy'}
        </Button>
      </div>
      {copyState === 'failed' && (
        <p className="mt-2 text-sm text-red-700">Couldn't copy automatically. Select the key above and copy it by hand.</p>
      )}
      <p className="mt-4 text-sm text-gray-600">
        Use it as the API key with base URL <code>{origin}/v1</code> (OpenAI SDKs) or <code>{origin}</code> (Anthropic
        SDKs, Claude Code).
      </p>
      <div className="mt-5 text-right">
        <Button onClick={onClose}>Done</Button>
      </div>
    </Modal>
  )
}

function budgetCell(k: ApiKey) {
  if (k.monthlyBudget === null) return <span className="text-gray-500">{formatMoney(k.spentThisMonth)} / no limit</span>
  const percent = Math.min(100, budgetPercent(k.spentThisMonth, k.monthlyBudget))
  const color = percent >= 100 ? 'bg-red-500' : percent >= 80 ? 'bg-amber-500' : 'bg-green-500'
  return (
    <div className="min-w-32">
      <div className="text-xs text-gray-600">
        {formatMoney(k.spentThisMonth)} / {formatMoney(k.monthlyBudget)}
      </div>
      <div className="mt-1 h-1.5 rounded bg-gray-200">
        <div className={`h-1.5 rounded ${color}`} style={{ width: `${percent}%` }} />
      </div>
    </div>
  )
}

export default function KeysPage() {
  const [keys, setKeys] = useState<ApiKey[] | null>(null)
  const [providers, setProviders] = useState<Provider[]>([])
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<ApiKey | 'new' | null>(null)
  const [shownKey, setShownKey] = useState<{ key: string; title: string } | null>(null)

  const load = useCallback(() => {
    Promise.all([api<ApiKey[]>('/api/admin/keys'), api<Provider[]>('/api/admin/providers')])
      .then(([k, p]) => {
        setKeys(k)
        setProviders(p)
        setError(null)
      })
      .catch((e) => setError(e.message))
  }, [])

  useEffect(load, [load])

  async function remove(k: ApiKey) {
    if (!confirm(`Delete the key of "${k.name}"? This also deletes its usage history. Disabling keeps the history.`)) return
    try {
      await api(`/api/admin/keys/${k.id}`, { method: 'DELETE' })
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Delete failed')
    }
  }

  async function regenerate(k: ApiKey) {
    if (!confirm(`Give "${k.name}" a new key? The current key stops working right away. Budget and history stay.`)) return
    try {
      const created = await api<{ apiKey: string }>(`/api/admin/keys/${k.id}/regenerate`, { method: 'POST' })
      setShownKey({ key: created.apiKey, title: `New key for ${k.name}` })
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not create a new key')
    }
  }

  return (
    <div>
      <PageHeader title="Keys" action={<Button onClick={() => setEditing('new')}>+ New key</Button>} />
      <ErrorMessage message={error} />
      <Table headers={['Name', 'Key', 'This month', 'Rate limit', 'Last used', 'Status', '']}>
        {keys === null && !error && (
          <tr>
            <td colSpan={7} className="px-4 py-6 text-center text-gray-500">
              Loading...
            </td>
          </tr>
        )}
        {keys?.length === 0 && (
          <tr>
            <td colSpan={7} className="px-4 py-6 text-center text-gray-500">
              No keys yet. Create one for each team member.
            </td>
          </tr>
        )}
        {keys?.map((k) => (
          <tr key={k.id}>
            <td className="whitespace-nowrap px-4 py-2 font-medium">{k.name}</td>
            <td className="px-4 py-2 font-mono text-xs text-gray-600">{k.keyPrefix}…</td>
            <td className="px-4 py-2">{budgetCell(k)}</td>
            <td className="px-4 py-2">{k.rateLimitPerMinute ? `${k.rateLimitPerMinute}/min` : '—'}</td>
            <td className="px-4 py-2 text-gray-600">{formatDate(k.lastUsedAt)}</td>
            <td className="px-4 py-2">
              <Badge color={k.enabled ? 'green' : 'gray'}>{k.enabled ? 'Enabled' : 'Disabled'}</Badge>
            </td>
            <td className="space-x-2 whitespace-nowrap px-4 py-2 text-right">
              <Button variant="secondary" onClick={() => setEditing(k)}>
                Edit
              </Button>
              <Button variant="secondary" onClick={() => regenerate(k)} title="Replace a leaked or lost key">
                New secret
              </Button>
              <Button variant="danger" onClick={() => remove(k)}>
                Delete
              </Button>
            </td>
          </tr>
        ))}
      </Table>
      {editing && (
        <Modal title={editing === 'new' ? 'New key' : `Edit ${editing.name}`} onClose={() => setEditing(null)}>
          <KeyForm
            apiKey={editing === 'new' ? undefined : editing}
            providers={providers}
            onCancel={() => setEditing(null)}
            onSaved={(key) => {
              setEditing(null)
              if (key) setShownKey({ key, title: 'Key created' })
              load()
            }}
          />
        </Modal>
      )}
      {shownKey && <NewKeyDialog apiKey={shownKey.key} title={shownKey.title} onClose={() => setShownKey(null)} />}
    </div>
  )
}
