import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import type { Store } from './api/catalog'
import type { Origin } from './location'
import { callsTo, healthyApi, mockApi, mockGeolocation } from './test/fixtures'

// Leaflet needs a real layout engine. The fake map prints what it was given instead.
vi.mock('./components/MapView', () => ({
  MapView: ({ stores, origin }: { stores: Store[]; origin: Origin | null }) => (
    <div data-testid="map">
      {stores.length} stores; origin {origin ? `${origin.position.latitude},${origin.position.longitude} (${origin.source})` : 'none'}
    </div>
  ),
}))

const DEFAULT_NEAREST = '/api/routing/nearest?lat=52.0809&lon=4.3245&mode=bike'

describe('App routing', () => {
  let restoreGeolocation: (() => void) | null = null

  afterEach(() => {
    vi.unstubAllGlobals()
    restoreGeolocation?.()
    restoreGeolocation = null
  })

  it('uses the device location when it is inside the demo area', async () => {
    restoreGeolocation = mockGeolocation({ kind: 'granted', latitude: 52.07, longitude: 4.3 })
    const fetchMock = mockApi(healthyApi)

    render(<App />)

    expect(await screen.findByText('Hoekwinkel Centrum')).toBeInTheDocument()
    expect(callsTo(fetchMock, '/api/routing/nearest')).toEqual(['/api/routing/nearest?lat=52.07&lon=4.3&mode=bike'])
    expect(screen.getByTestId('map')).toHaveTextContent('origin 52.07,4.3 (device)')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('falls back to the default location when permission is denied', async () => {
    restoreGeolocation = mockGeolocation({ kind: 'denied' })
    const fetchMock = mockApi(healthyApi)

    render(<App />)

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Location access is off. Using a sample location in Den Haag; drag the pin to move it.',
    )
    await screen.findByText('Hoekwinkel Centrum')
    expect(callsTo(fetchMock, '/api/routing/nearest')).toEqual([DEFAULT_NEAREST])
  })

  it('falls back to the default location when the device is outside the demo area', async () => {
    restoreGeolocation = mockGeolocation({ kind: 'granted', latitude: 52.3676, longitude: 4.9041 }) // Amsterdam
    const fetchMock = mockApi(healthyApi)

    render(<App />)

    expect(await screen.findByRole('status')).toHaveTextContent('Demo data only covers Den Haag.')
    await screen.findByText('Hoekwinkel Centrum')
    expect(callsTo(fetchMock, '/api/routing/nearest')).toEqual([DEFAULT_NEAREST])
    expect(screen.getByTestId('map')).toHaveTextContent('(default)')
  })

  it('works while the permission prompt is still unanswered', async () => {
    restoreGeolocation = mockGeolocation({ kind: 'never' })
    mockApi(healthyApi)

    render(<App />)

    expect(await screen.findByRole('status')).toHaveTextContent('Waiting for location permission.')
    expect(await screen.findByText('Hoekwinkel Centrum')).toBeInTheDocument()
  })

  it('shows travel time and distance for the nearest store of each chain', async () => {
    restoreGeolocation = mockGeolocation({ kind: 'denied' })
    mockApi(healthyApi)

    render(<App />)

    const row = (await screen.findByText('Hoekwinkel Centrum')).closest('li')!
    expect(row).toHaveTextContent('5 min · 1,2 km')
    expect(screen.getByText(/Times are straight-line estimates/)).toBeInTheDocument()
  })

  it('asks again for walking times when the mode changes', async () => {
    restoreGeolocation = mockGeolocation({ kind: 'denied' })
    const fetchMock = mockApi(healthyApi)
    render(<App />)
    await screen.findByText('Hoekwinkel Centrum')

    await userEvent.click(screen.getByRole('radio', { name: 'Walk' }))

    await waitFor(() =>
      expect(callsTo(fetchMock, '/api/routing/nearest')).toEqual([
        DEFAULT_NEAREST,
        '/api/routing/nearest?lat=52.0809&lon=4.3245&mode=walk',
      ]),
    )
  })

  it('shows the server explanation when a request is rejected', async () => {
    restoreGeolocation = mockGeolocation({ kind: 'denied' })
    mockApi({
      ...healthyApi,
      '/api/routing/nearest': { ok: false, status: 400, body: { title: 'Outside demo area', detail: 'Location is outside the demo area' } },
    })

    render(<App />)

    expect(await screen.findByText('Could not load travel times: Location is outside the demo area')).toBeInTheDocument()
  })
})
