import type { Chain } from '../api/catalog'
import type { OptimizeResponse, Purchase } from '../api/planning'
import type { TravelMode } from '../api/routing'
import { formatDistance, formatDuration, formatPrice, formatQuantity } from '../format'
import type { BasketPlanner } from '../hooks/useBasketPlanner'
import { difference, verdict } from '../planText'

type Props = {
  planner: BasketPlanner
  chains: Chain[]
  provider: string
}

/**
 * The optimizer's answer: one card per number of stores (best first, like route
 * alternatives), a sentence on whether the extra stores are worth it, and a shopping
 * route for the selected plan with what to buy at each stop.
 */
export function PlanResults({ planner, chains, provider }: Props) {
  const { result } = planner
  if (result.state === 'idle') return null
  if (result.state === 'error') {
    return <p className="panel-section error">Could not plan your shopping: {result.message}</p>
  }
  const data = result.state === 'done' ? result.data : result.previous
  if (!data) {
    return <p className="panel-section muted">Finding the best plan…</p>
  }

  const chainByCode = new Map(chains.map((chain) => [chain.code, chain]))
  const stale = result.state === 'loading'

  return (
    <section
      className={stale ? 'panel-section plan-card is-stale' : 'panel-section plan-card'}
      aria-labelledby="plan-heading"
      aria-busy={stale}
    >
      <h2 id="plan-heading" className="section-title">
        Best plan
      </h2>
      {data.unavailable.length > 0 && (
        <p className="notice notice--warning">
          Not sold in any demo store: {data.unavailable.map((item) => item.name).join(', ')}.
        </p>
      )}
      {data.plans.length === 0 ? (
        <p className="muted">Nothing on your list is sold in the demo stores.</p>
      ) : (
        <Plans data={data} planner={planner} chainByCode={chainByCode} provider={provider} />
      )}
    </section>
  )
}

type PlansProps = {
  data: OptimizeResponse
  planner: BasketPlanner
  chainByCode: Map<string, Chain>
  provider: string
}

function Plans({ data, planner, chainByCode, provider }: PlansProps) {
  const best = data.plans[0]
  const activeIndex = data.plans[planner.activePlan] ? planner.activePlan : 0
  const active = data.plans[activeIndex]

  return (
    <>
      <p className="verdict">{verdict(data.plans, data.valueOfTimeEurPerHour)}</p>

      <div className="route-options" role="radiogroup" aria-label="Shopping plans">
        {data.plans.map((plan, index) => (
          <button
            key={plan.storeCount}
            type="button"
            role="radio"
            aria-checked={index === activeIndex}
            className={index === activeIndex ? 'route-option is-active' : 'route-option'}
            onClick={() => planner.selectPlan(index)}
          >
            <span className="route-option__time">
              {formatPrice(plan.basketCents)}
              <span className="route-option__distance"> · {formatDuration(plan.travelSeconds)}</span>
            </span>
            <span className="route-option__tag">{index === 0 ? 'Best' : difference(plan, best)}</span>
            <span className="route-option__via">
              {plan.storeCount} {plan.storeCount === 1 ? 'store' : 'stores'}:{' '}
              {plan.stops.map((stop) => stop.storeName).join(', ')}
            </span>
          </button>
        ))}
      </div>

      <ol className="timeline" aria-label="Shopping route">
        <li className="timeline__item timeline__item--origin">
          <span className="timeline__node" aria-hidden />
          <span className="timeline__name">Your location</span>
        </li>
        {active.stops.map((stop, index) => (
          <li key={stop.storeId} className="timeline__item">
            <span className="timeline__leg">{formatDuration(active.legs[index]?.durationSeconds ?? 0)}</span>
            <span
              className="timeline__node timeline__node--stop"
              style={{ background: chainByCode.get(stop.chainCode)?.color }}
              aria-hidden
            >
              {index + 1}
            </span>
            <span className="timeline__name timeline__name--store">
              <span>{stop.storeName}</span>
              <span className="timeline__subtotal">{formatPrice(stop.subtotalCents)}</span>
            </span>
            <ul className="buy-list" aria-label={`Buy at ${stop.storeName}`}>
              {stop.items.map((item) => (
                <BuyRow key={item.productId} item={item} />
              ))}
            </ul>
          </li>
        ))}
        <li className="timeline__item timeline__item--origin">
          <span className="timeline__leg">
            {formatDuration(active.legs[active.legs.length - 1]?.durationSeconds ?? 0)}
          </span>
          <span className="timeline__node" aria-hidden />
          <span className="timeline__name">Back to start</span>
        </li>
      </ol>

      <p className="hint">
        Round trip {formatDistance(active.travelDistanceMeters)}. Compared {data.combinationsChecked} store
        combinations; each plan scores price + travel time × €{data.valueOfTimeEurPerHour}/h
        {active.timeCostCents > 0 && ` (this one: ${formatPrice(active.basketCents)} + ${formatPrice(active.timeCostCents)})`}.
      </p>
      {data.estimated && <EstimateNote mode={data.mode} provider={provider} />}
    </>
  )
}

function BuyRow({ item }: { item: Purchase }) {
  const packs = item.packs.map((pack) => `${pack.count} × ${pack.quantityText}`).join(' + ')
  const extra = item.boughtAmount - item.neededAmount
  return (
    <li className="buy-list__item">
      <span className="buy-list__name">
        {item.name}
        <span className="buy-list__packs">
          {packs}
          {extra > 0 && ` (${formatQuantity(extra, item.unit)} more than needed)`}
        </span>
      </span>
      <span className="buy-list__price">{formatPrice(item.costCents)}</span>
    </li>
  )
}

function EstimateNote({ mode, provider }: { mode: TravelMode; provider: string }) {
  const speed = mode === 'bike' ? '15 km/h' : '5 km/h'
  return (
    <p className="hint">
      {provider === 'straight-line'
        ? `Travel times are estimated from straight-line distance at ${speed}; no routing API key is configured.`
        : `The routing service is unavailable, so travel times are estimated at ${speed}.`}
    </p>
  )
}
