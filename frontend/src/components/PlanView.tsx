import { useState } from 'react'
import type { Chain, Store } from '../api/catalog'
import type { OptimizeResponse, Plan, Purchase } from '../api/planning'
import type { LatLng, ServiceArea, TravelMode } from '../api/routing'
import { formatDistance, formatDuration, formatPrice } from '../format'
import type { Origin } from '../location'
import { difference, extraText, packsText, verdict } from '../planText'
import { MapView } from './MapView'
import { Price } from './Price'

type Props = {
  data: OptimizeResponse
  chains: Chain[]
  stores: Store[]
  provider: string
  origin: Origin | null
  onOriginChange: (position: LatLng) => void
  serviceArea: ServiceArea | null
  /** Index of the plan shown in detail; 0 is the recommendation. */
  activePlan: number
  onSelectPlan: (index: number) => void
}

/**
 * The recommendation and its alternatives side by side, then the selected plan as a route:
 * stops in order with what to buy at each (ticked off while shopping), next to the map.
 */
export function PlanView({ data, chains, stores, provider, origin, onOriginChange, serviceArea, activePlan, onSelectPlan }: Props) {
  if (data.plans.length === 0) {
    return (
      <div className="results__body">
        <p className="muted">Nothing on your list is sold in the demo stores.</p>
      </div>
    )
  }
  const index = data.plans[activePlan] ? activePlan : 0
  const active = data.plans[index]
  const best = data.plans[0]
  const chainByCode = new Map(chains.map((chain) => [chain.code, chain]))

  return (
    <div className="results__body">
      <p className="verdict">{verdict(data.plans, data.valueOfTimeEurPerHour)}</p>

      <div className="plan-cards" role="radiogroup" aria-label="Shopping plans">
        {data.plans.map((plan, i) => (
          <button
            key={plan.storeCount}
            type="button"
            role="radio"
            aria-checked={i === index}
            className={i === index ? 'plan-card is-active' : 'plan-card'}
            onClick={() => onSelectPlan(i)}
          >
            <span className="plan-card__price">
              <Price cents={plan.basketCents} />
              <span className="plan-card__time"> · {formatDuration(plan.travelSeconds)}</span>
            </span>
            <span className={i === 0 ? 'plan-card__tag plan-card__tag--best' : 'plan-card__tag'}>
              {i === 0 ? 'Best' : difference(plan, best)}
            </span>
            <span className="plan-card__stores">
              {plan.storeCount} {plan.storeCount === 1 ? 'store' : 'stores'}: {plan.stops.map((stop) => stop.storeName).join(', ')}
            </span>
          </button>
        ))}
      </div>

      <div className="plan-detail">
        <section className="plan-detail__route" aria-labelledby="route-heading">
          <h2 id="route-heading" className="section-title">
            Shopping route
          </h2>
          {/* Keyed by plan: another plan starts with nothing ticked. */}
          <RouteChecklist key={planKey(active)} plan={active} mode={data.mode} chainByCode={chainByCode} />
          <p className="score-note">
            Round trip {formatDistance(active.travelDistanceMeters)}. Score: {formatPrice(active.basketCents)} +{' '}
            {formatDuration(active.travelSeconds)} at €{data.valueOfTimeEurPerHour}/h ({formatPrice(active.timeCostCents)}) ={' '}
            {formatPrice(active.scoreCents)}. Compared {data.combinationsChecked} store combinations.
            {data.estimated &&
              (provider === 'straight-line'
                ? ' Travel times are straight-line estimates; no routing API key is configured.'
                : ' The routing service is unavailable, so travel times are estimated.')}
          </p>
        </section>

        <section className="plan-detail__map" aria-label="Route map">
          <MapView
            className="map map--plan"
            chains={chains}
            stores={stores}
            origin={origin}
            onOriginChange={onOriginChange}
            serviceArea={serviceArea}
            routes={data.plans}
            activeRoute={index}
            onSelectRoute={onSelectPlan}
          />
        </section>
      </div>
    </div>
  )
}

const planKey = (plan: Plan) => plan.stops.map((stop) => stop.storeId).join('-')

type RouteChecklistProps = { plan: Plan; mode: TravelMode; chainByCode: Map<string, Chain> }

function RouteChecklist({ plan, mode, chainByCode }: RouteChecklistProps) {
  const [done, setDone] = useState<Set<string>>(() => new Set())
  const by = mode === 'bike' ? 'by bike' : 'on foot'
  const toggle = (key: string) =>
    setDone((current) => {
      const next = new Set(current)
      if (!next.delete(key)) next.add(key)
      return next
    })

  return (
    <ol className="route" aria-label="Shopping route">
      <li className="route__end">
        <span className="route__node" aria-hidden />
        <span>Start</span>
      </li>
      {plan.stops.map((stop, i) => (
        <li key={stop.storeId} className="route__stop">
          <p className="route__leg">
            {formatDuration(plan.legs[i]?.durationSeconds ?? 0)} {by}
          </p>
          <div className="route__store">
            <span className="route__number" style={{ background: chainByCode.get(stop.chainCode)?.color }} aria-hidden>
              {i + 1}
            </span>
            <span className="route__name">{stop.storeName}</span>
            <span className="route__subtotal">
              <Price cents={stop.subtotalCents} />
            </span>
          </div>
          <ul className="buy-list" aria-label={`Buy at ${stop.storeName}`}>
            {stop.items.map((item) => {
              const key = `${stop.storeId}:${item.productId}`
              return <BuyRow key={key} item={item} checked={done.has(key)} onToggle={() => toggle(key)} />
            })}
          </ul>
        </li>
      ))}
      <li className="route__end">
        <p className="route__leg">
          {formatDuration(plan.legs[plan.legs.length - 1]?.durationSeconds ?? 0)} {by}
        </p>
        <span className="route__node" aria-hidden />
        <span>Back to start</span>
      </li>
    </ol>
  )
}

function BuyRow({ item, checked, onToggle }: { item: Purchase; checked: boolean; onToggle: () => void }) {
  const extra = extraText(item.boughtAmount, item.neededAmount, item.unit)
  return (
    <li className={checked ? 'buy-list__item is-done' : 'buy-list__item'}>
      <label className="buy-list__label">
        <input type="checkbox" checked={checked} onChange={onToggle} />
        <span>
          {item.name}
          <span className="buy-list__packs">
            {' '}
            · {packsText(item.packs)}
            {extra && ` (${extra})`}
          </span>
        </span>
      </label>
      <span className="buy-list__price">
        <Price cents={item.costCents} />
      </span>
    </li>
  )
}
