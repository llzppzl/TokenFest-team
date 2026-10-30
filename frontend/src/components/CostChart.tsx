import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { formatMoney } from '../api'
import { eachDay } from '../dates'
import type { DailyRow } from '../types'
import { axisMoney, OTHER_COLOR, SERIES_COLORS, topGroups } from './chartData'


export default function CostChart({ rows, from, to }: { rows: DailyRow[]; from: string; to: string }) {
  const { groups, hasOther } = topGroups(rows)
  const series = hasOther ? [...groups, 'Other'] : groups
  const data = eachDay(from, to).map((date) => {
    const point: Record<string, number | string> = { date: date.slice(5) }
    series.forEach((s) => (point[s] = 0))
    rows
      .filter((r) => r.date === date)
      .forEach((r) => {
        const key = groups.includes(r.group) ? r.group : 'Other'
        point[key] = (point[key] as number) + r.cost
      })
    return point
  })

  if (rows.length === 0) {
    return <div className="flex h-64 items-center justify-center text-sm text-gray-500">No usage in this period.</div>
  }

  return (
    <ResponsiveContainer width="100%" height={280}>
      <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barCategoryGap="20%">
        <CartesianGrid vertical={false} stroke="#e5e7eb" />
        <XAxis dataKey="date" tickLine={false} axisLine={{ stroke: '#d1d5db' }} tick={{ fontSize: 12, fill: '#6b7280' }} />
        <YAxis
          tickLine={false}
          axisLine={false}
          width={56}
          tick={{ fontSize: 12, fill: '#6b7280' }}
          tickFormatter={axisMoney}
        />
        <Tooltip
          cursor={{ fill: 'rgba(0,0,0,0.04)' }}
          formatter={(value) => formatMoney(Number(value))}
          contentStyle={{ fontSize: 12, borderRadius: 6 }}
        />
        <Legend wrapperStyle={{ fontSize: 12, color: '#374151' }} iconType="square" />
        {series.map((s, i) => (
          <Bar
            key={s}
            dataKey={s}
            stackId="cost"
            fill={s === 'Other' ? OTHER_COLOR : SERIES_COLORS[i]}
            stroke="#ffffff"
            strokeWidth={1}
            radius={i === series.length - 1 ? [4, 4, 0, 0] : 0}
            maxBarSize={36}
          />
        ))}
      </BarChart>
    </ResponsiveContainer>
  )
}
