import type { Unit } from './api/catalog'
import type { BoughtPack, Plan, PlanRole } from './api/planning'
import { formatDuration, formatPrice, formatQuantity } from './format'

// The sentences that explain a plan. Kept apart from the components so they can be tested
// on their own.

/** "−€ 1,40 · +9 min" compared with the best plan. */
export function difference(plan: Plan, best: Plan): string {
  const price = plan.basketCents - best.basketCents
  const seconds = plan.travelSeconds - best.travelSeconds
  const priceText = price === 0 ? 'same price' : `${price > 0 ? '+' : '−'}${formatPrice(Math.abs(price))}`
  const timeText = Math.abs(seconds) < 30 ? 'same time' : `${seconds > 0 ? '+' : '−'}${formatDuration(Math.abs(seconds))}`
  return `${priceText} · ${timeText}`
}

const ROLE_LABELS: Record<PlanRole, string> = { best: 'Best for you', cheapest: 'Cheapest', fastest: 'Fastest' }

/** "Best for you · Cheapest" */
export function roleLabel(roles: PlanRole[]): string {
  return roles.map((role) => ROLE_LABELS[role]).join(' · ')
}

/**
 * One sentence on the trade-off: what the cheapest and the fastest plan would change
 * compared with the recommendation, and why the recommendation wins at this value of time.
 */
export function verdict(plans: Plan[], eurosPerHour: number): string {
  const best = plans[0]
  const cheapest = plans.find((plan) => plan.roles.includes('cheapest')) ?? best
  const fastest = plans.find((plan) => plan.roles.includes('fastest')) ?? best
  const rate = `At €${eurosPerHour}/h`
  const money = (cents: number) => formatPrice(Math.abs(cents))
  const time = (seconds: number) => formatDuration(Math.abs(seconds))

  if (cheapest === best && fastest === best) {
    return 'This plan is both the cheapest and the fastest way to buy your list.'
  }
  if (cheapest === best) {
    return `The fastest plan saves ${time(best.travelSeconds - fastest.travelSeconds)} but costs ${money(fastest.basketCents - best.basketCents)} more. ${rate} that is not worth it.`
  }
  if (fastest === best) {
    return `The cheapest plan saves ${money(best.basketCents - cheapest.basketCents)} but takes ${time(cheapest.travelSeconds - best.travelSeconds)} longer. ${rate} that is not worth it.`
  }
  return `The cheapest plan would save ${money(best.basketCents - cheapest.basketCents)} for ${time(cheapest.travelSeconds - best.travelSeconds)} more travel; the fastest would save ${time(best.travelSeconds - fastest.travelSeconds)} for ${money(fastest.basketCents - best.basketCents)} more. ${rate} this plan is the balance.`
}

/** "2 × 1 L" or "1 × 1,5 L + 1 × 1 L", with the chain's own pack labels. */
export function packsText(packs: BoughtPack[]): string {
  return packs.map((pack) => `${pack.count} × ${pack.quantityText}`).join(' + ')
}

/** "+500 g" when the packs add up to more than needed, else null. */
export function extraText(boughtAmount: number, neededAmount: number, unit: Unit): string | null {
  const extra = boughtAmount - neededAmount
  return extra > 0 ? `+${formatQuantity(extra, unit)}` : null
}
