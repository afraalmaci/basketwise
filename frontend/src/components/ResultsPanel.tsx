import { useId, type KeyboardEvent } from 'react'
import type { Chain, Store } from '../api/catalog'
import type { LatLng, ServiceArea } from '../api/routing'
import type { BasketPlanner } from '../hooks/useBasketPlanner'
import type { ListItem } from '../hooks/useShoppingList'
import type { Origin } from '../location'
import { ComparisonView } from './ComparisonView'
import { PlanView } from './PlanView'

export type ResultsTab = 'compare' | 'plan'

const TABS: { id: ResultsTab; label: string }[] = [
  { id: 'compare', label: 'Compare' },
  { id: 'plan', label: 'Plan' },
]

type Props = {
  tab: ResultsTab
  onTabChange: (tab: ResultsTab) => void
  planner: BasketPlanner
  items: ListItem[]
  chains: Chain[]
  stores: Store[]
  provider: string
  origin: Origin | null
  onOriginChange: (position: LatLng) => void
  serviceArea: ServiceArea | null
}

/** The right column: the optimizer's answer as a comparison table or as a plan. */
export function ResultsPanel({ tab, onTabChange, planner, items, ...rest }: Props) {
  const baseId = useId()
  const { result } = planner
  const data = result.state === 'done' ? result.data : result.state === 'loading' ? result.previous : null

  // Arrow keys move between tabs, as in the WAI-ARIA tabs pattern.
  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
    const next = TABS[(TABS.findIndex((t) => t.id === tab) + 1) % TABS.length]
    onTabChange(next.id)
    document.getElementById(`${baseId}-tab-${next.id}`)?.focus()
  }

  if (items.length === 0) {
    return (
      <section className="card results results--empty" aria-label="Results">
        <h2 className="section-title">Where should you shop?</h2>
        <p className="muted">
          Add products to your list. BasketWise prices them at every chain, then works out whether a second store is
          worth the trip.
        </p>
      </section>
    )
  }

  return (
    <section className="card results" aria-label="Results">
      <div className="tabs" role="tablist" aria-label="Results" onKeyDown={onKeyDown}>
        {TABS.map((t) => (
          <button
            key={t.id}
            id={`${baseId}-tab-${t.id}`}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            aria-controls={`${baseId}-panel`}
            tabIndex={tab === t.id ? 0 : -1}
            className={tab === t.id ? 'tab is-active' : 'tab'}
            onClick={() => onTabChange(t.id)}
          >
            {t.label}
          </button>
        ))}
      </div>

      <div
        id={`${baseId}-panel`}
        role="tabpanel"
        aria-labelledby={`${baseId}-tab-${tab}`}
        aria-busy={result.state === 'loading'}
        className={result.state === 'loading' && data ? 'is-stale' : undefined}
      >
        {result.state === 'error' && <p className="results__body error">Could not plan your shopping: {result.message}</p>}
        {result.state !== 'error' && !data && <p className="results__body muted">Comparing prices…</p>}
        {result.state !== 'error' && data && tab === 'compare' && (
          <ComparisonView data={data} chains={rest.chains} items={items} onSeePlan={() => onTabChange('plan')} />
        )}
        {result.state !== 'error' && data && tab === 'plan' && (
          <PlanView data={data} activePlan={planner.activePlan} onSelectPlan={planner.selectPlan} {...rest} />
        )}
      </div>
    </section>
  )
}
