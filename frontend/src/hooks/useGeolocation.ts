import { useEffect, useState } from 'react'
import type { LatLng } from '../api/routing'

export type GeolocationState =
  | { status: 'pending' }
  | { status: 'granted'; position: LatLng; accuracyMeters: number }
  | { status: 'denied' }
  | { status: 'unavailable'; message: string }

const OPTIONS: PositionOptions = {
  // Coarse is enough to pick nearby stores, and much faster on laptops without GPS.
  enableHighAccuracy: false,
  timeout: 10_000,
  maximumAge: 5 * 60_000,
}

function supported(): boolean {
  return typeof navigator !== 'undefined' && 'geolocation' in navigator
}

/**
 * Asks the browser for the user's location once, on mount.
 *
 * The browser shows its own permission prompt; nothing happens until the user answers,
 * so callers should treat "pending" as "use a default for now". Geolocation only works in
 * a secure context (HTTPS or localhost).
 *
 * The position stays in the browser: it is only sent to the API as part of a routing request.
 */
export function useGeolocation(): GeolocationState {
  // Decided during the first render, so no synchronous setState inside the effect.
  const [state, setState] = useState<GeolocationState>(() =>
    supported() ? { status: 'pending' } : { status: 'unavailable', message: 'Geolocation is not supported' },
  )

  useEffect(() => {
    if (!supported()) return
    let cancelled = false

    navigator.geolocation.getCurrentPosition(
      (position) => {
        if (cancelled) return
        setState({
          status: 'granted',
          position: { latitude: position.coords.latitude, longitude: position.coords.longitude },
          accuracyMeters: position.coords.accuracy,
        })
      },
      (error) => {
        if (cancelled) return
        setState(
          error.code === error.PERMISSION_DENIED
            ? { status: 'denied' }
            : { status: 'unavailable', message: error.message || 'Position unavailable' },
        )
      },
      OPTIONS,
    )

    return () => {
      cancelled = true
    }
  }, [])

  return state
}
