import 'leaflet/dist/leaflet.css'
import L from 'leaflet'
import { useEffect, useRef, useSyncExternalStore } from 'react'
import {
  Circle,
  CircleMarker,
  MapContainer,
  Marker,
  Polyline,
  Rectangle,
  TileLayer,
  Tooltip,
  ZoomControl,
  useMap,
} from 'react-leaflet'
import type { Chain, Store } from '../api/catalog'
import type { LatLng, ServiceArea } from '../api/routing'
import type { Origin, OriginSource } from '../location'
import { tileSource } from '../tiles'
import { MyLocationIcon } from './icons'

// Den Haag Centraal. Used until the routing config arrives.
const DEN_HAAG: [number, number] = [52.0809, 4.3245]

// CSS-only markers: Leaflet's default marker images do not resolve through Vite's bundler.
const originIcon = L.divIcon({
  className: 'origin-dot',
  html: '<span class="origin-dot__core"></span>',
  iconSize: [22, 22],
  iconAnchor: [11, 11],
})

/** A teardrop pin in the chain's color, with the stop number inside. */
function pinIcon(color: string, label: string): L.DivIcon {
  return L.divIcon({
    className: 'store-pin',
    html: `<div class="store-pin__body" style="background:${color}"><span>${label}</span></div>`,
    iconSize: [28, 38],
    iconAnchor: [14, 34],
    tooltipAnchor: [0, -30],
  })
}

/** What the map needs to draw a route: the line, and the stops to number. */
export type MapRoute = {
  stops: { storeId: number }[]
  geometry: [number, number][]
  /** Straight-line estimate: drawn dotted so it never passes for a real path. */
  estimated: boolean
}

type Props = {
  chains: Chain[]
  stores: Store[]
  origin?: Origin | null
  /** Makes the blue dot draggable. */
  onOriginChange?: (position: LatLng) => void
  /** "Your location" button; only shown when the device location is known. */
  onLocate?: () => void
  canLocate?: boolean
  serviceArea?: ServiceArea | null
  /** Plans' routes, best first. The active one is drawn blue, the others gray. */
  routes?: MapRoute[]
  activeRoute?: number
  onSelectRoute?: (index: number) => void
  /** Initial zoom; the map zooms to the routes when there are any. */
  zoom?: number
  className?: string
}

export function MapView({
  chains,
  stores,
  origin = null,
  onOriginChange,
  onLocate,
  canLocate = false,
  serviceArea = null,
  routes = [],
  activeRoute = 0,
  onSelectRoute,
  zoom = 13,
  className = 'map',
}: Props) {
  const dark = usePrefersDark()
  const tiles = tileSource(dark)
  const colorByChain = new Map(chains.map((chain) => [chain.code, chain.color]))
  const active = routes[activeRoute] ?? routes[0] ?? null
  const stopNumber = new Map(active?.stops.map((stop, index) => [stop.storeId, index + 1]) ?? [])
  const center: [number, number] = origin ? [origin.position.latitude, origin.position.longitude] : DEN_HAAG

  return (
    <div className={tiles.cssDarken ? `${className} map-wrapper--darken-tiles` : className}>
      <MapContainer center={center} zoom={zoom} className="map__leaflet" scrollWheelZoom zoomControl={false}>
        <TileLayer key={tiles.url} attribution={tiles.attribution} url={tiles.url} subdomains={tiles.subdomains} />
        <ZoomControl position="bottomright" />

        {serviceArea && (
          <Rectangle
            bounds={[
              [serviceArea.minLatitude, serviceArea.minLongitude],
              [serviceArea.maxLatitude, serviceArea.maxLongitude],
            ]}
            pathOptions={{ className: 'service-area', fill: false, dashArray: '4 8', weight: 1.5 }}
            interactive={false}
          />
        )}

        {/* Alternatives first, so the active route is drawn on top of them. */}
        {routes.map((route, index) =>
          index === activeRoute ? null : (
            <RouteLine
              key={`alt-${index}`}
              route={route}
              active={false}
              onClick={onSelectRoute ? () => onSelectRoute(index) : undefined}
            />
          ),
        )}
        {active && <RouteLine key={`active-${activeRoute}`} route={active} active />}

        {stores.map((store) => {
          const color = colorByChain.get(store.chainCode) ?? '#666'
          const number = stopNumber.get(store.id)
          // Stops of the shown route become numbered pins; the rest stay small dots.
          if (number !== undefined) {
            return (
              <Marker
                key={store.id}
                position={[store.latitude, store.longitude]}
                icon={pinIcon(color, String(number))}
                zIndexOffset={500}
              >
                <Tooltip direction="top">{store.name}</Tooltip>
              </Marker>
            )
          }
          return (
            <CircleMarker
              key={store.id}
              center={[store.latitude, store.longitude]}
              radius={6}
              pathOptions={{ className: 'store-dot', weight: 2, fillColor: color, fillOpacity: 1 }}
            >
              <Tooltip direction="top">{store.name}</Tooltip>
            </CircleMarker>
          )
        })}

        {origin?.accuracyMeters !== undefined && origin.accuracyMeters < 2000 && (
          <Circle
            center={[origin.position.latitude, origin.position.longitude]}
            radius={origin.accuracyMeters}
            pathOptions={{ className: 'accuracy-halo', stroke: false, fillOpacity: 0.15 }}
            interactive={false}
          />
        )}

        {origin && (
          <Marker
            position={[origin.position.latitude, origin.position.longitude]}
            icon={originIcon}
            draggable={Boolean(onOriginChange)}
            title={onOriginChange ? 'Your start point (drag to move)' : 'Your start point'}
            zIndexOffset={1000}
            eventHandlers={{
              dragend: (event) => {
                const { lat, lng } = (event.target as L.Marker).getLatLng()
                onOriginChange?.({ latitude: lat, longitude: lng })
              },
            }}
          />
        )}

        {origin && routes.length === 0 && <Recenter origin={origin} />}
        <FitRoutes routes={routes} />
        {origin && canLocate && onLocate && <LocateButton origin={origin} onLocate={onLocate} />}
      </MapContainer>
    </div>
  )
}

