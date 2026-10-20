/**
 * How much an hour of extra travel is worth to the user. The optimizer scores every plan as
 * basket price + travel time × this value, so the choice decides whether a second store is
 * worth the detour.
 */
export type Priority = 'price' | 'balanced' | 'time'

export const PRIORITIES: Record<Priority, { label: string; eurosPerHour: number }> = {
  price: { label: 'Lowest price', eurosPerHour: 3 },
  balanced: { label: 'Balanced', eurosPerHour: 10 },
  time: { label: 'Least time', eurosPerHour: 25 },
}

export const PRIORITY_ORDER: Priority[] = ['price', 'balanced', 'time']

export const DEFAULT_PRIORITY: Priority = 'balanced'

/** The smallest saving that justifies 10 extra minutes, in cents: €10/h → 167. */
export function savingPerTenMinutesCents(eurosPerHour: number): number {
  return Math.round((eurosPerHour * 100) / 6)
}
