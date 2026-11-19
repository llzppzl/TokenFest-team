import { expect, test } from 'vitest'
import { formatMoney } from '../api'
import { axisMoney, topGroups } from './chartData'

const row = (group: string, cost: number) => ({ date: '2026-10-01', group, cost, requests: 1, inputTokens: 1, outputTokens: 1 })

test('groups are ordered by cost', () => {
  expect(topGroups([row('a', 1), row('b', 5), row('a', 1)]).groups).toEqual(['b', 'a'])
})

test('more than seven groups fold into Other', () => {
  const rows = Array.from({ length: 9 }, (_, i) => row(`g${i}`, 100 - i))
  const result = topGroups(rows)
  expect(result.hasOther).toBe(true)
  expect(result.groups).toHaveLength(6)
  expect(result.groups[0]).toBe('g0')
})

test('axis labels stay distinct for small amounts', () => {
  expect([0, 0.0025, 0.005, 0.0075, 0.01].map(axisMoney)).toEqual(['$0', '$0.0025', '$0.005', '$0.0075', '$0.01'])
  expect(axisMoney(2.5)).toBe('$2.50')
  expect(axisMoney(120)).toBe('$120')
})

test('money keeps small amounts readable', () => {
  expect(formatMoney(0)).toBe('$0.00')
  expect(formatMoney(12.345)).toBe('$12.35')
  expect(formatMoney(0.000027)).toBe('$0.000027')
  expect(formatMoney(0.0007)).toBe('$0.0007')
  expect(formatMoney(null)).toBe('—')
})
