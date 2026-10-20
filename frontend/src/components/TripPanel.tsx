import type { ReactNode } from 'react'
import type { Chain } from '../api/catalog'
import type { NearestStore, TravelMode } from '../api/routing'
import { formatDistance, formatDuration } from '../format'
import type { TripPlanner } from '../hooks/useTripPlanner'
import { BikeIcon, InfoIcon, WalkIcon } from './icons'

type Props = {
  planner: TripPlanner
}

const MODES: Record<TravelMode, { label: string; icon: ReactNode }> = {
  bike: { label: 'Bike', icon: <BikeIcon /> },
  walk: { label: 'Walk', icon: <WalkIcon /> },
}

/** How the user travels and where from. */
export function TripPanel({ planner }: Props) {
  const { config, origin, geolocation } = planner

  if (config.state === 'error') {
    return <p className="panel-section error">Could not load routing settings: {config.message}</p>
  }
  if (config.state !== 'done' || !origin) {
    return <p className="panel-section muted">Loading map settings…</p>
  }

  return (
    <section className="panel-section" aria-label="Travel">
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
    </section>
  )
}

type NearestListProps = { planner: TripPlanner; chains: Chain[] }

/**
 * The closest store of each chain, for reference. The optimizer picks the stores itself, so
 * this list is information, not a selection.
 */
export function NearestList({ planner, chains }: NearestListProps) {
  const { nearest } = planner
  const chainByCode = new Map(chains.map((chain) => [chain.code, chain]))

  if (nearest.state === 'idle') return null
  if (nearest.state === 'error') {
    return <p className="panel-section error">Could not load travel times: {nearest.message}</p>
  }
  const data = nearest.state === 'done' ? nearest.data : nearest.state === 'loading' ? nearest.previous : null
  if (!data) {
    return <p className="panel-section muted">Calculating travel times…</p>
  }

  return (
    <section className="panel-section panel-section--flush" aria-labelledby="nearest-heading">
      <h3 id="nearest-heading" className="section-subtitle">
        Nearest store per chain
      </h3>
      <ul
        className={nearest.state === 'loading' ? 'place-list is-stale' : 'place-list'}
        aria-busy={nearest.state === 'loading'}
      >
        {data.stores.map((store) => (
          <NearestRow key={store.storeId} store={store} chain={chainByCode.get(store.chainCode)} />
        ))}
      </ul>
      {data.estimated && <p className="hint">Times are straight-line estimates.</p>}
    </section>
  )
}

function NearestRow({ store, chain }: { store: NearestStore; chain: Chain | undefined }) {
  return (
    <li className="place">
      <span className="place__marker" style={{ background: chain?.color }} aria-hidden />
      <span className="place__text">
        <span className="place__name">{store.storeName}</span>
        <span className="place__meta">
          {formatDuration(store.durationSeconds)} · {formatDistance(store.distanceMeters)}
        </span>
      </span>
    </li>
  )
}
