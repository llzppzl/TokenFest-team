import { useCallback, useEffect, useState } from 'react'
import { api, formatDate, formatMoney } from '../api'
import { Badge, Button, ErrorMessage, Input, Modal, PageHeader, Select, Table } from '../components/ui'
import { formatNumber } from '../dates'
import type { ApiKey, Page, RequestDetail, RequestRow } from '../types'

const PAGE_SIZE = 50

function StatusBadge({ code }: { code: number }) {
  return <Badge color={code < 400 ? 'green' : code === 429 ? 'amber' : 'red'}>{code}</Badge>
}

function pretty(body: string | null) {
  if (!body) return '(empty)'
  try {
    return JSON.stringify(JSON.parse(body), null, 2)
  } catch {
    return body // streams and truncated bodies are shown as they are
  }
}

function RequestDetailModal({ id, basePath, onClose }: { id: number; basePath: string; onClose: () => void }) {
  const [detail, setDetail] = useState<RequestDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    api<RequestDetail>(`${basePath}/${id}`)
      .then(setDetail)
      .catch((e) => setError(e.message))
  }, [id, basePath])
  const r = detail?.request
  return (
    <Modal title={`Request #${id}`} onClose={onClose}>
      <ErrorMessage message={error} />
      {r && (
        <div className="space-y-4 text-sm">
          <dl className="grid grid-cols-2 gap-x-4 gap-y-1 sm:grid-cols-3">
            {[
              ['Time', formatDate(r.createdAt)],
              ['Key', r.keyName],
              ['Model', r.model ?? '—'],
              ['Provider', r.providerName ?? '—'],
              ['Endpoint', r.apiFormat === 'OPENAI' ? 'OpenAI' : 'Anthropic'],
              ['Streamed', r.streamed ? 'Yes' : 'No'],
              ['Tokens', `${r.inputTokens} in / ${r.outputTokens} out`],
              ['Cost', formatMoney(r.cost)],
              ['Latency', `${r.latencyMs} ms`],
              ['Attempts', String(r.attempts)],
            ].map(([label, value]) => (
              <div key={label}>
                <dt className="text-gray-500">{label}</dt>
                <dd className="font-medium">{value}</dd>
              </div>
            ))}
            <div>
              <dt className="text-gray-500">Status</dt>
              <dd>
                <StatusBadge code={r.statusCode} />
              </dd>
            </div>
          </dl>
          {r.errorMessage && <p className="rounded bg-amber-50 px-3 py-2 text-amber-900">{r.errorMessage}</p>}
          <div>
            <h3 className="mb-1 font-medium">Request</h3>
            <pre className="max-h-64 overflow-auto rounded bg-gray-900 p-3 text-xs text-gray-100">{pretty(detail.requestBody)}</pre>
          </div>
          <div>
            <h3 className="mb-1 font-medium">Response</h3>
            <pre className="max-h-64 overflow-auto rounded bg-gray-900 p-3 text-xs text-gray-100">{pretty(detail.responseBody)}</pre>
          </div>
        </div>
      )}
    </Modal>
  )
}

/**
 * Request log. Admins see everyone's requests and can filter by key; team members see
 * only their own (basePath /api/user/requests).
 */
