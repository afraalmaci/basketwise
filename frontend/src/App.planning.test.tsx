import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import type { Store } from './api/catalog'
import type { MapRoute } from './components/MapView'
import { STORAGE_KEY, type ListItem } from './hooks/useShoppingList'
import { eggs, healthyApi, mockApi, mockGeolocation, optimizeResponse } from './test/fixtures'

// Leaflet needs a real layout engine. The fake map prints what it was given instead.
vi.mock('./components/MapView', () => ({
  MapView: ({ stores, routes = [], activeRoute = 0 }: { stores: Store[]; routes?: MapRoute[]; activeRoute?: number }) => (
    <div data-testid="map">
      {stores.length} stores;{routes.length > 0 ? ` ${routes.length} routes, active ${activeRoute}` : ' no route'}
    </div>
  ),
}))

const MILK: ListItem = { productId: 1, name: 'Halfvolle melk', unit: 'ml', quantity: 1000, step: 1000 }
const EGGS: ListItem = { productId: 2, name: 'Scharreleieren', unit: 'pc', quantity: 10, step: 1 }

// Intl puts a non-breaking space after the euro sign.
const normalize = (text: string | null | undefined) => (text ?? '').replace(/ /g, ' ')

function storeList(items: ListItem[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(items))
}

function optimizeBodies(fetchMock: ReturnType<typeof mockApi>) {
  return fetchMock.mock.calls
    .filter(([url]) => url.toString() === '/api/optimize')
    .map(([, init]) => JSON.parse(String(init?.body)))
}

/** Text of every cell, row by row. */
function tableText(table: HTMLElement): string[][] {
  return within(table)
    .getAllByRole('row')
    .map((row) => Array.from(row.querySelectorAll('th, td')).map((cell) => normalize(cell.textContent)))
}

