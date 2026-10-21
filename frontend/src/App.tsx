import { useState } from 'react'
import { ResultsPanel, type ResultsTab } from './components/ResultsPanel'
import { ShoppingListPanel } from './components/ShoppingListPanel'
import { TopBar } from './components/TopBar'
import { InfoIcon } from './components/icons'
import { useBasketPlanner } from './hooks/useBasketPlanner'
import { useCatalog } from './hooks/useCatalog'
import { useShoppingList } from './hooks/useShoppingList'
import { useSystemInfo } from './hooks/useSystemInfo'
import { useTripPlanner } from './hooks/useTripPlanner'

/**
 * List first: the shopping list on the left, the answer on the right (a price comparison or
 * a plan), and the settings every plan depends on in the top bar.
 */
function App() {
  const status = useSystemInfo()
  const { chains, stores, error } = useCatalog()
  const trip = useTripPlanner()
  const list = useShoppingList()
  const basket = useBasketPlanner(list.items, trip.origin, trip.mode)
  const [tab, setTab] = useState<ResultsTab>('compare')
  const config = trip.config.state === 'done' ? trip.config.data : null

  return (
    <div className="app">
      <TopBar
        status={status}
        trip={trip}
        chains={chains}
        stores={stores}
        priority={basket.priority}
        onPriorityChange={basket.setPriority}
      />

      {trip.origin?.notice && (
        <p className="banner" role="status">
          <InfoIcon size={18} />
          <span>{trip.origin.notice}</span>
        </p>
      )}
      {trip.config.state === 'error' && (
        <p className="banner banner--error">Could not load routing settings: {trip.config.message}</p>
      )}
      {error && <p className="banner banner--error">Could not load stores: {error}</p>}

      <div className="layout">
        <ShoppingListPanel list={list} chains={chains} />
        <ResultsPanel
          tab={tab}
          onTabChange={setTab}
          planner={basket}
          items={list.items}
          chains={chains}
          stores={stores}
          provider={config?.provider ?? 'straight-line'}
          origin={trip.origin}
          onOriginChange={trip.moveOrigin}
          serviceArea={config?.serviceArea ?? null}
        />
      </div>
    </div>
  )
}

export default App
