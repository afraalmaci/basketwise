import 'leaflet/dist/leaflet.css'
import L from 'leaflet'
import { useEffect, useRef, useSyncExternalStore, type RefObject } from 'react'
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

/** A teardrop pin in the chain's color, with the stop number (or a dot) inside. */
function pinIcon(color: string, label: string | null): L.DivIcon {
  return L.divIcon({
    className: 'store-pin',
    html: `<div class="store-pin__body" style="background:${color}"><span>${label ?? ''}</span></div>`,
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
  onOriginChange?: (position: LatLng) => void
  /** "Your location" button; only shown when the device location is known. */
  onLocate?: () => void
  canLocate?: boolean
  serviceArea?: ServiceArea | null
  /** Plans' routes, best first. The active one is drawn blue, the others gray. */
  routes?: MapRoute[]
  activeRoute?: number
  onSelectRoute?: (index: number) => void
  /** The floating panel. Centering accounts for it, so the target is not hidden behind it. */
  coveredBy?: RefObject<HTMLElement | null>
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
  coveredBy,
}: Props) {
  const dark = usePrefersDark()
  const tiles = tileSource(dark)
  const colorByChain = new Map(chains.map((chain) => [chain.code, chain.color]))
  const active = routes[activeRoute] ?? routes[0] ?? null
  const stopNumber = new Map(active?.stops.map((stop, index) => [stop.storeId, index + 1]) ?? [])

  return (
    <div className={tiles.cssDarken ? 'map-wrapper map-wrapper--darken-tiles' : 'map-wrapper'}>
      <MapContainer center={DEN_HAAG} zoom={13} className="map" scrollWheelZoom zoomControl={false}>
        <TileLayer
          key={tiles.url}
          attribution={tiles.attribution}
          url={tiles.url}
          subdomains={tiles.subdomains}
        />
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

          // Stops of the shown route become numbered pins; the rest stay small dots, like
          // points of interest.
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
              radius={7}
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
            title="Your location (drag to move)"
            zIndexOffset={1000}
            eventHandlers={{
              dragend: (event) => {
                const { lat, lng } = (event.target as L.Marker).getLatLng()
                onOriginChange?.({ latitude: lat, longitude: lng })
              },
            }}
          />
        )}

        {origin && <Recenter origin={origin} coveredBy={coveredBy} />}
        <FitRoutes routes={routes} coveredBy={coveredBy} />
        {origin && canLocate && onLocate && <LocateButton origin={origin} onLocate={onLocate} coveredBy={coveredBy} />}
      </MapContainer>

      {chains.length > 0 && (
        <ul className="chips" aria-label="Supermarket chains">
          {chains.map((chain) => (
            <li key={chain.code} className="chip">
              <span className="dot" style={{ background: chain.color }} aria-hidden />
              {chain.name}
              <span className="chip__count">{chain.storeCount}</span>
            </li>
          ))}
        </ul>
      )}
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
      <Polyline
        positions={route.geometry}
        pathOptions={{ className: `${tone}-casing`, weight: 9, lineJoin: 'round' }}
        eventHandlers={events}
      />
      <Polyline
        positions={route.geometry}
        pathOptions={{ className: `${tone}-line`, weight: 6, lineJoin: 'round' }}
        eventHandlers={events}
      />
    </>
  )
}

/**
 * Pans to the origin when it comes from a new source (default → device location), but not
 * while the user drags the pin around: that would fight with their own panning.
 */
function Recenter({ origin, coveredBy }: { origin: Origin; coveredBy?: RefObject<HTMLElement | null> }) {
  const map = useMap()
  const lastSource = useRef<OriginSource | null>(null)
  const { latitude, longitude } = origin.position
  const { source } = origin

  useEffect(() => {
    const changed = source !== lastSource.current
    lastSource.current = source
    if (!changed || source === 'manual') return
    focusOn(map, [latitude, longitude], Math.max(map.getZoom(), 14), coveredBy?.current)
  }, [map, source, latitude, longitude, coveredBy])

  return null
}

/**
 * When new routes arrive, zooms the map so every route is visible, keeping clear of the
 * panel. Selecting another plan does not move the map: the routes stay the same.
 */
function FitRoutes({ routes, coveredBy }: { routes: MapRoute[]; coveredBy?: RefObject<HTMLElement | null> }) {
  const map = useMap()
  // A string of all coordinates: changes only when the routes themselves change.
  const signature = routes.map((route) => route.geometry.map((point) => point.join(',')).join(';')).join('|')

  useEffect(() => {
    if (!signature) return
    const points = signature.split(/[|;]/).map((pair) => pair.split(',').map(Number) as [number, number])
    const mapBox = map.getContainer().getBoundingClientRect()
    const cover = coveredBy?.current?.getBoundingClientRect()
    const margin = 48
    let paddingTopLeft: [number, number] = [margin, margin]
    let paddingBottomRight: [number, number] = [margin, margin]
    if (cover) {
      const isBottomSheet = cover.width >= mapBox.width - 24
      if (isBottomSheet) {
        paddingBottomRight = [margin, mapBox.bottom - cover.top + margin]
      } else {
        paddingTopLeft = [cover.right - mapBox.left + margin, margin]
      }
    }
    // No animation: Leaflet ignores a new zoom while a zoom animation is still running, so a
    // quick second selection would leave the map at the first trip's zoom.
    map.fitBounds(L.latLngBounds(points), { paddingTopLeft, paddingBottomRight, maxZoom: 16, animate: false })
  }, [map, signature, coveredBy])

  return null
}

/** Map button that returns to the device location (and re-centers if already there). */
type LocateButtonProps = { origin: Origin; onLocate: () => void; coveredBy?: RefObject<HTMLElement | null> }

function LocateButton({ origin, onLocate, coveredBy }: LocateButtonProps) {
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
        aria-label="Show your location"
        title="Show your location"
        onClick={() => {
          onLocate()
          if (origin.source !== 'manual') {
            const target: [number, number] = [origin.position.latitude, origin.position.longitude]
            focusOn(map, target, Math.max(map.getZoom(), 15), coveredBy?.current)
          }
        }}
      >
        <MyLocationIcon />
      </button>
    </div>
  )
}

/**
 * Centers the map on a point within the part of the map the panel leaves visible: right of
 * the side panel on desktop, above the bottom sheet on phones.
 */
function focusOn(map: L.Map, target: [number, number], zoom: number, cover: HTMLElement | null | undefined) {
  map.setView(target, zoom, { animate: false })
  if (!cover) return
  const mapBox = map.getContainer().getBoundingClientRect()
  const coverBox = cover.getBoundingClientRect()
  const isBottomSheet = coverBox.width >= mapBox.width - 24
  if (isBottomSheet) {
    map.panBy([0, (mapBox.bottom - coverBox.top) / 2], { animate: false })
  } else {
    map.panBy([-(coverBox.right - mapBox.left) / 2, 0], { animate: false })
  }
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
