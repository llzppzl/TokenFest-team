import type { DailyRow } from '../types'

// Categorical slots in fixed order (validated palette); extra groups fold into "Other"
export const SERIES_COLORS = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#4a3aa7']
export const OTHER_COLOR = '#9a9993'
const MAX_SERIES = SERIES_COLORS.length

/** Groups ordered by total cost; anything past the 7th becomes "Other". */
export function topGroups(rows: DailyRow[]) {
  const totals = new Map<string, number>()
  rows.forEach((r) => totals.set(r.group, (totals.get(r.group) ?? 0) + r.cost))
  const sorted = [...totals.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0])).map(([g]) => g)
  if (sorted.length <= MAX_SERIES) return { groups: sorted, hasOther: false }
  return { groups: sorted.slice(0, MAX_SERIES - 1), hasOther: true }
}

/** Axis label for dollar amounts, readable for both $0.003 and $300. */
export function axisMoney(value: number) {
  if (value === 0) return '$0'
  if (value >= 10) return `$${value.toFixed(0)}`
  if (value >= 1) return `$${value.toFixed(2)}`
  return `$${Number(value.toPrecision(2))}`
}
