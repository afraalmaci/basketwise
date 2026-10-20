import { useRef } from 'react'
import { MapView } from './components/MapView'
import { PlanResults } from './components/PlanResults'
import { ShoppingListPanel } from './components/ShoppingListPanel'
import { StatusBadge } from './components/StatusBadge'
import { NearestList, TripPanel } from './components/TripPanel'
import { useBasketPlanner } from './hooks/useBasketPlanner'
import { useCatalog } from './hooks/useCatalog'
import { useShoppingList } from './hooks/useShoppingList'
import { useSystemInfo } from './hooks/useSystemInfo'
import { useTripPlanner } from './hooks/useTripPlanner'
import { isInServiceArea } from './location'

function App() {
  const status = useSystemInfo()
  const { chains, stores, error } = useCatalog()
  const trip = useTripPlanner()
  const list = useShoppingList()
  const basket = useBasketPlanner(list.items, trip.origin, trip.mode)
  const panelRef = useRef<HTMLElement>(null)

  // While a new answer loads, the map keeps showing the previous routes instead of flashing empty.
  const optimized =
    basket.result.state === 'done' ? basket.result.data : basket.result.state === 'loading' ? basket.result.previous : null
  const routes = list.items.length > 0 && optimized ? optimized.plans : []
  const config = trip.config.state === 'done' ? trip.config.data : null
  const geo = trip.geolocation
  const canLocate = geo.status === 'granted' && config !== null && isInServiceArea(geo.position, config.serviceArea)

  // Full-screen map with a floating panel on top, like a navigation app.
  return (
    <div className="app">
      <main className="map-section" aria-label="Map">
        <MapView
          chains={chains}
          stores={stores}
          origin={trip.origin}
          onOriginChange={trip.moveOrigin}
          onLocate={trip.resetOrigin}
          canLocate={canLocate}
          serviceArea={config?.serviceArea ?? null}
          routes={routes}
          activeRoute={basket.activePlan}
          onSelectRoute={basket.selectPlan}
          coveredBy={panelRef}
        />
      </main>

      <aside ref={panelRef} className="panel" aria-label="BasketWise">
        <header className="panel__header">
          <h1 className="logo">BasketWise</h1>
          <StatusBadge status={status} />
        </header>
        <div className="panel__body">
          <TripPanel planner={trip} />
          <ShoppingListPanel
            list={list}
            chains={chains}
            priority={basket.priority}
            onPriorityChange={basket.setPriority}
          />
          <PlanResults planner={basket} chains={chains} provider={config?.provider ?? 'straight-line'} />
          <NearestList planner={trip} chains={chains} />
          {error && <p className="panel-section error">Could not load stores: {error}</p>}
        </div>
      </aside>
    </div>
  )
}

export default App