export function RequestsTable({ basePath, showKeyFilter }: { basePath: string; showKeyFilter: boolean }) {
  const [days, setDays] = useState(7)
  const [keyId, setKeyId] = useState('')
  const [model, setModel] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<RequestRow> | null>(null)
  const [keys, setKeys] = useState<ApiKey[]>([])
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<number | null>(null)
  const [appliedModel, setAppliedModel] = useState('')

  useEffect(() => {
    if (showKeyFilter) api<ApiKey[]>('/api/admin/keys').then(setKeys).catch(() => {})
  }, [showKeyFilter])

  const load = useCallback(() => {
    const params = new URLSearchParams({ days: String(days), page: String(page), size: String(PAGE_SIZE) })
    if (keyId) params.set('keyId', keyId)
    if (appliedModel) params.set('model', appliedModel)
    if (status) params.set('status', status)
    api<Page<RequestRow>>(`${basePath}?${params}`)
      .then((d) => {
        setData(d)
        setError(null)
      })
      .catch((e) => setError(e.message))
  }, [basePath, days, keyId, appliedModel, status, page])

  useEffect(load, [load])

  const pages = data ? Math.max(1, Math.ceil(data.total / PAGE_SIZE)) : 1
  const resetPage = <T,>(setter: (v: T) => void) => (v: T) => {
    setter(v)
    setPage(0)
  }

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-end gap-2">
        <Select value={days} onChange={(e) => resetPage(setDays)(Number(e.target.value))} className="w-36" aria-label="Period">
          <option value={1}>Today</option>
          <option value={7}>Last 7 days</option>
          <option value={30}>Last 30 days</option>
          <option value={90}>Last 90 days</option>
        </Select>
        {showKeyFilter && (
          <Select value={keyId} onChange={(e) => resetPage(setKeyId)(e.target.value)} className="w-40" aria-label="Key">
            <option value="">All keys</option>
            {keys.map((k) => (
              <option key={k.id} value={k.id}>
                {k.name}
              </option>
            ))}
          </Select>
        )}
        <Select value={status} onChange={(e) => resetPage(setStatus)(e.target.value)} className="w-36" aria-label="Status">
          <option value="">All statuses</option>
          <option value="success">Success</option>
          <option value="error">Errors</option>
          <option value="429">429 only</option>
        </Select>
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault()
            setAppliedModel(model.trim())
            setPage(0)
          }}
        >
          <Input placeholder="Model" value={model} onChange={(e) => setModel(e.target.value)} className="w-44" aria-label="Model" />
          <Button type="submit" variant="secondary">
            Filter
          </Button>
        </form>
        <Button variant="secondary" onClick={load} className="ml-auto">
          Refresh
        </Button>
      </div>
      <ErrorMessage message={error} />
      <Table headers={['Time', ...(showKeyFilter ? ['Key'] : []), 'Model', 'Provider', 'Tokens', 'Cost', 'Latency', 'Status']}>
        {data?.items.length === 0 && (
          <tr>
            <td colSpan={8} className="px-4 py-6 text-center text-gray-500">
              No requests found.
            </td>
          </tr>
        )}
        {data?.items.map((r) => (
          <tr key={r.id} onClick={() => setSelected(r.id)} className="cursor-pointer hover:bg-gray-50">
            <td className="whitespace-nowrap px-4 py-2 text-gray-600">{formatDate(r.createdAt)}</td>
            {showKeyFilter && <td className="whitespace-nowrap px-4 py-2 font-medium">{r.keyName}</td>}
            <td className="whitespace-nowrap px-4 py-2">
              {r.model ?? '—'}
              {r.streamed && <span className="ml-1 text-xs text-gray-400">stream</span>}
            </td>
            <td className="whitespace-nowrap px-4 py-2 text-gray-600">
              {r.providerName ?? '—'}
              {r.attempts > 1 && <span className="ml-1 text-xs text-amber-700">({r.attempts} tries)</span>}
            </td>
            <td className="whitespace-nowrap px-4 py-2">
              {formatNumber(r.inputTokens)} / {formatNumber(r.outputTokens)}
            </td>
            <td className="px-4 py-2">{formatMoney(r.cost)}</td>
            <td className="whitespace-nowrap px-4 py-2 text-gray-600">{r.latencyMs} ms</td>
            <td className="px-4 py-2">
              <StatusBadge code={r.statusCode} />
            </td>
          </tr>
        ))}
      </Table>
      <div className="mt-3 flex items-center justify-between text-sm text-gray-600">
        <span>{data ? `${data.total} requests` : ''}</span>
        <div className="flex items-center gap-2">
          <Button variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
            Previous
          </Button>
          <span>
            {page + 1} / {pages}
          </span>
          <Button variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>
            Next
          </Button>
        </div>
      </div>
      {selected !== null && <RequestDetailModal id={selected} basePath={basePath} onClose={() => setSelected(null)} />}
    </div>
  )
}

export default function RequestsPage() {
  return (
    <div>
      <PageHeader title="Requests" />
      <RequestsTable basePath="/api/admin/requests" showKeyFilter />
    </div>
  )
}
