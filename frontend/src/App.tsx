import { MapView } from './components/MapView'
import { StatusBadge } from './components/StatusBadge'
import { useSystemInfo } from './hooks/useSystemInfo'

function App() {
  const status = useSystemInfo()

  return (
    <div className="layout">
      <header className="header">
        <h1 className="logo">BasketWise</h1>
        <StatusBadge status={status} />
      </header>

      <main className="content">
        <aside className="panel">
          <h2>Shopping list</h2>
          <p className="muted">
            Add products here. BasketWise will split your basket across stores and plan
            the route, weighing savings against travel time.
          </p>
        </aside>
        <section className="map-wrapper" aria-label="Map">
          <MapView />
        </section>
      </main>
    </div>
  )
}

export default App
