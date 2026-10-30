import { useEffect, useState } from 'react'
import { api, formatMoney } from '../api'
import { useAuth } from '../authContext'
import CostChart from '../components/CostChart'
import ExportButton from '../components/ExportButton'
import { ErrorMessage, Select } from '../components/ui'
import { formatNumber } from '../dates'
import type { DailyRow, Me, Summary } from '../types'
import { RequestsTable } from './RequestsPage'

/** Page for team members: their own budget, spending and requests. */
export default function UserDashboard() {
  const { logout } = useAuth()
  const [me, setMe] = useState<Me | null>(null)
  const [days, setDays] = useState(30)
  const [summary, setSummary] = useState<Summary | null>(null)
  const [daily, setDaily] = useState<DailyRow[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api<Me>('/api/user/me')
      .then(setMe)
      .catch((e) => setError(e.message))
  }, [])

  useEffect(() => {
    Promise.all([api<Summary>(`/api/user/stats/summary?days=${days}`), api<DailyRow[]>(`/api/user/stats/daily?days=${days}`)])
      .then(([s, d]) => {
        setSummary(s)
        setDaily(d)
      })
      .catch((e) => setError(e.message))
  }, [days])

  const percent = me?.monthlyBudget ? (me.spentThisMonth / me.monthlyBudget) * 100 : 0
  const barColor = percent >= 100 ? 'bg-red-600' : percent >= 80 ? 'bg-amber-500' : 'bg-green-600'

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-4 py-3 md:px-8">
        <div className="text-xl font-bold">TokenFest</div>
        <div className="flex items-center gap-4 text-sm">
          {me && (
            <span className="text-gray-600">
              {me.name} <span className="font-mono text-xs text-gray-400">({me.keyPrefix}…)</span>
            </span>
          )}
          <button onClick={logout} className="text-indigo-600 hover:underline">
            Log out
          </button>
        </div>
      </header>
      <main className="mx-auto max-w-6xl space-y-6 p-4 md:p-8">
        <ErrorMessage message={error} />
        {me && (
          <section className="rounded-lg border border-gray-200 bg-white p-5">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <h1 className="text-lg font-semibold">This month ({me.month})</h1>
              <span className="text-sm text-gray-600">
                Rate limit: {me.rateLimitPerMinute ? `${me.rateLimitPerMinute} requests/minute` : 'none'}
              </span>
            </div>
            <div className="mt-3 text-3xl font-semibold">
              {formatMoney(me.spentThisMonth)}
              <span className="ml-2 text-base font-normal text-gray-500">
                {me.monthlyBudget !== null ? `of ${formatMoney(me.monthlyBudget)} budget` : 'no budget limit'}
              </span>
            </div>
            {me.monthlyBudget !== null && (
              <div className="mt-3 h-2 rounded bg-gray-200" aria-label={`${percent.toFixed(0)}% of budget used`}>
                <div className={`h-2 rounded ${barColor}`} style={{ width: `${Math.min(100, percent)}%` }} />
              </div>
            )}
            {percent >= 100 && <p className="mt-2 text-sm text-red-700">Budget used up: new requests are blocked until next month.</p>}
          </section>
        )}

        <section className="rounded-lg border border-gray-200 bg-white p-5">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
            <h2 className="font-medium">
              Cost per day by model
              {summary && (
                <span className="ml-2 text-sm font-normal text-gray-500">
                  {formatMoney(summary.totalCost)} · {formatNumber(summary.requests)} requests
                </span>
              )}
            </h2>
            <div className="flex flex-wrap gap-2">
              <Select value={days} onChange={(e) => setDays(Number(e.target.value))} className="w-36" aria-label="Period">
                <option value={7}>Last 7 days</option>
                <option value={30}>Last 30 days</option>
                <option value={90}>Last 90 days</option>
              </Select>
              <ExportButton path="/api/user/export" />
            </div>
          </div>
          {summary && <CostChart rows={daily} from={summary.from} to={summary.to} />}
        </section>

        <section>
          <h2 className="mb-3 font-medium">My requests</h2>
          <RequestsTable basePath="/api/user/requests" showKeyFilter={false} />
        </section>
      </main>
    </div>
  )
}
