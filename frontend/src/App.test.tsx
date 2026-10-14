import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import type { Chain, Product, Store } from './api/catalog'

// Leaflet needs a real browser layout engine; jsdom does not provide one.
vi.mock('./components/MapView', () => ({
  MapView: ({ stores }: { stores: Store[] }) => <div data-testid="map">{stores.length} stores</div>,
}))

const chains: Chain[] = [
  { code: 'BUDGETBAAS', name: 'Budgetbaas', color: '#d97706', storeCount: 1 },
  { code: 'HOEKWINKEL', name: 'Hoekwinkel', color: '#2563eb', storeCount: 1 },
]

const stores: Store[] = [
  { id: 1, chainCode: 'BUDGETBAAS', chainName: 'Budgetbaas', name: 'Budgetbaas Moerwijk', latitude: 52.05, longitude: 4.29 },
  { id: 2, chainCode: 'HOEKWINKEL', chainName: 'Hoekwinkel', name: 'Hoekwinkel Centrum', latitude: 52.07, longitude: 4.31 },
]

const milk: Product = {
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

type Routes = Record<string, { ok: boolean; status?: number; body?: unknown }>

function mockApi(routes: Routes) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = input.toString()
    const route = Object.entries(routes).find(([prefix]) => url.startsWith(prefix))?.[1]
    if (!route) return { ok: false, status: 404, json: async () => ({}) }
    return { ok: route.ok, status: route.status ?? 200, json: async () => route.body }
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

const healthyApi: Routes = {
  '/api/system/info': { ok: true, body: { app: 'basketwise', dataMode: 'demo', databaseUp: true } },
  '/api/chains': { ok: true, body: chains },
  '/api/stores': { ok: true, body: stores },
  '/api/products/search': { ok: true, body: [milk] },
}

describe('App', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows the demo badge and passes stores to the map', async () => {
    mockApi(healthyApi)

    render(<App />)

    expect(await screen.findByText('Demo data')).toBeInTheDocument()
    expect(await screen.findByText('2 stores')).toBeInTheDocument()
  })

  it('shows an error badge when the API fails', async () => {
    mockApi({ '/api/system/info': { ok: false, status: 503 } })

    render(<App />)

    expect(await screen.findByText('API unavailable')).toBeInTheDocument()
  })

  it('shows a database error when the API reports the database down', async () => {
    mockApi({ ...healthyApi, '/api/system/info': { ok: true, body: { app: 'basketwise', dataMode: 'demo', databaseUp: false } } })

    render(<App />)

    expect(await screen.findByText('Database unavailable')).toBeInTheDocument()
  })

  it('searches products and highlights the cheapest unit price', async () => {
    const fetchMock = mockApi(healthyApi)
    render(<App />)

    await userEvent.type(screen.getByLabelText('Search products'), 'melk')

    expect(await screen.findByText('Halfvolle melk')).toBeInTheDocument()
    const bestRow = screen.getByText('1,5 L').closest('tr')
    expect(bestRow).toHaveClass('offers__best')
    expect(bestRow).toHaveTextContent('Budgetbaas')
    expect(screen.getByText('1000 ml').closest('tr')).not.toHaveClass('offers__best')

    // Debounced: typing four characters sends one request, not four.
    const searchCalls = fetchMock.mock.calls.filter(([url]) => url.toString().startsWith('/api/products/search'))
    expect(searchCalls).toHaveLength(1)
    expect(searchCalls[0][0].toString()).toBe('/api/products/search?q=melk')
  })

  it('does not search for a single character', async () => {
    const fetchMock = mockApi(healthyApi)
    render(<App />)

    await userEvent.type(screen.getByLabelText('Search products'), 'm')

    expect(await screen.findByText('Type at least 2 characters.')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => url.toString().includes('/search'))).toBe(false)
  })
})