describe('App shopping list and results', () => {
  let restoreGeolocation: () => void

  beforeEach(() => {
    restoreGeolocation = mockGeolocation({ kind: 'denied' })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    restoreGeolocation()
  })

  it('explains what to do and asks nothing while the list is empty', async () => {
    const fetchMock = mockApi(healthyApi)
    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Where should you shop?' })).toBeInTheDocument()
    await screen.findByRole('button', { name: /Start point/ }) // routing config loaded, so the app has settled
    expect(optimizeBodies(fetchMock)).toEqual([])
  })

  it('adds a searched product with its smallest pack as the quantity', async () => {
    const fetchMock = mockApi(healthyApi)
    render(<App />)

    await userEvent.type(screen.getByLabelText('Search products'), 'melk')
    await userEvent.click(await screen.findByRole('button', { name: 'Add Halfvolle melk to list' }))

    const list = screen.getByRole('list', { name: 'Shopping list items' })
    expect(within(list).getByLabelText('Halfvolle melk quantity')).toHaveTextContent('1 L')
    expect(screen.getByText('On list')).toBeInTheDocument()
    await waitFor(() =>
      expect(optimizeBodies(fetchMock).at(-1)).toEqual({
        origin: { latitude: 52.0809, longitude: 4.3245 },
        mode: 'bike',
        items: [{ productId: 1, quantity: 1000 }],
        valueOfTimeEurPerHour: 10,
      }),
    )
  })

  it('starts pieces at the smallest pack but steps them one by one', async () => {
    mockApi({ ...healthyApi, '/api/products/search': { ok: true, body: [eggs] } })
    render(<App />)

    await userEvent.type(screen.getByLabelText('Search products'), 'eier')
    await userEvent.click(await screen.findByRole('button', { name: 'Add Scharreleieren to list' }))
    expect(screen.getByLabelText('Scharreleieren quantity')).toHaveTextContent('10 st')

    await userEvent.click(screen.getByRole('button', { name: 'More Scharreleieren' }))
    expect(screen.getByLabelText('Scharreleieren quantity')).toHaveTextContent('11 st')
  })

  it('steps quantities by the pack size and sends them in base units', async () => {
    storeList([MILK])
    const fetchMock = mockApi(healthyApi)
    render(<App />)

    await userEvent.click(screen.getByRole('button', { name: 'More Halfvolle melk' }))

    expect(screen.getByLabelText('Halfvolle melk quantity')).toHaveTextContent('2 L')
    await waitFor(() => expect(optimizeBodies(fetchMock).at(-1)?.items).toEqual([{ productId: 1, quantity: 2000 }]))
  })

  it('compares the list at every chain, in list order, with the cheapest price marked', async () => {
    storeList([EGGS, MILK])
    mockApi(healthyApi)
    render(<App />)

    const table = await screen.findByRole('table', { name: 'Prices per chain' })
    expect(tableText(table)).toEqual([
      ['Item', 'Budgetbaasnearest 15 min', 'Hoekwinkelnearest 5 min'],
      ['Scharreleieren10 st', '–not sold', '€ 3,491 × 10 stuks'],
      ['Halfvolle melk1 L', '€ 1,291 × 1,5 L · +500 ml', '€ 1,491 × 1000 ml'],
      ['All at one chain', '€ 1,291 of 2 items', '€ 4,98all 2 items'],
    ])
    const cheapestMilk = within(table).getByText('1 × 1,5 L · +500 ml').closest('td')
    expect(cheapestMilk).toHaveClass('compare__cell--best')
    expect(within(table).getByText('1 × 1000 ml').closest('td')).not.toHaveClass('compare__cell--best')
  })

  it('sums up the best plan above the table and opens it', async () => {
    storeList([MILK, EGGS])
    mockApi(healthyApi)
    render(<App />)

    expect(normalize((await screen.findByText(/^2 stores ·/)).textContent)).toBe('2 stores · € 4,98 · 25 min')
    await userEvent.click(screen.getByRole('button', { name: 'See the plan' }))

    expect(screen.getByRole('tab', { name: 'Plan' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('radiogroup', { name: 'Shopping plans' })).toBeInTheDocument()
  })

  it('shows the plans, the route and what to buy where', async () => {
    storeList([MILK, EGGS])
    mockApi(healthyApi)
    render(<App />)
    await userEvent.click(await screen.findByRole('tab', { name: 'Plan' }))

    expect(normalize((await screen.findByText(/The fastest plan/)).textContent)).toBe(
      'The fastest plan saves 9 min but costs € 2,40 more. At €10/h that is not worth it.',
    )
    const plans = within(screen.getByRole('radiogroup', { name: 'Shopping plans' })).getAllByRole('radio')
    expect(plans.map((plan) => normalize(plan.textContent))).toEqual([
      '€ 4,98 · 25 minBest for you · Cheapest2 stores: Hoekwinkel Centrum, Budgetbaas Moerwijk',
      '€ 7,38 · 16 minFastest1 store: Hoekwinkel Centrum+€ 2,40 · −9 min vs. best',
    ])
    expect(plans[0]).toHaveAttribute('aria-checked', 'true')

    const atBudgetbaas = screen.getByRole('list', { name: 'Buy at Budgetbaas Moerwijk' })
    expect(normalize(atBudgetbaas.textContent)).toBe('Halfvolle melk · 1 × 1,5 L (+500 ml)€ 1,29')
    expect(screen.getByTestId('map')).toHaveTextContent('2 routes, active 0')
  })

  it('ticks items off while shopping', async () => {
    storeList([MILK, EGGS])
    mockApi(healthyApi)
    render(<App />)
    await userEvent.click(await screen.findByRole('tab', { name: 'Plan' }))

    const milk = await screen.findByRole('checkbox', { name: /Halfvolle melk/ })
    await userEvent.click(milk)

    expect(milk).toBeChecked()
    expect(milk.closest('li')).toHaveClass('is-done')
  })

  it('shows another plan in detail when it is selected', async () => {
    storeList([MILK, EGGS])
    mockApi(healthyApi)
    render(<App />)
    await userEvent.click(await screen.findByRole('tab', { name: 'Plan' }))

    await userEvent.click(await screen.findByRole('radio', { name: /1 store:/ }))

    expect(screen.getByRole('radio', { name: /1 store:/ })).toHaveAttribute('aria-checked', 'true')
    expect(screen.queryByRole('list', { name: 'Buy at Budgetbaas Moerwijk' })).not.toBeInTheDocument()
    expect(screen.getByTestId('map')).toHaveTextContent('2 routes, active 1')
  })

  it('sends the value of time that belongs to the chosen priority', async () => {
    storeList([MILK])
    const fetchMock = mockApi(healthyApi)
    render(<App />)
    await screen.findByRole('table', { name: 'Prices per chain' })

    await userEvent.click(screen.getByRole('button', { name: /Priority: Balanced/ }))
    const dialog = screen.getByRole('dialog', { name: 'What matters more?' })
    expect(normalize(within(dialog).getByText(/€3\/h/).textContent)).toBe(
      'Your time counts as €3/h: 10 extra minutes must save at least € 0,50.',
    )
    await userEvent.click(within(dialog).getByRole('radio', { name: /Lowest price/ }))

    await waitFor(() => expect(optimizeBodies(fetchMock).at(-1)?.valueOfTimeEurPerHour).toBe(3))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Priority: Lowest price/ })).toHaveTextContent('Lowest price · €3/h')
  })

  it('keeps the list after a reload', async () => {
    mockApi(healthyApi)
    const { unmount } = render(<App />)
    await userEvent.type(screen.getByLabelText('Search products'), 'melk')
    await userEvent.click(await screen.findByRole('button', { name: 'Add Halfvolle melk to list' }))
    unmount()

    render(<App />)

    expect(screen.getByLabelText('Halfvolle melk quantity')).toHaveTextContent('1 L')
  })

  it('goes back to the empty state when the last item is removed', async () => {
    storeList([MILK])
    mockApi(healthyApi)
    render(<App />)
    await screen.findByRole('table', { name: 'Prices per chain' })

    await userEvent.click(screen.getByRole('button', { name: 'Remove Halfvolle melk' }))

    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Where should you shop?' })).toBeInTheDocument()
  })

  it('names items that no store sells', async () => {
    storeList([MILK])
    mockApi({
      ...healthyApi,
      '/api/optimize': { ok: true, body: { ...optimizeResponse, unavailable: [{ productId: 9, name: 'Saffraan' }] } },
    })
    render(<App />)

    expect(await screen.findByText('Not sold in any demo store: Saffraan.')).toBeInTheDocument()
  })

  it('shows the server explanation when optimizing fails', async () => {
    storeList([MILK])
    mockApi({
      ...healthyApi,
      '/api/optimize': { ok: false, status: 400, body: { detail: 'Quantity of Halfvolle melk must be 1 to 25000 ml' } },
    })
    render(<App />)

    expect(
      await screen.findByText('Could not plan your shopping: Quantity of Halfvolle melk must be 1 to 25000 ml'),
    ).toBeInTheDocument()
  })

  it('ignores a corrupt saved list', async () => {
    localStorage.setItem(STORAGE_KEY, '{not json')
    mockApi(healthyApi)
    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Where should you shop?' })).toBeInTheDocument()
  })
})
