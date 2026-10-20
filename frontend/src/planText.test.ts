import { describe, expect, it } from 'vitest'
import type { Plan } from './api/planning'
import { difference, verdict } from './planText'

const normalize = (text: string) => text.replace(/ /g, ' ')

const plan = (storeCount: number, basketCents: number, travelSeconds: number): Plan => ({
  storeCount,
  basketCents,
  travelSeconds,
  travelDistanceMeters: 0,
  timeCostCents: 0,
  scoreCents: 0,
  stops: [],
  legs: [],
  estimated: true,
  geometry: [],
})

describe('verdict', () => {
  it('explains why more stores won', () => {
    expect(normalize(verdict([plan(2, 400, 1140), plan(1, 500, 600)], 3))).toBe(
      'Going to 2 stores saves € 1,00 for 9 min more travel than 1 store. At €3/h that is worth it.',
    )
  })

  it('explains why more stores lost', () => {
    expect(normalize(verdict([plan(1, 500, 600), plan(2, 400, 1140)], 10))).toBe(
      '2 stores would save € 1,00 but add 9 min of travel. At €10/h that is not worth it.',
    )
  })

  it('says when more stores win on both price and time', () => {
    expect(verdict([plan(2, 400, 500), plan(1, 500, 600)], 10)).toBe('2 stores beat 1 store on both price and time.')
  })

  it('says when more stores would not save anything', () => {
    expect(verdict([plan(1, 400, 600), plan(2, 400, 900)], 10)).toBe('Going to more stores would not save anything.')
  })

  it('handles a single plan', () => {
    expect(verdict([plan(1, 400, 600)], 10)).toBe('One store has everything at the best price.')
  })
})

describe('difference', () => {
  it('shows price and time relative to the best plan', () => {
    expect(normalize(difference(plan(1, 500, 600), plan(2, 400, 1140)))).toBe('+€ 1,00 · −9 min')
    expect(normalize(difference(plan(3, 380, 1800), plan(2, 400, 1140)))).toBe('−€ 0,20 · +11 min')
    expect(difference(plan(1, 400, 610), plan(2, 400, 600))).toBe('same price · same time')
  })
})
