import { getJson, postJson } from './http'

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

export type TripStop = {
  storeId: number
  chainCode: string
  storeName: string
  latitude: number
  longitude: number
}

export type Leg = {
  durationSeconds: number
  distanceMeters: number
}

/** One way to do the trip. */
export type TripOption = {
  /** Stores in visiting order. */
  stops: TripStop[]
  /** Origin → first stop, ..., last stop → origin. */
  legs: Leg[]
  totalDurationSeconds: number
  totalDistanceMeters: number
  /** How much slower than the fastest option; 0 for the fastest. */
  extraSeconds: number
  /** True when this route is a straight-line estimate. */
  estimated: boolean
  /** [latitude, longitude] pairs. */
  geometry: [number, number][]
}

export type TripPlan = {
  mode: TravelMode
  estimated: boolean
  /** Fastest first, then up to two alternatives (other orders or other branches). */
  options: TripOption[]
}

export const fetchRoutingConfig = (signal?: AbortSignal) =>
  getJson<RoutingConfig>('/api/routing/config', signal)

export const fetchNearestStores = (origin: LatLng, mode: TravelMode, signal?: AbortSignal) =>
  getJson<NearestStores>(
    `/api/routing/nearest?lat=${origin.latitude}&lon=${origin.longitude}&mode=${mode}`,
    signal,
  )

export const planTrip = (origin: LatLng, storeIds: number[], mode: TravelMode, signal?: AbortSignal) =>
  postJson<TripPlan>('/api/routing/trip', { origin, storeIds, mode }, signal)
