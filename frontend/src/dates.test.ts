import { describe, it, expect } from 'vitest'
import { isoDate, eachDay } from './dates'

describe('Date utilities', () => {
  it('formats a date correctly', () => {
    const date = new Date(2026, 9, 29)
    expect(isoDate(date)).toBe('2026-10-29')
  })

  it('includes both start and end dates', () => {
    expect(eachDay('2026-10-01', '2026-10-03')).toEqual([
      '2026-10-01',
      '2026-10-02',
      '2026-10-03',
    ])
  })

  it('returns a single date when start and end are the same', () => {
    expect(eachDay('2026-10-01', '2026-10-01')).toEqual([
      '2026-10-01',
    ])
  })
})
