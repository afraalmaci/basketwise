import type { Plan } from './api/planning'
import { formatDuration, formatPrice } from './format'

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

/**
 * One sentence on the trade-off, comparing the best plan with the closest alternative:
 * fewer stores if the best plan has several, more stores otherwise.
 */
export function verdict(plans: Plan[], eurosPerHour: number): string {
  const best = plans[0]
  const stores = (count: number) => `${count} ${count === 1 ? 'store' : 'stores'}`
  const fewer = plans.find((plan) => plan.storeCount < best.storeCount)
  if (fewer) {
    const saving = fewer.basketCents - best.basketCents
    const extra = best.travelSeconds - fewer.travelSeconds
    if (saving > 0 && extra > 0) {
      return `Going to ${stores(best.storeCount)} saves ${formatPrice(saving)} for ${formatDuration(extra)} more travel than ${stores(fewer.storeCount)}. At €${eurosPerHour}/h that is worth it.`
    }
    return `${stores(best.storeCount)} beat ${stores(fewer.storeCount)} on both price and time.`
  }
  const more = plans.find((plan) => plan.storeCount > best.storeCount)
  if (more) {
    const saving = best.basketCents - more.basketCents
    const extra = more.travelSeconds - best.travelSeconds
    if (saving > 0) {
      return `${stores(more.storeCount)} would save ${formatPrice(saving)} but add ${formatDuration(Math.max(extra, 0))} of travel. At €${eurosPerHour}/h that is not worth it.`
    }
    return `Going to more stores would not save anything.`
  }
  return best.storeCount === 1 ? 'One store has everything at the best price.' : `${stores(best.storeCount)} is the only option.`
}
