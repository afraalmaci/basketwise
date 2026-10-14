import { MapView } from './components/MapView'
import { ProductSearch } from './components/ProductSearch'
import { StatusBadge } from './components/StatusBadge'
import { useCatalog } from './hooks/useCatalog'
import { useSystemInfo } from './hooks/useSystemInfo'

function App() {
  const status = useSystemInfo()
  const { chains, stores, error } = useCatalog()

  return (
    <div className="layout">
      <header className="header">
        <h1 className="logo">BasketWise</h1>
        <StatusBadge status={status} />
      </header>

      <main className="content">
        <aside className="panel">
          <ProductSearch chains={chains} />
          {error && <p className="error">Could not load stores: {error}</p>}
        </aside>
        <section className="map-section" aria-label="Map">
          <MapView chains={chains} stores={stores} />
        </section>
      </main>
    </div>
  )
}

export default App
