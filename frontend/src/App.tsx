import { useRef } from 'react'
import { MapView } from './components/MapView'
import { ProductSearch } from './components/ProductSearch'
import { StatusBadge } from './components/StatusBadge'
import { TripPanel } from './components/TripPanel'
import { useCatalog } from './hooks/useCatalog'
import { useSystemInfo } from './hooks/useSystemInfo'
import { useTripPlanner } from './hooks/useTripPlanner'
import { isInServiceArea } from './location'

function App() {
  const status = useSystemInfo()
  const { chains, stores, error } = useCatalog()
  const planner = useTripPlanner()
  const panelRef = useRef<HTMLElement>(null)

  const tripOptions = planner.trip.state === 'done' ? planner.trip.data.options : []
  const serviceArea = planner.config.state === 'done' ? planner.config.data.serviceArea : null
  const geo = planner.geolocation
  const canLocate = geo.status === 'granted' && serviceArea !== null && isInServiceArea(geo.position, serviceArea)

  // Full-screen map with a floating panel on top, like a navigation app.
  return (
    <div className="app">
      <main className="map-section" aria-label="Map">
        <MapView
          chains={chains}
          stores={stores}
          origin={planner.origin}
          onOriginChange={planner.moveOrigin}
          onLocate={planner.resetOrigin}
          canLocate={canLocate}
          serviceArea={serviceArea}
          selectedStoreIds={planner.selectedStoreIds}
          onStoreClick={planner.toggleStore}
          tripOptions={tripOptions}
          activeOption={planner.activeOption}
          onSelectOption={planner.selectOption}
          coveredBy={panelRef}
        />
      </main>

      <aside ref={panelRef} className="panel" aria-label="BasketWise">
        <header className="panel__header">
          <h1 className="logo">BasketWise</h1>
          <StatusBadge status={status} />
        </header>
        <div className="panel__body">
          <TripPanel planner={planner} chains={chains} />
          <ProductSearch chains={chains} />
          {error && <p className="panel-section error">Could not load stores: {error}</p>}
        </div>
      </aside>
    </div>
  )
}

export default App
