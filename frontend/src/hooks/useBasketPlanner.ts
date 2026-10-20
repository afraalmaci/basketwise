import { useState } from 'react'
import { optimizeBasket, type OptimizeResponse } from '../api/planning'
import type { TravelMode } from '../api/routing'
import type { Origin } from '../location'
import { DEFAULT_PRIORITY, PRIORITIES, type Priority } from '../priorities'
import type { ListItem } from './useShoppingList'
import { useRequest, type RequestState } from './useRequest'
import { coordKey } from './useTripPlanner'

export type BasketPlanner = {
  priority: Priority
  setPriority: (priority: Priority) => void
  result: RequestState<OptimizeResponse>
  /** Index of the plan shown in detail and drawn blue; 0 is the recommendation. */
  activePlan: number
  selectPlan: (index: number) => void
}

/**
 * Asks the optimizer whenever the list, the location, the travel mode or the priority
 * changes. Like the other requests, the key lists every input, so editing the list and
 * changing it back reuses nothing stale, and a slow old answer never overwrites a new one.
 */
export function useBasketPlanner(items: ListItem[], origin: Origin | null, mode: TravelMode): BasketPlanner {
  const [priority, setPriority] = useState<Priority>(DEFAULT_PRIORITY)
  const eurosPerHour = PRIORITIES[priority].eurosPerHour

  // Sorted by product: the order things were added does not change the answer.
  const sorted = [...items].sort((a, b) => a.productId - b.productId)
  const key =
    origin && sorted.length > 0
      ? `optimize:${coordKey(origin.position)}:${mode}:${eurosPerHour}:${sorted.map((i) => `${i.productId}x${i.quantity}`).join(',')}`
      : null

  const result = useRequest<OptimizeResponse>(key, (signal) =>
    optimizeBasket(
      {
        origin: origin!.position,
        mode,
        items: sorted.map((item) => ({ productId: item.productId, quantity: item.quantity })),
        valueOfTimeEurPerHour: eurosPerHour,
      },
      signal,
    ),
  )

  // The chosen plan belongs to one request; a new request starts at the recommendation.
  const [chosen, setChosen] = useState<{ key: string | null; index: number }>({ key: null, index: 0 })
  const activePlan = chosen.key === key ? chosen.index : 0

  return {
    priority,
    setPriority,
    result,
    activePlan,
    selectPlan: (index) => setChosen({ key, index }),
  }
}
