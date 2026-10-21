import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import type { Store } from './api/catalog'
import { STORAGE_KEY } from './hooks/useShoppingList'
import type { Origin } from './location'
import { healthyApi, mockApi, mockGeolocation } from './test/fixtures'

// Leaflet needs a real layout engine. The fake map prints what it was given instead.
vi.mock('./components/MapView', () => ({
  MapView: ({ stores, origin }: { stores: Store[]; origin: Origin | null }) => (
    <div data-testid="map">
      {stores.length} stores; origin {origin ? `${origin.position.latitude},${origin.position.longitude} (${origin.source})` : 'none'}
    </div>
  ),
}))

const MILK = { productId: 1, name: 'Halfvolle melk', unit: 'ml', quantity: 1000, step: 1000 }

function lastOptimizeBody(fetchMock: ReturnType<typeof mockApi>) {
  const calls = fetchMock.mock.calls.filter(([url]) => url.toString() === '/api/optimize')
  return calls.length === 0 ? null : JSON.parse(String(calls[calls.length - 1][1]?.body))
}

describe('App start point and travel mode', () => {
  let restoreGeolocation: (() => void) | null = null

  afterEach(() => {
    vi.unstubAllGlobals()
    restoreGeolocation?.()
    restoreGeolocation = null
  })

  it('uses the device location when it is inside the demo area', async () => {
    restoreGeolocation = mockGeolocation({ kind: 'granted', latitude: 52.07, longitude: 4.3 })
    localStorage.setItem(STORAGE_KEY, JSON.stringify([MILK]))
    const fetchMock = mockApi(healthyApi)

    render(<App />)

    expect(await screen.findByRole('button', { name: /Start point: My location/ })).toBeInTheDocument()
    await waitFor(() => expect(lastOptimizeBody(fetchMock)?.origin).toEqual({ latitude: 52.07, longitude: 4.3 }))
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('falls back to the default location when permission is denied', async () => {
    restoreGeolocation = mockGeolocation({ kind: 'denied' })
    mockApi(healthyApi)

    render(<App />)

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Location access is off. Using a sample location in Den Haag; drag the pin to move it.',
    )
    expect(screen.getByRole('button', { name: /Start point: Den Haag \(sample\)/ })).toBeInTheDocument()
  })

  it('falls back to the default location when the device is outside the demo area', async () => {
    restoreGeolocation = mockGeolocation({ kind: 'granted', latitude: 52.3676, longitude: 4.9041 }) // Amsterdam
    localStorage.setItem(STORAGE_KEY, JSON.stringify([MILK]))
    const fetchMock = mockApi(healthyApi)

    render(<App />)

    expect(await screen.findByRole('status')).toHaveTextContent('Demo data only covers Den Haag.')
    await waitFor(() =>
      expect(lastOptimizeBody(fetchMock)?.origin).toEqual({ latitude: 52.0809, longitude: 4.3245 }),
    )
  })

  it('works while the permission prompt is still unanswered', async () => {
    restoreGeolocation = mockGeolocation({ kind: 'never' })
    mockApi(healthyApi)

    render(<App />)

    expect(await screen.findByRole('status')).toHaveTextContent('Waiting for location permission.')
  })

  it('shows the start point on a map in a popover', async () => {
    restoreGeolocation = mockGeolocation({ kind: 'denied' })
    mockApi(healthyApi)
    render(<App />)

    await userEvent.click(await screen.findByRole('button', { name: /Start point/ }))

    const dialog = screen.getByRole('dialog', { name: 'Start point' })
    expect(within(dialog).getByTestId('map')).toHaveTextContent('2 stores; origin 52.0809,4.3245 (default)')

    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('plans again on foot when the mode changes', async () => {
    restoreGeolocation = mockGeolocation({ kind: 'denied' })
    localStorage.setItem(STORAGE_KEY, JSON.stringify([MILK]))
    const fetchMock = mockApi(healthyApi)
    render(<App />)
    await waitFor(() => expect(lastOptimizeBody(fetchMock)?.mode).toBe('bike'))

    await userEvent.click(screen.getByRole('radio', { name: 'Walk' }))

    await waitFor(() => expect(lastOptimizeBody(fetchMock)?.mode).toBe('walk'))
  })
})
