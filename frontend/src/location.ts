import type { LatLng, ServiceArea } from './api/routing'
import type { GeolocationState } from './hooks/useGeolocation'

export type OriginSource = 'manual' | 'device' | 'default'

export type Origin = {
  position: LatLng
  source: OriginSource
  /** Shown above the map when the app is not using the user's own location. */
  notice: string | null
  /** How precise the device location is; drawn as a halo around the blue dot. */
  accuracyMeters?: number
}

export function isInServiceArea(point: LatLng, area: ServiceArea): boolean {
  return (
    point.latitude >= area.minLatitude &&
    point.latitude <= area.maxLatitude &&
    point.longitude >= area.minLongitude &&
    point.longitude <= area.maxLongitude
  )
}

/**
 * Which location to route from, in order of preference:
 * 1. a pin the user dragged (always inside the area: useTripPlanner clamps it),
 * 2. the device location, if it is inside the demo area,
 * 3. the area's default location, with a notice explaining why.
 */
export function resolveOrigin(geo: GeolocationState, manual: LatLng | null, area: ServiceArea): Origin {
  if (manual) {
    return { position: manual, source: 'manual', notice: null }
  }
  if (geo.status === 'granted' && isInServiceArea(geo.position, area)) {
    return { position: geo.position, source: 'device', notice: null, accuracyMeters: geo.accuracyMeters }
  }

  const fallback = (reason: string): Origin => ({
    position: area.defaultLocation,
    source: 'default',
    notice: `${reason} Using a sample location in ${area.name}; drag the pin to move it.`,
  })

  switch (geo.status) {
    case 'pending':
      return fallback('Waiting for location permission.')
    case 'denied':
      return fallback('Location access is off.')
    case 'unavailable':
      return fallback('Your location is unavailable.')
    case 'granted':
      return fallback(`Demo data only covers ${area.name}.`)
  }
}

const round5 = (value: number) => Math.round(value * 1e5) / 1e5

/**
 * Keeps a dragged pin inside the service area, rounded to 5 decimals (about 1 m): Leaflet
 * reports 15 digits, which only makes URLs long and cache keys unique for no benefit.
 */
export function clampToServiceArea(point: LatLng, area: ServiceArea): LatLng {
  return {
    latitude: round5(Math.min(Math.max(point.latitude, area.minLatitude), area.maxLatitude)),
    longitude: round5(Math.min(Math.max(point.longitude, area.minLongitude), area.maxLongitude)),
  }
}
