import { vi } from 'vitest'
import type { Chain, Product, Store } from '../api/catalog'
import type { OptimizeResponse } from '../api/planning'
import type { RoutingConfig } from '../api/routing'

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

/**
 * The optimizer's answer for milk + eggs: two stores is best and cheapest (9 minutes more),
 * one store is the fastest.
 */
export const optimizeResponse: OptimizeResponse = {
  mode: 'bike',
  valueOfTimeEurPerHour: 10,
  estimated: true,
  combinationsChecked: 3,
  unavailable: [],
  plans: [
    {
      storeCount: 2,
      roles: ['best', 'cheapest'],
      basketCents: 498,
      travelSeconds: 1500,
      travelDistanceMeters: 6250,
      timeCostCents: 417,
      scoreCents: 915,
      stops: [
        {
          storeId: 2,
          chainCode: 'HOEKWINKEL',
          storeName: 'Hoekwinkel Centrum',
          latitude: 52.07,
          longitude: 4.31,
          subtotalCents: 349,
          items: [
            {
              productId: 2,
              name: 'Scharreleieren',
              unit: 'pc',
              neededAmount: 10,
              boughtAmount: 10,
              costCents: 349,
              packs: [{ displayName: 'Scharreleieren', quantityText: '10 stuks', amount: 10, priceCents: 349, count: 1 }],
            },
          ],
        },
        {
          storeId: 1,
          chainCode: 'BUDGETBAAS',
          storeName: 'Budgetbaas Moerwijk',
          latitude: 52.05,
          longitude: 4.29,
          subtotalCents: 149,
          items: [
            {
              productId: 1,
              name: 'Halfvolle melk',
              unit: 'ml',
              neededAmount: 1000,
              boughtAmount: 1500,
              costCents: 129,
              packs: [{ displayName: 'BB Halfvolle melk', quantityText: '1,5 L', amount: 1500, priceCents: 129, count: 1 }],
            },
          ],
        },
      ],
      legs: [
        { durationSeconds: 300, distanceMeters: 1240 },
        { durationSeconds: 300, distanceMeters: 1250 },
        { durationSeconds: 900, distanceMeters: 3760 },
      ],
      estimated: true,
      geometry: [
        [52.0809, 4.3245],
        [52.07, 4.31],
        [52.05, 4.29],
        [52.0809, 4.3245],
      ],
    },
    {
      storeCount: 1,
      roles: ['fastest'],
      basketCents: 738,
      travelSeconds: 960,
      travelDistanceMeters: 2480,
      timeCostCents: 267,
      scoreCents: 1005,
      stops: [
        {
          storeId: 2,
          chainCode: 'HOEKWINKEL',
          storeName: 'Hoekwinkel Centrum',
          latitude: 52.07,
          longitude: 4.31,
          subtotalCents: 738,
          items: [],
        },
      ],
      legs: [
        { durationSeconds: 480, distanceMeters: 1240 },
        { durationSeconds: 480, distanceMeters: 1240 },
      ],
      estimated: true,
      geometry: [
        [52.0809, 4.3245],
        [52.07, 4.31],
        [52.0809, 4.3245],
      ],
    },
  ],
  comparison: {
    chains: [
      { chainCode: 'BUDGETBAAS', nearestStoreId: 1, nearestStoreName: 'Budgetbaas Moerwijk', nearestSeconds: 900, totalCents: 129, itemsCovered: 1 },
      { chainCode: 'HOEKWINKEL', nearestStoreId: 2, nearestStoreName: 'Hoekwinkel Centrum', nearestSeconds: 300, totalCents: 498, itemsCovered: 2 },
    ],
    // In product id order, as the backend answers; the UI follows the list order instead.
    items: [
      {
        productId: 1,
        name: 'Halfvolle melk',
        unit: 'ml',
        neededAmount: 1000,
        prices: [
          {
            chainCode: 'BUDGETBAAS',
            costCents: 129,
            boughtAmount: 1500,
            cheapest: true,
            packs: [{ displayName: 'BB Halfvolle melk', quantityText: '1,5 L', amount: 1500, priceCents: 129, count: 1 }],
          },
          {
            chainCode: 'HOEKWINKEL',
            costCents: 149,
            boughtAmount: 1000,
            cheapest: false,
            packs: [{ displayName: 'Halfvolle Melk', quantityText: '1000 ml', amount: 1000, priceCents: 149, count: 1 }],
          },
        ],
      },
      {
        productId: 2,
        name: 'Scharreleieren',
        unit: 'pc',
        neededAmount: 10,
        prices: [
          {
            chainCode: 'HOEKWINKEL',
            costCents: 349,
            boughtAmount: 10,
            cheapest: true,
            packs: [{ displayName: 'Scharreleieren', quantityText: '10 stuks', amount: 10, priceCents: 349, count: 1 }],
          },
        ],
      },
    ],
  },
}

export const eggs: Product = {
  id: 2,
  slug: 'scharreleieren',
  name: 'Scharreleieren',
  category: 'dairy',
  unit: 'pc',
  offers: [
    { chainCode: 'HOEKWINKEL', displayName: 'Scharreleieren', quantityText: '10 stuks', quantityAmount: 10, priceCents: 349, unitPriceCents: 35 },
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
  '/api/optimize': { ok: true, body: optimizeResponse },
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
