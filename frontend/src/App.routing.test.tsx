import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import type { Store } from './api/catalog'
import type { TripOption } from './api/routing'
import type { Origin } from './location'
import { callsTo, healthyApi, mockApi, mockGeolocation, routingConfig } from './test/fixtures'

// Leaflet needs a real layout engine. The fake map prints what it was given instead.
vi.mock('./components/MapView', () => ({
  MapView: ({
    stores,
    origin,
    tripOptions = [],
    activeOption = 0,
  }: {
    stores: Store[]
    origin: Origin | null
    tripOptions?: TripOption[]
    activeOption?: number
  }) => (
    <div data-testid="map">
      {stores.length} stores; origin {origin ? `${origin.position.latitude},${origin.position.longitude} (${origin.source})` : 'none'};
      {tripOptions.length > 0 ? ` ${tripOptions.length} routes, active ${activeOption}` : ' no route'}
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

    const row = (await screen.findByText('Hoekwinkel Centrum')).closest('label')!
    expect(row).toHaveTextContent('5 min · 1,2 km')
    expect(screen.getByText(/Times are straight-line estimates/)).toBeInTheDocument()
  })

  it('plans a trip for the selected stores', async () => {
    restoreGeolocation = mockGeolocation({ kind: 'denied' })
    const fetchMock = mockApi(healthyApi)
    render(<App />)

    await userEvent.click(await screen.findByRole('checkbox', { name: /Budgetbaas Moerwijk/ }))
    await userEvent.click(screen.getByRole('checkbox', { name: /Hoekwinkel Centrum/ }))

    expect(await screen.findByText('Round trip · 2 stops')).toBeInTheDocument()
    const options = within(screen.getByRole('radiogroup', { name: 'Route options' })).getAllByRole('radio')
    expect(options.map((option) => option.textContent)).toEqual([
      '32 min · 7,9 kmFastestvia Hoekwinkel Centrum → Budgetbaas Moerwijk',
      '35 min · 8,7 km+3 minvia Hoekwinkel Laak → Budgetbaas Moerwijk',
    ])
    expect(options[0]).toHaveAttribute('aria-checked', 'true')

    const timeline = screen.getByRole('list', { name: 'Trip stops' })
    const items = within(timeline).getAllByRole('listitem')
    expect(items.map((item) => item.querySelector('.timeline__name')?.textContent)).toEqual([
      'Your location',
      'Hoekwinkel Centrum',
      'Budgetbaas Moerwijk',
      'Back to start',
    ])
    expect(items.map((item) => item.querySelector('.timeline__leg')?.textContent ?? null)).toEqual([
      null,
      '5 min',
      '12 min',
      '15 min',
    ])
    expect(screen.getByTestId('map')).toHaveTextContent('2 routes, active 0')

    // The selection is sent sorted, whatever order it was clicked in.
    const tripCalls = fetchMock.mock.calls.filter(([url]) => url.toString() === '/api/routing/trip')
    const lastBody = JSON.parse(String(tripCalls[tripCalls.length - 1][1]?.body))
    expect(lastBody).toEqual({
      origin: { latitude: 52.0809, longitude: 4.3245 },
      storeIds: [1, 2],
      mode: 'bike',
    })
  })

  it('shows an alternative route when it is selected', async () => {
    restoreGeolocation = mockGeolocation({ kind: 'denied' })
    mockApi(healthyApi)
    render(<App />)
    await userEvent.click(await screen.findByRole('checkbox', { name: /Hoekwinkel Centrum/ }))

    await userEvent.click(await screen.findByRole('radio', { name: /Hoekwinkel Laak/ }))

    expect(screen.getByRole('radio', { name: /Hoekwinkel Laak/ })).toHaveAttribute('aria-checked', 'true')
    const names = within(screen.getByRole('list', { name: 'Trip stops' }))
      .getAllByRole('listitem')
      .map((item) => item.querySelector('.timeline__name')?.textContent)
    expect(names).toContain('Hoekwinkel Laak')
    expect(screen.getByTestId('map')).toHaveTextContent('2 routes, active 1')
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

  it('does not allow more stores than the maximum', async () => {
    restoreGeolocation = mockGeolocation({ kind: 'denied' })
    mockApi({ ...healthyApi, '/api/routing/config': { ok: true, body: { ...routingConfig, maxStops: 1 } } })
    render(<App />)

    await userEvent.click(await screen.findByRole('checkbox', { name: /Hoekwinkel Centrum/ }))

    expect(screen.getByRole('checkbox', { name: /Budgetbaas Moerwijk/ })).toBeDisabled()
    expect(screen.getByText(/Pick up to 1 stores/)).toBeInTheDocument()
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
