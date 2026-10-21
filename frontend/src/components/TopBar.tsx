import type { ReactNode } from 'react'
import type { Chain, Store } from '../api/catalog'
import type { TravelMode } from '../api/routing'
import { formatPrice } from '../format'
import type { SystemStatus } from '../hooks/useSystemInfo'
import type { TripPlanner } from '../hooks/useTripPlanner'
import { isInServiceArea, originLabel } from '../location'
import { PRIORITIES, PRIORITY_ORDER, savingPerTenMinutesCents, type Priority } from '../priorities'
import { BalanceIcon, BikeIcon, LogoMark, PinIcon, WalkIcon } from './icons'
import { MapView } from './MapView'
import { Popover } from './Popover'
import { StatusBadge } from './StatusBadge'

type Props = {
  status: SystemStatus
  trip: TripPlanner
  chains: Chain[]
  stores: Store[]
  priority: Priority
  onPriorityChange: (priority: Priority) => void
}

const MODES: Record<TravelMode, { label: string; icon: ReactNode }> = {
  bike: { label: 'Bike', icon: <BikeIcon size={18} /> },
  walk: { label: 'Walk', icon: <WalkIcon size={18} /> },
}

/** The settings every plan depends on, as chips: where from, how, and what matters more. */
export function TopBar({ status, trip, chains, stores, priority, onPriorityChange }: Props) {
  const config = trip.config.state === 'done' ? trip.config.data : null
  const { origin } = trip

  return (
    <header className="topbar">
      <div className="topbar__brand">
        <h1 className="logo">
          <LogoMark />
          BasketWise
        </h1>
        <StatusBadge status={status} />
      </div>

      {config && origin && (
        <div className="topbar__controls">
          <Popover
            label={
              <>
                <PinIcon size={18} />
                {originLabel(origin, config.serviceArea.name)}
              </>
            }
            ariaLabel={`Start point: ${originLabel(origin, config.serviceArea.name)}. Change`}
            title="Start point"
          >
            {() => (
              <>
                <p className="popover__text">Drag the blue dot to where you start. Travel times are measured from there.</p>
                <MapView
                  className="map map--popover"
                  chains={chains}
                  stores={stores}
                  origin={origin}
                  onOriginChange={trip.moveOrigin}
                  onLocate={trip.resetOrigin}
                  canLocate={
                    trip.geolocation.status === 'granted' && isInServiceArea(trip.geolocation.position, config.serviceArea)
                  }
                  serviceArea={config.serviceArea}
                />
                {origin.source === 'manual' && (
                  <button type="button" className="text-button" onClick={trip.resetOrigin}>
                    {trip.geolocation.status === 'granted' ? 'Use my location' : 'Reset start point'}
                  </button>
                )}
              </>
            )}
          </Popover>

          <fieldset className="segmented">
            <legend className="visually-hidden">Travel mode</legend>
            <div className="segmented__track">
              {config.modes.map((mode) => (
                <label key={mode} className={trip.mode === mode ? 'segmented__option is-active' : 'segmented__option'}>
                  <input
                    type="radio"
                    name="travel-mode"
                    value={mode}
                    checked={trip.mode === mode}
                    onChange={() => trip.setMode(mode)}
                  />
                  {MODES[mode].icon}
                  <span>{MODES[mode].label}</span>
                </label>
              ))}
            </div>
          </fieldset>

          <Popover
            label={
              <>
                <BalanceIcon size={18} />
                {PRIORITIES[priority].label} · €{PRIORITIES[priority].eurosPerHour}/h
              </>
            }
            ariaLabel={`Priority: ${PRIORITIES[priority].label}. Change`}
            title="What matters more?"
            align="end"
          >
            {(close) => (
              <fieldset className="priority-list">
                <legend className="visually-hidden">Priority</legend>
                {PRIORITY_ORDER.map((option) => {
                  const euros = PRIORITIES[option].eurosPerHour
                  return (
                    <label key={option} className={option === priority ? 'priority-option is-active' : 'priority-option'}>
                      <input
                        type="radio"
                        name="priority"
                        value={option}
                        checked={option === priority}
                        onChange={() => {
                          onPriorityChange(option)
                          close()
                        }}
                      />
                      <span className="priority-option__label">{PRIORITIES[option].label}</span>
                      <span className="priority-option__hint">
                        Your time counts as €{euros}/h: 10 extra minutes must save at least{' '}
                        {formatPrice(savingPerTenMinutesCents(euros))}.
                      </span>
                    </label>
                  )
                })}
              </fieldset>
            )}
          </Popover>
        </div>
      )}
    </header>
  )
}