type RouteLineProps = { route: MapRoute; active: boolean; onClick?: () => void }

/**
 * Route in the style of navigation apps: a darker casing under a bright line, blue for the
 * route shown in detail and gray for alternatives (click one to show it instead). An
 * estimate (straight lines, no routing API) is drawn dotted so it never passes for a real path.
 */
function RouteLine({ route, active, onClick }: RouteLineProps) {
  const tone = active ? 'route' : 'route-alt'
  const events = onClick ? { click: onClick } : undefined
  if (route.estimated) {
    return (
      <Polyline
        positions={route.geometry}
        pathOptions={{ className: `${tone}-line`, weight: active ? 5 : 4, dashArray: '2 10', lineCap: 'round' }}
        eventHandlers={events}
      />
    )
  }
  return (
    <>
      <Polyline positions={route.geometry} pathOptions={{ className: `${tone}-casing`, weight: 9, lineJoin: 'round' }} eventHandlers={events} />
      <Polyline positions={route.geometry} pathOptions={{ className: `${tone}-line`, weight: 6, lineJoin: 'round' }} eventHandlers={events} />
    </>
  )
}

/**
 * Pans to the origin when it comes from a new source (default → device location), but not
 * while the user drags the pin around: that would fight with their own panning.
 */
function Recenter({ origin }: { origin: Origin }) {
  const map = useMap()
  const lastSource = useRef<OriginSource | null>(null)
  const { latitude, longitude } = origin.position
  const { source } = origin

  useEffect(() => {
    const changed = source !== lastSource.current
    lastSource.current = source
    if (!changed || source === 'manual') return
    map.setView([latitude, longitude], Math.max(map.getZoom(), 13), { animate: false })
  }, [map, source, latitude, longitude])

  return null
}

/**
 * When new routes arrive, zooms the map so every route is visible. Selecting another plan
 * does not move the map: the routes stay the same.
 */
function FitRoutes({ routes }: { routes: MapRoute[] }) {
  const map = useMap()
  // A string of all coordinates: changes only when the routes themselves change.
  const signature = routes.map((route) => route.geometry.map((point) => point.join(',')).join(';')).join('|')

  useEffect(() => {
    if (!signature) return
    const points = signature.split(/[|;]/).map((pair) => pair.split(',').map(Number) as [number, number])
    // No animation: Leaflet ignores a new zoom while a zoom animation is still running, so a
    // quick second change would leave the map at the first route's zoom.
    map.fitBounds(L.latLngBounds(points), { padding: [40, 40], maxZoom: 16, animate: false })
  }, [map, signature])

  return null
}

/** Map button that returns to the device location. */
function LocateButton({ origin, onLocate }: { origin: Origin; onLocate: () => void }) {
  const map = useMap()
  const ref = useRef<HTMLDivElement>(null)

  // Without this, clicking the button would also click (and drag) the map underneath.
  useEffect(() => {
    if (ref.current) L.DomEvent.disableClickPropagation(ref.current)
  }, [])

  return (
    <div ref={ref} className="map-control map-control--locate">
      <button
        type="button"
        aria-label="Use my location"
        title="Use my location"
        onClick={() => {
          onLocate()
          if (origin.source !== 'manual') {
            map.setView([origin.position.latitude, origin.position.longitude], Math.max(map.getZoom(), 15), { animate: false })
          }
        }}
      >
        <MyLocationIcon />
      </button>
    </div>
  )
}

const darkQuery = '(prefers-color-scheme: dark)'

/** Follows the OS color scheme, so the map tiles switch with the rest of the page. */
function usePrefersDark(): boolean {
  return useSyncExternalStore(
    (onChange) => {
      const media = window.matchMedia?.(darkQuery)
      media?.addEventListener('change', onChange)
      return () => media?.removeEventListener('change', onChange)
    },
    () => window.matchMedia?.(darkQuery).matches ?? false,
    () => false,
  )
}
