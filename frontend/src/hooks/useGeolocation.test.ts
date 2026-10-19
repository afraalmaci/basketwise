import { renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { mockGeolocation } from '../test/fixtures'
import { useGeolocation } from './useGeolocation'

describe('useGeolocation', () => {
  let restore: (() => void) | null = null

  afterEach(() => {
    restore?.()
    restore = null
  })

  it('reports the position once the user allows it', async () => {
    restore = mockGeolocation({ kind: 'granted', latitude: 52.07, longitude: 4.3 })

    const { result } = renderHook(() => useGeolocation())

    await waitFor(() =>
      expect(result.current).toEqual({
        status: 'granted',
        position: { latitude: 52.07, longitude: 4.3 },
        accuracyMeters: 30,
      }),
    )
  })

  it('reports a denied permission', async () => {
    restore = mockGeolocation({ kind: 'denied' })

    const { result } = renderHook(() => useGeolocation())

    await waitFor(() => expect(result.current).toEqual({ status: 'denied' }))
  })

  it('stays pending while the prompt is unanswered', () => {
    restore = mockGeolocation({ kind: 'never' })

    const { result } = renderHook(() => useGeolocation())

    expect(result.current).toEqual({ status: 'pending' })
  })

  it('is unavailable when the browser has no geolocation', () => {
    // jsdom does not implement navigator.geolocation.
    const { result } = renderHook(() => useGeolocation())

    expect(result.current.status).toBe('unavailable')
  })
})
