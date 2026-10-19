import type { ReactNode } from 'react'
import type { Chain } from '../api/catalog'
import type { NearestStore, RoutingConfig, TravelMode, TripOption } from '../api/routing'
import { formatDistance, formatDuration } from '../format'
import type { TripPlanner } from '../hooks/useTripPlanner'
import { BikeIcon, InfoIcon, WalkIcon } from './icons'

type Props = {
  planner: TripPlanner
  chains: Chain[]
}

const MODES: Record<TravelMode, { label: string; icon: ReactNode }> = {
  bike: { label: 'Bike', icon: <BikeIcon /> },
  walk: { label: 'Walk', icon: <WalkIcon /> },
}

export function TripPanel({ planner, chains }: Props) {
  const { config, origin, geolocation } = planner
  const chainByCode = new Map(chains.map((chain) => [chain.code, chain]))

  if (config.state === 'error') {
    return <p className="panel-section error">Could not load routing settings: {config.message}</p>
  }
  if (config.state !== 'done' || !origin) {
    return <p className="panel-section muted">Loading map settings…</p>
  }

  return (
    <section className="trip" aria-labelledby="trip-heading">
      <div className="panel-section">
        <h2 id="trip-heading" className="section-title">
          Plan a trip
        </h2>

        <fieldset className="mode-tabs">
          <legend className="visually-hidden">Travel mode</legend>
          {config.data.modes.map((mode) => (
            <label key={mode} className={planner.mode === mode ? 'mode-tab is-active' : 'mode-tab'}>
              <input
                type="radio"
                name="travel-mode"
                value={mode}
                checked={planner.mode === mode}
                onChange={() => planner.setMode(mode)}
              />
              {MODES[mode].icon}
              <span>{MODES[mode].label}</span>
            </label>
          ))}
        </fieldset>

        {origin.notice && (
          <p className="notice" role="status">
            <InfoIcon size={18} />
            <span>{origin.notice}</span>
          </p>
        )}
        {origin.source === 'manual' && (
          <button type="button" className="text-button" onClick={planner.resetOrigin}>
            {geolocation.status === 'granted' ? 'Use my location' : 'Reset location'}
          </button>
        )}
      </div>

      <TripSummary planner={planner} config={config.data} chainByCode={chainByCode} />
      <NearestList planner={planner} chainByCode={chainByCode} />
    </section>
  )
}

type NearestListProps = { planner: TripPlanner; chainByCode: Map<string, Chain> }

function NearestList({ planner, chainByCode }: NearestListProps) {
  const { nearest, selectedStoreIds, maxStops } = planner

  if (nearest.state === 'error') {
    return <p className="panel-section error">Could not load travel times: {nearest.message}</p>
  }
  const data = nearest.state === 'done' ? nearest.data : nearest.state === 'loading' ? nearest.previous : null
  if (!data) {
    return <p className="panel-section muted">Calculating travel times…</p>
  }

  const full = selectedStoreIds.length >= maxStops
  return (
    <div className="panel-section panel-section--flush">
      <h3 className="section-subtitle">Nearest store per chain</h3>
      <ul className={nearest.state === 'loading' ? 'place-list is-stale' : 'place-list'} aria-busy={nearest.state === 'loading'}>
        {data.stores.map((store) => (
          <NearestRow
            key={store.storeId}
            store={store}
            chain={chainByCode.get(store.chainCode)}
            checked={selectedStoreIds.includes(store.storeId)}
            disabled={full && !selectedStoreIds.includes(store.storeId)}
            onToggle={() => planner.toggleStore(store.storeId)}
          />
        ))}
      </ul>
      <p className="hint">
        Pick up to {maxStops} stores, or click any store on the map.
        {data.estimated && ' Times are straight-line estimates.'}
      </p>
    </div>
  )
}

type NearestRowProps = {
  store: NearestStore
  chain: Chain | undefined
  checked: boolean
  disabled: boolean
  onToggle: () => void
}

