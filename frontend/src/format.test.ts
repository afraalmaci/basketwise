import { describe, expect, it } from 'vitest'
import { formatDistance, formatDuration, formatPrice, formatUnitPrice } from './format'

// Intl uses a non-breaking space between the euro sign and the amount.
const normalize = (text: string) => text.replace(/ /g, ' ')

describe('format', () => {
  it('formats cents as Dutch euros', () => {
    expect(normalize(formatPrice(129))).toBe('€ 1,29')
    expect(normalize(formatPrice(1279))).toBe('€ 12,79')
  })

  it('labels unit prices per liter, kilogram or piece', () => {
    expect(normalize(formatUnitPrice(115, 'ml'))).toBe('€ 1,15 / L')
    expect(normalize(formatUnitPrice(1290, 'g'))).toBe('€ 12,90 / kg')
    expect(normalize(formatUnitPrice(33, 'pc'))).toBe('€ 0,33 / st')
  })

  it('formats travel durations in minutes and hours', () => {
    expect(formatDuration(20)).toBe('1 min')
    expect(formatDuration(412)).toBe('7 min')
    expect(formatDuration(3540)).toBe('59 min')
    expect(formatDuration(3900)).toBe('1 h 05 min')
  })

  it('formats distances in meters below 1 km and kilometers above', () => {
    expect(formatDistance(847)).toBe('850 m')
    expect(formatDistance(1000)).toBe('1,0 km')
    expect(formatDistance(2430)).toBe('2,4 km')
  })
})
