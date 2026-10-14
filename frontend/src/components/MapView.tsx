import 'leaflet/dist/leaflet.css'
import { MapContainer, TileLayer } from 'react-leaflet'

// Den Haag Centraal. Demo stores will be placed around the city.
const DEN_HAAG: [number, number] = [52.0808, 4.3247]

export function MapView() {
  return (
    <MapContainer center={DEN_HAAG} zoom={13} className="map" scrollWheelZoom>
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
    </MapContainer>
  )
}
