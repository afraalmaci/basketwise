import { useState } from 'react'
import { fetchRoutingConfig, type LatLng, type RoutingConfig, type TravelMode } from '../api/routing'
import { clampToServiceArea, resolveOrigin, type Origin } from '../location'
import { useGeolocation, type GeolocationState } from './useGeolocation'
import { useRequest, type RequestState } from './useRequest'

export type TripPlanner = {
  config: RequestState<RoutingConfig>
  geolocation: GeolocationState
  /** Null until the routing config (which holds the default location) has loaded. */
  origin: Origin | null
  moveOrigin: (position: LatLng) => void
  resetOrigin: () => void
  mode: TravelMode
  setMode: (mode: TravelMode) => void
}

/** ~1 m precision; keeps request keys stable against floating point noise. */
export const coordKey = (point: LatLng) => `${point.latitude.toFixed(5)},${point.longitude.toFixed(5)}`

/** Where the user starts and how they travel: the inputs every plan needs. */
export function useTripPlanner(): TripPlanner {
  const config = useRequest<RoutingConfig>('config', fetchRoutingConfig)
  const geolocation = useGeolocation()
  const [manualOrigin, setManualOrigin] = useState<LatLng | null>(null)
  const [mode, setMode] = useState<TravelMode>('bike')

  const area = config.state === 'done' ? config.data.serviceArea : null
  const origin = area ? resolveOrigin(geolocation, manualOrigin, area) : null

  return {
    config,
    geolocation,
    origin,
    moveOrigin: (position) => setManualOrigin(area ? clampToServiceArea(position, area) : position),
    resetOrigin: () => setManualOrigin(null),
    mode,
    setMode,
  }
}
