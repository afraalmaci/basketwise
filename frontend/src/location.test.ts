import { describe, expect, it } from 'vitest'
import { clampToServiceArea, isInServiceArea, resolveOrigin } from './location'
import { routingConfig } from './test/fixtures'

const area = routingConfig.serviceArea
const inside = { latitude: 52.07, longitude: 4.3 }
const amsterdam = { latitude: 52.3676, longitude: 4.9041 }

describe('location', () => {
  it('checks the service area bounds inclusively', () => {
    expect(isInServiceArea(inside, area)).toBe(true)
    expect(isInServiceArea(amsterdam, area)).toBe(false)
    expect(isInServiceArea({ latitude: area.maxLatitude, longitude: area.minLongitude }, area)).toBe(true)
  })

  it('prefers a dragged pin over everything else', () => {
    const manual = { latitude: 52.1, longitude: 4.35 }
    const origin = resolveOrigin({ status: 'granted', position: inside, accuracyMeters: 20 }, manual, area)

    expect(origin).toEqual({ position: manual, source: 'manual', notice: null })
  })

  it('uses the device location inside the area without a notice', () => {
    const origin = resolveOrigin({ status: 'granted', position: inside, accuracyMeters: 20 }, null, area)

    expect(origin).toEqual({ position: inside, source: 'device', notice: null, accuracyMeters: 20 })
  })

  it('explains why it falls back to the default location', () => {
    const outside = resolveOrigin({ status: 'granted', position: amsterdam, accuracyMeters: 20 }, null, area)
    const denied = resolveOrigin({ status: 'denied' }, null, area)
    const pending = resolveOrigin({ status: 'pending' }, null, area)

    for (const origin of [outside, denied, pending]) {
      expect(origin.source).toBe('default')
      expect(origin.position).toEqual(area.defaultLocation)
    }
    expect(outside.notice).toMatch(/^Demo data only covers Den Haag\./)
    expect(denied.notice).toMatch(/^Location access is off\./)
    expect(pending.notice).toMatch(/^Waiting for location permission\./)
  })

  it('keeps a dragged pin inside the area', () => {
    expect(clampToServiceArea(amsterdam, area)).toEqual({ latitude: area.maxLatitude, longitude: area.maxLongitude })
    expect(clampToServiceArea(inside, area)).toEqual(inside)
  })

  it('rounds a dragged pin to about one meter', () => {
    expect(clampToServiceArea({ latitude: 52.07454734649577, longitude: 4.3116188049316415 }, area)).toEqual({
      latitude: 52.07455,
      longitude: 4.31162,
    })
  })
})
