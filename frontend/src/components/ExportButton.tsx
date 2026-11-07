import { useState } from 'react'
import { downloadFile } from '../api'
import { Button, Input } from './ui'

function currentMonth() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

/** Month picker + button that downloads the usage CSV for that month. */
export default function ExportButton({ path }: { path: string }) {
  const [month, setMonth] = useState(currentMonth)
  const [error, setError] = useState<string | null>(null)
  return (
    <div className="flex items-center gap-2">
      <Input type="month" value={month} onChange={(e) => setMonth(e.target.value)} className="w-40" aria-label="Month" />
      <Button
        variant="secondary"
        onClick={() => {
          setError(null)
          downloadFile(`${path}?month=${month}`, `tokenfest-usage-${month}.csv`).catch((e) => setError(e.message))
        }}
      >
        Export CSV
      </Button>
      {error && <span className="text-sm text-red-700">{error}</span>}
    </div>
  )
}
