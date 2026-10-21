import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import type { Store } from './api/catalog'
import { healthyApi, mockApi } from './test/fixtures'

// Leaflet needs a real browser layout engine; jsdom does not provide one.
vi.mock('./components/MapView', () => ({
  MapView: ({ stores }: { stores: Store[] }) => <div data-testid="map">{stores.length} stores</div>,
}))

describe('App', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows the demo badge and loads the stores for the map', async () => {
    const fetchMock = mockApi(healthyApi)

    render(<App />)

    expect(await screen.findByText('Demo data')).toBeInTheDocument()
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => url.toString() === '/api/stores')).toBe(true))
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
