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

export type Leg = {
  durationSeconds: number
  distanceMeters: number
}

export const fetchRoutingConfig = (signal?: AbortSignal) =>
  getJson<RoutingConfig>('/api/routing/config', signal)
