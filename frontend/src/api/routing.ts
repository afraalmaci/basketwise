import { getJson } from './http'

export type LatLng = {
  latitude: number
  longitude: number
}

export type TravelMode = 'bike' | 'walk'

export type ServiceArea = {
  name: string
  minLatitude: number
  minLongitude: number
  maxLatitude: number
  maxLongitude: number
  defaultLocation: LatLng
}

export type RoutingConfig = {
  /** "openrouteservice" or "straight-line" */
  provider: string
  serviceArea: ServiceArea
  modes: TravelMode[]
  maxStops: number
}

export type NearestStore = {
  storeId: number
  chainCode: string
  storeName: string
  latitude: number
  longitude: number
  durationSeconds: number
  distanceMeters: number
}

export type NearestStores = {
  mode: TravelMode
  /** True when times are straight-line estimates rather than real routes. */
  estimated: boolean
  stores: NearestStore[]
}

export type Leg = {
  durationSeconds: number
  distanceMeters: number
}

export const fetchRoutingConfig = (signal?: AbortSignal) =>
  getJson<RoutingConfig>('/api/routing/config', signal)

export const fetchNearestStores = (origin: LatLng, mode: TravelMode, signal?: AbortSignal) =>
  getJson<NearestStores>(
    `/api/routing/nearest?lat=${origin.latitude}&lon=${origin.longitude}&mode=${mode}`,
    signal,
  )