function NearestRow({ store, chain, checked, disabled, onToggle }: NearestRowProps) {
  const classes = ['place', checked && 'is-checked', disabled && 'is-disabled'].filter(Boolean).join(' ')
  return (
    <li>
      <label className={classes}>
        <span className="place__marker" style={{ background: chain?.color }} aria-hidden />
        <span className="place__text">
          <span className="place__name">{store.storeName}</span>
          <span className="place__meta">
            {formatDuration(store.durationSeconds)} · {formatDistance(store.distanceMeters)}
          </span>
        </span>
        <input type="checkbox" className="place__check" checked={checked} disabled={disabled} onChange={onToggle} />
      </label>
    </li>
  )
}

type TripSummaryProps = { planner: TripPlanner; config: RoutingConfig; chainByCode: Map<string, Chain> }

/**
 * Directions-style card: the trip options (fastest first, like route alternatives in a
 * navigation app), then a timeline of the option that is selected.
 */
function TripSummary({ planner, config, chainByCode }: TripSummaryProps) {
  const { trip } = planner
  if (trip.state === 'idle') return null
  if (trip.state === 'error') {
    return <p className="panel-section error">Could not plan the trip: {trip.message}</p>
  }
  const data = trip.state === 'done' ? trip.data : trip.previous
  if (!data || data.options.length === 0) {
    return <p className="panel-section muted">Planning route…</p>
  }

  const activeIndex = data.options[planner.activeOption] ? planner.activeOption : 0
  const active = data.options[activeIndex]
  const stopCount = active.stops.length

  return (
    <div
      className={trip.state === 'loading' ? 'panel-section route-card is-stale' : 'panel-section route-card'}
      aria-busy={trip.state === 'loading'}
    >
      <p className="route-card__subtitle">
        Round trip · {stopCount} {stopCount === 1 ? 'stop' : 'stops'}
      </p>

      <div className="route-options" role="radiogroup" aria-label="Route options">
        {data.options.map((option, index) => (
          <RouteOptionCard
            key={index}
            option={option}
            fastest={index === 0}
            active={index === activeIndex}
            onSelect={() => planner.selectOption(index)}
          />
        ))}
      </div>

      <ol className="timeline" aria-label="Trip stops">
        <li className="timeline__item timeline__item--origin">
          <span className="timeline__node" aria-hidden />
          <span className="timeline__name">Your location</span>
        </li>
        {active.stops.map((stop, index) => (
          <li key={stop.storeId} className="timeline__item">
            <span className="timeline__leg">{formatDuration(active.legs[index].durationSeconds)}</span>
            <span
              className="timeline__node timeline__node--stop"
              style={{ background: chainByCode.get(stop.chainCode)?.color }}
              aria-hidden
            >
              {index + 1}
            </span>
            <span className="timeline__name">{stop.storeName}</span>
          </li>
        ))}
        <li className="timeline__item timeline__item--origin">
          <span className="timeline__leg">{formatDuration(active.legs[active.legs.length - 1].durationSeconds)}</span>
          <span className="timeline__node" aria-hidden />
          <span className="timeline__name">Back to start</span>
        </li>
      </ol>

      {active.estimated && <EstimateNote mode={data.mode} provider={config.provider} />}
    </div>
  )
}

type RouteOptionCardProps = { option: TripOption; fastest: boolean; active: boolean; onSelect: () => void }

function RouteOptionCard({ option, fastest, active, onSelect }: RouteOptionCardProps) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={active}
      className={active ? 'route-option is-active' : 'route-option'}
      onClick={onSelect}
    >
      <span className="route-option__time">
        {formatDuration(option.totalDurationSeconds)}
        <span className="route-option__distance"> · {formatDistance(option.totalDistanceMeters)}</span>
      </span>
      <span className="route-option__tag">
        {fastest ? 'Fastest' : `+${formatDuration(option.extraSeconds)}`}
      </span>
      <span className="route-option__via">via {option.stops.map((stop) => stop.storeName).join(' → ')}</span>
    </button>
  )
}

function EstimateNote({ mode, provider }: { mode: TravelMode; provider: string }) {
  const speed = mode === 'bike' ? '15 km/h' : '5 km/h'
  return (
    <p className="hint">
      {provider === 'straight-line'
        ? `Estimated from straight-line distance at ${speed}; no routing API key is configured.`
        : `The routing service is unavailable, so times are estimated at ${speed}.`}
    </p>
  )
}
