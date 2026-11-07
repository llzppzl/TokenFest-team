import { useEffect, useMemo, useState } from 'react'
import { api, budgetPercent, formatMoney } from '../api'
import CostChart from '../components/CostChart'
import ExportButton from '../components/ExportButton'
import { ErrorMessage, PageHeader, Select } from '../components/ui'
import { formatNumber } from '../dates'
import type { ApiKey, DailyRow, Summary } from '../types'

type GroupBy = 'key' | 'model' | 'provider'

function StatTile({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <div className="rounded-lg border border-gray-200 bg-white p-4">
      <div className="text-sm text-gray-500">{label}</div>
      <div className="mt-1 text-2xl font-semibold text-gray-900">{value}</div>
      {note && <div className="mt-1 text-xs text-gray-500">{note}</div>}
    </div>
  )
}

export default function OverviewPage() {
  const [days, setDays] = useState(7)
  const [groupBy, setGroupBy] = useState<GroupBy>('key')
  const [summary, setSummary] = useState<Summary | null>(null)
  const [daily, setDaily] = useState<DailyRow[]>([])
  const [keys, setKeys] = useState<ApiKey[]>([])
  const [error, setError] = useState<string | null>(null)
  // Grouping of the rows currently shown (they may still be the old ones while loading)
  const [shownGroupBy, setShownGroupBy] = useState<GroupBy>(groupBy)
  useEffect(() => {
    let ignore = false // a slower, older response must not replace a newer one
    // The server picks the dates in its own time zone
    const q = `days=${days}`
    Promise.all([
      api<Summary>(`/api/admin/stats/summary?${q}`),
      api<DailyRow[]>(`/api/admin/stats/daily?${q}&groupBy=${groupBy}`),
      api<ApiKey[]>('/api/admin/keys'),
    ])
      .then(([s, d, k]) => {
        if (ignore) return
        setSummary(s)
        setDaily(d)
        setKeys(k)
        setShownGroupBy(groupBy)
        setError(null)
      })
      .catch((e) => {
        if (!ignore) setError(e.message)
      })
    return () => {
      ignore = true
    }
  }, [days, groupBy])

  const totals = useMemo(() => {
    const byGroup = new Map<string, { cost: number; requests: number; tokens: number }>()
    daily.forEach((r) => {
      const t = byGroup.get(r.group) ?? { cost: 0, requests: 0, tokens: 0 }
      t.cost += r.cost
      t.requests += r.requests
      t.tokens += r.inputTokens + r.outputTokens
      byGroup.set(r.group, t)
    })
    return [...byGroup.entries()].sort((a, b) => b[1].cost - a[1].cost)
  }, [daily])

  const budgetKeys = keys
    .filter((k) => k.monthlyBudget !== null)
    .sort((a, b) => budgetPercent(b.spentThisMonth, b.monthlyBudget!) - budgetPercent(a.spentThisMonth, a.monthlyBudget!))
  const errorRate = summary && summary.requests > 0 ? ((summary.errors / summary.requests) * 100).toFixed(1) : '0'
  // Show a dash, not a misleading zero, until the first load finishes
  const show = (value: (s: Summary) => string) => (summary ? value(summary) : '—')

  return (
    <div className="space-y-6">
      <PageHeader
        title="Overview"
        action={
          <div className="flex flex-wrap justify-end gap-2">
            <Select value={days} onChange={(e) => setDays(Number(e.target.value))} className="w-40" aria-label="Period">
              <option value={7}>Last 7 days</option>
              <option value={30}>Last 30 days</option>
              <option value={90}>Last 90 days</option>
            </Select>
            <ExportButton path="/api/admin/export" />
          </div>
        }
      />
      <ErrorMessage message={error} />

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatTile label="Cost" value={show((s) => formatMoney(s.totalCost))} note={summary ? `${summary.activeKeys} active keys` : undefined} />
        <StatTile label="Requests" value={show((s) => formatNumber(s.requests))} />
        <StatTile
          label="Tokens"
          value={show((s) => formatNumber(s.inputTokens + s.outputTokens))}
          note={summary ? `${formatNumber(summary.inputTokens)} in · ${formatNumber(summary.outputTokens)} out` : undefined}
        />
        <StatTile label="Errors" value={show((s) => formatNumber(s.errors))} note={summary ? `${errorRate}% of requests` : undefined} />
      </div>

      <section className="rounded-lg border border-gray-200 bg-white p-4">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h2 className="font-medium text-gray-900">Cost per day</h2>
          <div className="flex rounded-md border border-gray-300 text-sm" role="group" aria-label="Group by">
            {(['key', 'model', 'provider'] as GroupBy[]).map((g) => (
              <button
                key={g}
                aria-pressed={groupBy === g}
                onClick={() => setGroupBy(g)}
                className={`px-3 py-1 capitalize first:rounded-l-md last:rounded-r-md ${
                  groupBy === g ? 'bg-indigo-600 text-white' : 'text-gray-600 hover:bg-gray-50'
                }`}
              >
                By {g}
              </button>
            ))}
          </div>
        </div>
        {summary && <CostChart rows={daily} from={summary.from} to={summary.to} />}
        {totals.length > 0 && (
          <table className="mt-4 w-full text-sm">
            <thead>
              <tr className="border-b text-left text-gray-500">
                <th className="py-1 font-medium capitalize">{shownGroupBy}</th>
                <th className="py-1 text-right font-medium">Cost</th>
                <th className="py-1 text-right font-medium">Requests</th>
                <th className="py-1 text-right font-medium">Tokens</th>
              </tr>
            </thead>
            <tbody>
              {totals.map(([group, t]) => (
                <tr key={group} className="border-b border-gray-100">
                  <td className="py-1">{group}</td>
                  <td className="py-1 text-right">{formatMoney(t.cost)}</td>
                  <td className="py-1 text-right">{formatNumber(t.requests)}</td>
                  <td className="py-1 text-right">{formatNumber(t.tokens)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="rounded-lg border border-gray-200 bg-white p-4">
        <h2 className="mb-3 font-medium text-gray-900">Budgets this month</h2>
        {!summary && !error && <p className="text-sm text-gray-500">Loading...</p>}
        {summary && budgetKeys.length === 0 && <p className="text-sm text-gray-500">No key has a monthly budget.</p>}
        <div className="space-y-3">
          {budgetKeys.map((k) => {
            const percent = budgetPercent(k.spentThisMonth, k.monthlyBudget!)
            const color = percent >= 100 ? 'bg-red-600' : percent >= 80 ? 'bg-amber-500' : 'bg-green-600'
            return (
              <div key={k.id}>
                <div className="flex justify-between text-sm">
                  <span className="font-medium">{k.name}</span>
                  <span className="text-gray-600">
                    {formatMoney(k.spentThisMonth)} of {formatMoney(k.monthlyBudget)} ({percent.toFixed(0)}%)
                  </span>
                </div>
                <div className="mt-1 h-2 rounded bg-gray-200">
                  <div className={`h-2 rounded ${color}`} style={{ width: `${Math.min(100, percent)}%` }} />
                </div>
              </div>
            )
          })}
        </div>
      </section>
    </div>
  )
}
