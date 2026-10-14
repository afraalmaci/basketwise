import 'leaflet/dist/leaflet.css'
import { CircleMarker, MapContainer, TileLayer, Tooltip } from 'react-leaflet'
import type { Chain, Store } from '../api/catalog'

// Den Haag Centraal. Demo stores are placed around the city.
const DEN_HAAG: [number, number] = [52.0708, 4.3047]

type Props = {
  chains: Chain[]
  stores: Store[]
}

export function MapView({ chains, stores }: Props) {
  const colorByChain = new Map(chains.map((chain) => [chain.code, chain.color]))

  return (
    <div className="map-wrapper">
      <MapContainer center={DEN_HAAG} zoom={13} className="map" scrollWheelZoom>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {stores.map((store) => {
          const color = colorByChain.get(store.chainCode) ?? '#666'
          return (
            <CircleMarker
              key={store.id}
              center={[store.latitude, store.longitude]}
              radius={8}
              pathOptions={{ color: '#fff', weight: 2, fillColor: color, fillOpacity: 1 }}
            >
              <Tooltip>{store.name}</Tooltip>
            </CircleMarker>
          )
        })}
      </MapContainer>

      {chains.length > 0 && (
        <ul className="legend" aria-label="Supermarket chains">
          {chains.map((chain) => (
            <li key={chain.code}>
              <span className="dot" style={{ background: chain.color }} aria-hidden />
              {chain.name} <span className="muted">({chain.storeCount})</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
