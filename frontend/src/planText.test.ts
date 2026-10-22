import { describe, expect, it } from 'vitest'
import type { Plan, PlanRole } from './api/planning'
import { difference, roleLabel, verdict } from './planText'

const normalize = (text: string) => text.replace(/ /g, ' ')

const plan = (storeCount: number, basketCents: number, travelSeconds: number, roles: PlanRole[] = []): Plan => ({
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
  roles,
})

describe('verdict', () => {
  it('says when the best plan wins on price and time', () => {
    expect(verdict([plan(1, 400, 600, ['best', 'cheapest', 'fastest'])], 10)).toBe(
      'This plan is both the cheapest and the fastest way to buy your list.',
    )
  })

  it('explains what the faster plan would cost', () => {
    expect(normalize(verdict([plan(2, 400, 1140, ['best', 'cheapest']), plan(1, 500, 600, ['fastest'])], 3))).toBe(
      'The fastest plan saves 9 min but costs € 1,00 more. At €3/h that is not worth it.',
    )
  })

  it('explains what the cheaper plan would cost in time', () => {
    expect(normalize(verdict([plan(1, 500, 600, ['best', 'fastest']), plan(2, 400, 1140, ['cheapest'])], 10))).toBe(
      'The cheapest plan saves € 1,00 but takes 9 min longer. At €10/h that is not worth it.',
    )
  })

  it('explains a plan between the two extremes', () => {
    const plans = [plan(1, 130, 600, ['best']), plan(1, 100, 2400, ['cheapest']), plan(1, 260, 300, ['fastest'])]
    expect(normalize(verdict(plans, 10))).toBe(
      'The cheapest plan would save € 0,30 for 30 min more travel; the fastest would save 5 min for € 1,30 more. At €10/h this plan is the balance.',
    )
  })
})

describe('roleLabel', () => {
  it('joins the roles a plan has', () => {
    expect(roleLabel(['best', 'cheapest'])).toBe('Best for you · Cheapest')
    expect(roleLabel(['fastest'])).toBe('Fastest')
  })
})

describe('difference', () => {
  it('shows price and time relative to the best plan', () => {
    expect(normalize(difference(plan(1, 500, 600), plan(2, 400, 1140)))).toBe('+€ 1,00 · −9 min')
    expect(normalize(difference(plan(3, 380, 1800), plan(2, 400, 1140)))).toBe('−€ 0,20 · +11 min')
    expect(difference(plan(1, 400, 610), plan(2, 400, 600))).toBe('same price · same time')
  })
})
