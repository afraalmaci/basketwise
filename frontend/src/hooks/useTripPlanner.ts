import { useState } from 'react'
import {
  fetchNearestStores,
  fetchRoutingConfig,
  planTrip,
  type LatLng,
  type NearestStores,
  type RoutingConfig,
  type TravelMode,
  type TripPlan,
} from '../api/routing'
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
  nearest: RequestState<NearestStores>
  selectedStoreIds: number[]
  toggleStore: (storeId: number) => void
  maxStops: number
  trip: RequestState<TripPlan>
  /** Index of the option shown in blue; the others are drawn gray. 0 is the fastest. */
  activeOption: number
  selectOption: (index: number) => void
}


/** ~1 m precision; keeps request keys stable against floating point noise. */
const coordKey = (point: LatLng) => `${point.latitude.toFixed(5)},${point.longitude.toFixed(5)}`

/**
 * State and requests behind the trip panel and the map: where the user is, how they
 * travel, which stores they picked, and the resulting nearest stores and trip.
 */
export function useTripPlanner(): TripPlanner {
  const config = useRequest<RoutingConfig>('config', fetchRoutingConfig)
  const geolocation = useGeolocation()
  const [manualOrigin, setManualOrigin] = useState<LatLng | null>(null)
  const [mode, setMode] = useState<TravelMode>('bike')
  const [selectedStoreIds, setSelectedStoreIds] = useState<number[]>([])

  const routingConfig = config.state === 'done' ? config.data : null
  const area = routingConfig?.serviceArea ?? null
  const maxStops = routingConfig?.maxStops ?? 3
  const origin = area ? resolveOrigin(geolocation, manualOrigin, area) : null

  const nearest = useRequest<NearestStores>(
    origin ? `nearest:${coordKey(origin.position)}:${mode}` : null,
    (signal) => fetchNearestStores(origin!.position, mode, signal),
  )

  // The server picks the visiting order, so the selection is sent sorted: picking A then B
  // and B then A is the same question and shares one request.
  const storeIds = [...selectedStoreIds].sort((a, b) => a - b)
  const tripKey = origin && storeIds.length > 0 ? `trip:${coordKey(origin.position)}:${mode}:${storeIds.join(',')}` : null
  const trip = useRequest<TripPlan>(tripKey, (signal) => planTrip(origin!.position, storeIds, mode, signal))

  // The chosen option belongs to one trip request. Storing the key with it means a new
  // request starts again at the fastest option, without an effect that resets state.
  const [chosen, setChosen] = useState<{ key: string | null; index: number }>({ key: null, index: 0 })
  const activeOption = chosen.key === tripKey ? chosen.index : 0

  return {
    config,
    geolocation,
    origin,
    moveOrigin: (position) => setManualOrigin(area ? clampToServiceArea(position, area) : position),
    resetOrigin: () => setManualOrigin(null),
    mode,
    setMode,
    nearest,
    selectedStoreIds,
    toggleStore: (storeId) =>
      setSelectedStoreIds((current) => {
        if (current.includes(storeId)) return current.filter((id) => id !== storeId)
        if (current.length >= maxStops) return current
        return [...current, storeId]
      }),
    maxStops,
    trip,
    activeOption,
    selectOption: (index) => setChosen({ key: tripKey, index }),
  }
}
