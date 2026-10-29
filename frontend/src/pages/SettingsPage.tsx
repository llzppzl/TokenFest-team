import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, formatDate, formatMoney } from '../api'
import { Badge, Button, ErrorMessage, Field, Input, PageHeader, Table } from '../components/ui'
import type { AlertEvent, AlertSettings, DeliveryResult } from '../types'

function AlertSettingsForm() {
  const [thresholds, setThresholds] = useState('')
  const [webhookUrl, setWebhookUrl] = useState('')
  const [emails, setEmails] = useState('')
  const [emailConfigured, setEmailConfigured] = useState(true)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const apply = (s: AlertSettings) => {
    setThresholds(s.thresholds.join(', '))
    setWebhookUrl(s.webhookUrl)
    setEmails(s.emails.join(', '))
    setEmailConfigured(s.emailConfigured)
  }

  useEffect(() => {
    api<AlertSettings>('/api/admin/settings')
      .then(apply)
      .catch((e) => setError(e.message))
  }, [])

  async function save(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setMessage(null)
    const parsed = thresholds.split(',').map((t) => t.trim()).filter(Boolean).map(Number)
    if (parsed.some((t) => !Number.isInteger(t))) {
      setError('Thresholds must be whole numbers, e.g. 50, 80, 100')
      return
    }
    setBusy(true)
    try {
      const saved = await api<AlertSettings>('/api/admin/settings', {
        method: 'PUT',
        body: {
          thresholds: parsed,
          webhookUrl,
          emails: emails.split(',').map((x) => x.trim()).filter(Boolean),
        },
      })
      apply(saved)
      setMessage('Saved.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Save failed')
    } finally {
      setBusy(false)
    }
  }

  async function sendTest() {
    setError(null)
    setMessage('Sending...')
    try {
      const r = await api<DeliveryResult>('/api/admin/settings/test-alert', { method: 'POST' })
      setMessage(`Webhook: ${r.webhook}. Email: ${r.email}.`)
    } catch (err) {
      setMessage(null)
      setError(err instanceof Error ? err.message : 'Test failed')
    }
  }

  return (
    <form onSubmit={save} className="space-y-4 rounded-lg border border-gray-200 bg-white p-5">
      <h2 className="font-medium">Budget alerts</h2>
      <p className="text-sm text-gray-600">
        An alert is sent once per key and month when its spending reaches each threshold. Save before sending a test.
      </p>
      <Field label="Thresholds (% of monthly budget)" hint="Comma-separated, e.g. 50, 80, 100">
        <Input value={thresholds} onChange={(e) => setThresholds(e.target.value)} />
      </Field>
      <Field label="Webhook URL" hint="Receives a JSON POST with a 'text' field (works with Slack incoming webhooks)">
        <Input value={webhookUrl} onChange={(e) => setWebhookUrl(e.target.value)} placeholder="https://hooks.slack.com/services/..." />
      </Field>
      <Field label="Alert emails" hint="Comma-separated">
        <Input value={emails} onChange={(e) => setEmails(e.target.value)} placeholder="lead@example.com" />
      </Field>
      {!emailConfigured && (
        <p className="rounded bg-amber-50 px-3 py-2 text-sm text-amber-900">
          No SMTP server is configured, so emails can't be sent. Set SPRING_MAIL_HOST and related settings (see README).
        </p>
      )}
      <ErrorMessage message={error} />
      {message && <p className="text-sm text-gray-700">{message}</p>}
      <div className="flex gap-2">
        <Button type="submit" disabled={busy}>
          Save
        </Button>
        <Button type="button" variant="secondary" onClick={sendTest}>
          Send test alert
        </Button>
      </div>
    </form>
  )
}

function AlertHistory() {
  const [events, setEvents] = useState<AlertEvent[]>([])
  const [error, setError] = useState<string | null>(null)
  const load = useCallback(() => {
    api<AlertEvent[]>('/api/admin/alerts')
      .then(setEvents)
      .catch((e) => setError(e.message))
  }, [])
  useEffect(load, [load])
  return (
    <section>
      <div className="mb-2 flex items-center justify-between">
        <h2 className="font-medium">Recent alerts</h2>
        <Button variant="secondary" onClick={load}>
          Refresh
        </Button>
      </div>
      <ErrorMessage message={error} />
      <Table headers={['Time', 'Key', 'Month', 'Threshold', 'Spent / budget', 'Delivery']}>
        {events.length === 0 && (
          <tr>
            <td colSpan={6} className="px-4 py-6 text-center text-gray-500">
              No alerts yet.
            </td>
          </tr>
        )}
        {events.map((e) => (
          <tr key={e.id}>
            <td className="whitespace-nowrap px-4 py-2 text-gray-600">{formatDate(e.createdAt)}</td>
            <td className="px-4 py-2 font-medium">{e.keyName}</td>
            <td className="px-4 py-2">{e.month}</td>
            <td className="px-4 py-2">{e.thresholdPercent}%</td>
            <td className="whitespace-nowrap px-4 py-2">
              {formatMoney(e.spent)} / {formatMoney(e.budget)}
            </td>
            <td className="px-4 py-2">
              {e.delivered ? (
                <Badge color="green">Delivered</Badge>
              ) : (
                <span title={e.errorMessage ?? ''}>
                  <Badge color={e.errorMessage ? 'red' : 'gray'}>{e.errorMessage ? 'Failed' : 'Not sent'}</Badge>
                </span>
              )}
            </td>
          </tr>
        ))}
      </Table>
    </section>
  )
}

function PasswordForm() {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setMessage(null)
    try {
      await api('/api/admin/account/password', {
        method: 'PUT',
        body: { currentPassword: current, newPassword: next },
      })
      setCurrent('')
      setNext('')
      setMessage('Password changed.')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Change failed')
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4 rounded-lg border border-gray-200 bg-white p-5">
      <h2 className="font-medium">Change password</h2>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Current password">
          <Input type="password" value={current} onChange={(e) => setCurrent(e.target.value)} autoComplete="current-password" required />
        </Field>
        <Field label="New password" hint="At least 8 characters">
          <Input type="password" value={next} onChange={(e) => setNext(e.target.value)} autoComplete="new-password" minLength={8} required />
        </Field>
      </div>
      <ErrorMessage message={error} />
      {message && <p className="text-sm text-gray-700">{message}</p>}
      <Button type="submit">Change password</Button>
    </form>
  )
}

export default function SettingsPage() {
  return (
    <div className="space-y-6">
      <PageHeader title="Settings" />
      <AlertSettingsForm />
      <AlertHistory />
      <PasswordForm />
    </div>
  )
}
