import { vi } from 'vitest'
import type { Chain, Product, Store } from '../api/catalog'
import type { NearestStores, RoutingConfig, TripPlan } from '../api/routing'

export const chains: Chain[] = [
  { code: 'BUDGETBAAS', name: 'Budgetbaas', color: '#d97706', storeCount: 1 },
  { code: 'HOEKWINKEL', name: 'Hoekwinkel', color: '#2563eb', storeCount: 1 },
]

export const stores: Store[] = [
  { id: 1, chainCode: 'BUDGETBAAS', chainName: 'Budgetbaas', name: 'Budgetbaas Moerwijk', latitude: 52.05, longitude: 4.29 },
  { id: 2, chainCode: 'HOEKWINKEL', chainName: 'Hoekwinkel', name: 'Hoekwinkel Centrum', latitude: 52.07, longitude: 4.31 },
]

export const milk: Product = {
  id: 1,
  slug: 'halfvolle-melk',
  name: 'Halfvolle melk',
  category: 'dairy',
  unit: 'ml',
  offers: [
    { chainCode: 'BUDGETBAAS', displayName: 'BB Halfvolle melk', quantityText: '1,5 L', quantityAmount: 1500, priceCents: 129, unitPriceCents: 86 },
    { chainCode: 'HOEKWINKEL', displayName: 'Halfvolle Melk', quantityText: '1000 ml', quantityAmount: 1000, priceCents: 119, unitPriceCents: 119 },
  ],
}

export const routingConfig: RoutingConfig = {
  provider: 'straight-line',
  serviceArea: {
    name: 'Den Haag',
    minLatitude: 52.0,
    minLongitude: 4.18,
    maxLatitude: 52.13,
    maxLongitude: 4.45,
    defaultLocation: { latitude: 52.0809, longitude: 4.3245 },
  },
  modes: ['bike', 'walk'],
  maxStops: 3,
}

export const nearestStores: NearestStores = {
  mode: 'bike',
  estimated: true,
  stores: [
    { storeId: 2, chainCode: 'HOEKWINKEL', storeName: 'Hoekwinkel Centrum', latitude: 52.07, longitude: 4.31, durationSeconds: 300, distanceMeters: 1240 },
    { storeId: 1, chainCode: 'BUDGETBAAS', storeName: 'Budgetbaas Moerwijk', latitude: 52.05, longitude: 4.29, durationSeconds: 900, distanceMeters: 3760 },
  ],
}

/** The fastest option, then an alternative that uses another Hoekwinkel branch. */
export const tripPlan: TripPlan = {
  mode: 'bike',
  estimated: true,
  options: [
    {
      stops: [
        { storeId: 2, chainCode: 'HOEKWINKEL', storeName: 'Hoekwinkel Centrum', latitude: 52.07, longitude: 4.31 },
        { storeId: 1, chainCode: 'BUDGETBAAS', storeName: 'Budgetbaas Moerwijk', latitude: 52.05, longitude: 4.29 },
      ],
      legs: [
        { durationSeconds: 300, distanceMeters: 1240 },
        { durationSeconds: 720, distanceMeters: 2900 },
        { durationSeconds: 900, distanceMeters: 3760 },
      ],
      totalDurationSeconds: 1920,
      totalDistanceMeters: 7900,
      extraSeconds: 0,
      estimated: true,
      geometry: [
        [52.0809, 4.3245],
        [52.07, 4.31],
        [52.05, 4.29],
        [52.0809, 4.3245],
      ],
    },
    {
      stops: [
        { storeId: 3, chainCode: 'HOEKWINKEL', storeName: 'Hoekwinkel Laak', latitude: 52.064, longitude: 4.327 },
        { storeId: 1, chainCode: 'BUDGETBAAS', storeName: 'Budgetbaas Moerwijk', latitude: 52.05, longitude: 4.29 },
      ],
      legs: [
        { durationSeconds: 420, distanceMeters: 1750 },
        { durationSeconds: 780, distanceMeters: 3200 },
        { durationSeconds: 900, distanceMeters: 3760 },
      ],
      totalDurationSeconds: 2100,
      totalDistanceMeters: 8710,
      extraSeconds: 180,
      estimated: true,
      geometry: [
        [52.0809, 4.3245],
        [52.064, 4.327],
        [52.05, 4.29],
        [52.0809, 4.3245],
      ],
    },
  ],
}

export type Routes = Record<string, { ok: boolean; status?: number; body?: unknown }>

/** Stubs fetch: the first route whose key is a prefix of the URL answers. */
export function mockApi(routes: Routes) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
    const url = input.toString()
    const route = Object.entries(routes).find(([prefix]) => url.startsWith(prefix))?.[1]
    if (!route) return { ok: false, status: 404, json: async () => ({}) }
    return { ok: route.ok, status: route.status ?? 200, json: async () => route.body }
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

export const healthyApi: Routes = {
  '/api/system/info': { ok: true, body: { app: 'basketwise', dataMode: 'demo', databaseUp: true } },
  '/api/chains': { ok: true, body: chains },
  '/api/stores': { ok: true, body: stores },
  '/api/products/search': { ok: true, body: [milk] },
  '/api/routing/config': { ok: true, body: routingConfig },
  '/api/routing/nearest': { ok: true, body: nearestStores },
  '/api/routing/trip': { ok: true, body: tripPlan },
}

/** URLs of all fetch calls that start with the prefix, in order. */
export function callsTo(fetchMock: ReturnType<typeof mockApi>, prefix: string): string[] {
  return fetchMock.mock.calls.map(([url]) => url.toString()).filter((url) => url.startsWith(prefix))
}

type FakeGeolocation =
  | { kind: 'granted'; latitude: number; longitude: number }
  | { kind: 'denied' }
  | { kind: 'never' }

/** Replaces navigator.geolocation (jsdom has none). Returns a cleanup function. */
export function mockGeolocation(fake: FakeGeolocation): () => void {
  const geolocation = {
    getCurrentPosition: vi.fn((success: PositionCallback, failure?: PositionErrorCallback | null) => {
      if (fake.kind === 'granted') {
        success({
          coords: { latitude: fake.latitude, longitude: fake.longitude, accuracy: 30 },
          timestamp: Date.now(),
        } as GeolocationPosition)
      } else if (fake.kind === 'denied') {
        failure?.({ code: 1, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3, message: 'User denied' } as GeolocationPositionError)
      }
      // 'never': the user ignores the permission prompt.
    }),
  }
  Object.defineProperty(navigator, 'geolocation', { value: geolocation, configurable: true })
  return () => {
    Reflect.deleteProperty(navigator, 'geolocation')
  }
}
