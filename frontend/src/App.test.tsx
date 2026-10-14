import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

// Leaflet needs a real browser layout engine; jsdom does not provide one.
vi.mock('./components/MapView', () => ({ MapView: () => <div data-testid="map" /> }))

function mockFetch(response: Partial<Response>) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response))
}

describe('App', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows the demo data badge when the API is healthy', async () => {
    mockFetch({
      ok: true,
      json: async () => ({ app: 'basketwise', dataMode: 'demo', databaseUp: true }),
    })

    render(<App />)

    expect(await screen.findByText('Demo data')).toBeInTheDocument()
    expect(screen.getByTestId('map')).toBeInTheDocument()
  })

  it('shows an error badge when the API fails', async () => {
    mockFetch({ ok: false, status: 503 })

    render(<App />)

    expect(await screen.findByText('API unavailable')).toBeInTheDocument()
  })

  it('shows a database error when the API reports the database down', async () => {
    mockFetch({
      ok: true,
      json: async () => ({ app: 'basketwise', dataMode: 'demo', databaseUp: false }),
    })

    render(<App />)

    expect(await screen.findByText('Database unavailable')).toBeInTheDocument()
  })
})
